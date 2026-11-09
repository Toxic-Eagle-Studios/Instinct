import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { GoogleGenAI } from '@google/genai';
import { interpretLocally, recoveryGuide, TYPES } from './src/logic.js';

const app = express();
const port = Number(process.env.PORT || 3001);
const base = path.dirname(fileURLToPath(import.meta.url));
const apiKey = process.env.GEMINI_API_KEY;
const ai = apiKey ? new GoogleGenAI({ apiKey }) : null;
const traffic = new Map();

app.disable('x-powered-by');
app.use(express.json({ limit: '2mb' }));

app.get('/api/status', (_req, res) => res.json({ status: 'ok', aiEnabled: Boolean(ai) }));

app.post('/api/interpret', async (req, res) => {
  const ip = req.ip || 'local';
  const now = Date.now();
  const bucket = traffic.get(ip) || { start: now, count: 0 };
  if (now - bucket.start > 60_000) { bucket.start = now; bucket.count = 0; }
  bucket.count += 1;
  traffic.set(ip, bucket);
  if (bucket.count > 30) return res.status(429).json({ error: 'Too many requests. Please try again shortly.' });
  if (traffic.size > 1500) traffic.clear();

  const text = typeof req.body?.text === 'string' ? req.body.text.trim().slice(0, 2000) : '';
  const image = typeof req.body?.image === 'string' ? req.body.image : '';
  if (!text && !image) return res.status(400).json({ error: 'Text or an image is required.' });
  const preview = Array.isArray(req.body?.memories) ? req.body.memories.slice(0, 30).map(e => ({
    type: String(e.type || '').slice(0, 30),
    title: String(e.title || '').slice(0, 90),
    content: String(e.content || '').slice(0, 220),
    context: String(e.context || '').slice(0, 70)
  })) : [];

  const fallback = interpretLocally(text || 'Remember this photo');
  if (!ai) return res.json({ ...fallback, mode: 'local', notice: 'AI not configured. Local rules are active.' });

  try {
    const parts = [{ text: JSON.stringify({ userInput: text, existingMemoryPreview: preview }) }];
    const imageMatch = image.match(/^data:image\/(jpeg|png|webp);base64,([a-zA-Z0-9+/=]+)$/);
    if (imageMatch && image.length < 1_400_000) {
      parts.push({ inlineData: { mimeType: `image/${imageMatch[1]}`, data: imageMatch[2] } });
    }
    const response = await ai.models.generateContent({
      model: process.env.GEMINI_MODEL || 'gemini-3.5-flash-lite',
      contents: [{ role: 'user', parts }],
      config: {
        responseMimeType: 'application/json',
        systemInstruction: `You are INSTINCT, a private second-brain app.
Return ONLY JSON: {"intent":"remember|retrieve|context|fix|chat", "query":"string", "entry":{"type":"reminder|preference|solution|note","title":"string","content":"string","context":"string","tags":["string"]}|null, "answer":"string"}.
Interpret the user's message accurately. REMEMBER when user is saving a fact, preference, reminder, method or solution; distinguish context and due time if relevant, no fabricated details. RETRIEVE when asking about something in their personal memory. CONTEXT when saying they're at/going to a person, location or activity, so matching reminders can be surfaced. FIX when they made a mistake and want recovery advice. CHAT for other requests. For RETRIEVE/CONTEXT do not invent personal information, because the client searches the real memory itself. For FIX, give 3-5 numbered, prudent, non-destructive troubleshooting steps; make uncertainty clear and ask which app/device only when necessary. For image-only capture, describe visible contents concisely as a note, do not infer private traits. Never falsely claim you saved something; the app saves it after this call. Return valid JSON, no markdown.`,
        temperature: 0.15
      }
    });
    const parsed = JSON.parse((response.text || '').replace(/^```(?:json)?\s*|\s*```$/g, '').trim());
    if (!['remember', 'retrieve', 'context', 'fix', 'chat'].includes(parsed.intent)) throw Error('Invalid intent');
    const entry = parsed.entry && typeof parsed.entry === 'object' ? {
      type: TYPES.includes(parsed.entry.type) ? parsed.entry.type : 'note',
      title: String(parsed.entry.title || '').slice(0, 100),
      content: String(parsed.entry.content || text).slice(0, 5000),
      context: String(parsed.entry.context || '').slice(0, 150),
      tags: Array.isArray(parsed.entry.tags) ? parsed.entry.tags.map(String).slice(0, 10) : []
    } : null;
    if (parsed.intent === 'remember' && (!entry || (!entry.content && !entry.title))) throw Error('Missing saved content');
    return res.json({
      intent: parsed.intent,
      query: String(parsed.query || text).slice(0, 2000),
      entry,
      answer: String(parsed.answer || (parsed.intent === 'fix' ? recoveryGuide(text) : '')).slice(0, 4000),
      mode: 'gemini'
    });
  } catch (_error) {
    return res.json({ ...fallback, mode: 'local', notice: 'Gemini did not respond; local rules handled this request.' });
  }
});

if (process.env.NODE_ENV === 'production') {
  app.use(express.static(path.join(base, 'dist')));
  app.get(/.*/, (_req, res) => res.sendFile(path.join(base, 'dist', 'index.html')));
}

app.listen(port, '0.0.0.0', () => console.log(`INSTINCT running on port ${port}`));

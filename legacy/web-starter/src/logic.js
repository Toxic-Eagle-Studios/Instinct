export const TYPES = ['reminder', 'preference', 'solution', 'note'];

function createId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    return [...bytes].map(b => b.toString(16).padStart(2, '0')).join('');
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}


const stopWords = new Set('a an and are as at be but by can did do does for from get have how i in into is it me my of on or our please should so that the their them there this to was we were what when where who why will with would you your im ive its about normal usual last time again'.split(' '));

export function tokenize(value = '') {
  return [...new Set(String(value).toLowerCase()
    .replace(/[’']s\b/g, '').replace(/[’']/g, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter(w => w.length > 1 && !stopWords.has(w))
    .map(w => w.endsWith('s') && w.length > 4 ? w.slice(0, -1) : w))];
}

export function searchMemories(entries, query, type = 'all') {
  const terms = tokenize(query);
  return entries.filter(entry => type === 'all' || entry.type === type)
    .map(entry => {
      const content = `${entry.title || ''} ${entry.content || ''} ${(entry.tags || []).join(' ')} ${entry.context || ''}`;
      const bag = new Set(tokenize(content));
      const titleBag = new Set(tokenize(entry.title || ''));
      const score = terms.reduce((sum, term) => sum + (bag.has(term) ? 2 : 0) + (titleBag.has(term) ? 2 : 0), 0);
      return { entry, score };
    })
    .filter(row => !terms.length || row.score > 0)
    .sort((a, b) => b.score - a.score || (b.entry.createdAt || '').localeCompare(a.entry.createdAt || ''))
    .map(row => row.entry);
}

export function matchContext(entries, context) {
  const terms = tokenize(context);
  return entries.filter(e => e.type === 'reminder' && !e.done)
    .map(entry => {
      const bag = new Set(tokenize(`${entry.context || ''} ${entry.title} ${entry.content} ${(entry.tags || []).join(' ')}`));
      return { entry, score: terms.filter(x => bag.has(x)).length };
    })
    .filter(r => r.score > 0)
    .sort((a, b) => b.score - a.score)
    .map(r => r.entry);
}

export function normalizeEntry(entry) {
  if (!entry || typeof entry !== 'object') return null;
  const content = String(entry.content || entry.body || '').trim().slice(0, 5000);
  const title = String(entry.title || content.slice(0, 70) || 'Untitled').trim().slice(0, 100);
  if (!content && !title) return null;
  return {
    id: String(entry.id || createId()),
    type: TYPES.includes(entry.type) ? entry.type : 'note',
    title,
    content,
    context: String(entry.context || '').trim().slice(0, 150),
    tags: Array.isArray(entry.tags) ? entry.tags.map(x => String(x).trim().slice(0, 40)).filter(Boolean).slice(0, 10) : [],
    dueAt: typeof entry.dueAt === 'string' ? entry.dueAt : '',
    done: Boolean(entry.done),
    image: typeof entry.image === 'string' && /^data:image\/(jpeg|png|webp);base64,/i.test(entry.image) && entry.image.length < 300000 ? entry.image : '',
    createdAt: typeof entry.createdAt === 'string' ? entry.createdAt : new Date().toISOString()
  };
}

function cleanLead(s) {
  return s.replace(/^(please\s+)?(remind me (to|that)\s+|remember (that|how)?\s*|save (this|that)?\s*|note (that)?\s*)/i, '').trim();
}

export function interpretLocally(raw) {
  const text = String(raw || '').trim();
  const lower = text.toLowerCase();
  if (!text) return { intent: 'chat', answer: 'Type or say something first.' };

  if (/\b(accidentally|deleted|lost|undo|restore|recover|messed up|made a mistake|sent the wrong|overwrote|erased)\b/i.test(text)) {
    return { intent: 'fix', query: text, answer: recoveryGuide(text) };
  }
  if (/^(i('| a)?m |im |we('| a)?re |we are )?(heading to|going to|on (my|our) way to|at |near |visiting |walking into )/i.test(text)) {
    const place = text.replace(/^.*?\b(heading to|going to|on (?:my|our) way to|at|near|visiting|walking into)\s+/i, '').replace(/[.!?]+$/, '');
    return { intent: 'context', query: place || text, answer: '' };
  }
  if (/^(what|where|when|who|which|how did|did i|do i|find|show me|search|pull up)\b/i.test(text) || text.includes('?')) {
    return { intent: 'retrieve', query: text, answer: '' };
  }
  if (/^(remind me|remember|save|note|don.t let me forget|my (usual|normal|favorite)|i (prefer|like|always|usually)|i fixed)/i.test(text)) {
    const type = /^remind me|^don.t let me forget/i.test(text) ? 'reminder'
      : /^(my (usual|normal|favorite)|i (prefer|like|always|usually))/i.test(text) ? 'preference'
      : /^i fixed/i.test(text) ? 'solution' : 'note';
    const body = cleanLead(text);
    const title = body.slice(0, 85) || text.slice(0, 85);
    const context = type === 'reminder' ? (body.match(/\b(?:ask|tell|call|text|meet|see|visit)\s+([A-Z][a-zA-Z'-]*)/i)?.[1] || '') : '';
    return { intent: 'remember', entry: { type, title, content: body || text, context, tags: [] }, answer: '' };
  }
  return { intent: 'remember', entry: { type: 'note', title: text.slice(0, 80), content: text, context: '', tags: [] }, answer: '' };
}

export function recoveryGuide(problem) {
  const lower = problem.toLowerCase();
  if (/delet|erased|overwrote|lost (a |my )?(file|document|photo)/.test(lower)) {
    return '1. Stop editing or saving over the affected file.\n2. Check Trash/Recently Deleted or the app’s version history.\n3. Check your cloud account and backups.\n4. Tell me which app/device it happened in for exact recovery steps. Recovery is not guaranteed.';
  }
  if (/sent the wrong|wrong message|wrong email/.test(lower)) {
    return '1. See whether the app still offers Undo Send.\n2. If not, send a short correction with the right information.\n3. Avoid sending more personal details until you verify the recipient.\n4. Save the final correction so you can find it again.';
  }
  return '1. Tell INSTINCT exactly what happened, which app or device you used, and what you tried.\n2. Avoid irreversible actions until you identify the recovery option.\n3. Check built-in Undo, Trash, history, or backups when applicable.\n4. Save the solution after you fix it.';
}

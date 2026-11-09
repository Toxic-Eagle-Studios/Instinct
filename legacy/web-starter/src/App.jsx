import React, { useEffect, useMemo, useRef, useState } from 'react';
import { interpretLocally, matchContext, normalizeEntry, searchMemories, TYPES } from './logic.js';
import { firebaseReady, watchUser, login, logout, watchEntries, putCloudEntry, removeCloudEntry } from './firebase.js';

const STORE_KEY = 'instinct-memories-v1';
const QUICK = [
  'Remind me to ask Dad about Friday',
  "I'm heading to Dad's",
  'What is my normal pizza order?',
  'I accidentally deleted a file'
];

function loadLocal() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORE_KEY) || '[]');
    return Array.isArray(parsed) ? parsed.map(normalizeEntry).filter(Boolean) : [];
  } catch { return []; }
}

function readableDate(date) {
  if (!date) return '';
  const parsed = new Date(date);
  return Number.isNaN(parsed.valueOf()) ? '' : parsed.toLocaleString([], { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
}

function readFile(file) {
  return new Promise((resolve, reject) => {
    if (!file) return resolve('');
    if (!file.type.startsWith('image/')) return reject(new Error('Choose an image file.'));
    if (file.size > 10 * 1024 * 1024) return reject(new Error('Choose an image under 10 MB.'));
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      const ratio = Math.min(1, 600 / Math.max(img.naturalWidth, img.naturalHeight));
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(img.naturalWidth * ratio));
      canvas.height = Math.max(1, Math.round(img.naturalHeight * ratio));
      canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
      URL.revokeObjectURL(url);
      const data = canvas.toDataURL('image/jpeg', 0.58);
      if (data.length > 290000) reject(new Error('Image still too large after compression.'));
      else resolve(data);
    };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Could not read image.')); };
    img.src = url;
  });
}

function EntryCard({ entry, onEdit, onDelete, onComplete }) {
  return <article className="entry">
    <div className="entry-top"><span className={`type ${entry.type}`}>{entry.type}</span><time>{readableDate(entry.createdAt)}</time></div>
    <h3 className={entry.done ? 'done' : ''}>{entry.title}</h3>
    <p className="entry-content">{entry.content}</p>
    {entry.image && <img className="saved-image" src={entry.image} alt={`Saved reference for ${entry.title}`} />}
    <div className="entry-meta">
      {entry.context && <span>⌁ {entry.context}</span>}
      {entry.dueAt && <span>◷ {readableDate(entry.dueAt)}</span>}
      {(entry.tags || []).map(tag => <span key={tag}>#{tag}</span>)}
    </div>
    <div className="entry-actions">
      {entry.type === 'reminder' && <button type="button" className="small" onClick={() => onComplete(entry)}>{entry.done ? 'Reopen' : 'Mark done'}</button>}
      <button type="button" className="small" onClick={() => onEdit(entry)}>Edit</button>
      <button type="button" className="small danger-link" onClick={() => onDelete(entry)}>Delete</button>
    </div>
  </article>;
}

function EditForm({ initial, onSave, onCancel }) {
  const [draft, setDraft] = useState({ ...initial, tagsText: (initial.tags || []).join(', ') });
  const set = (field, value) => setDraft(old => ({ ...old, [field]: value }));
  return <form className="editor" onSubmit={event => { event.preventDefault(); onSave(normalizeEntry({ ...draft, tags: draft.tagsText.split(',').map(t => t.trim()), dueAt: draft.dueAt || '' })); }}>
    <h3>Edit memory</h3>
    <label>Type<select value={draft.type} onChange={event => set('type', event.target.value)}>{TYPES.map(t => <option key={t} value={t}>{t}</option>)}</select></label>
    <label>Title<input required maxLength="100" value={draft.title} onChange={event => set('title', event.target.value)} /></label>
    <label>Details<textarea required rows="4" value={draft.content} onChange={event => set('content', event.target.value)} /></label>
    <label>Person, place, or context<input placeholder="e.g. Dad, grocery store" value={draft.context} onChange={event => set('context', event.target.value)} /></label>
    <label>Tags, separated by commas<input value={draft.tagsText} onChange={event => set('tagsText', event.target.value)} /></label>
    <label>Due date (optional)<input type="datetime-local" value={draft.dueAt || ''} onChange={event => set('dueAt', event.target.value)} /></label>
    <div className="actions"><button type="submit" className="primary">Save changes</button><button type="button" onClick={onCancel}>Cancel</button></div>
  </form>;
}

export default function App() {
  const [tab, setTab] = useState('home');
  const [input, setInput] = useState('');
  const [photo, setPhoto] = useState('');
  const [localEntries, setLocalEntries] = useState(loadLocal);
  const [cloudEntries, setCloudEntries] = useState([]);
  const [user, setUser] = useState(null);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('all');
  const [editing, setEditing] = useState(null);
  const [busy, setBusy] = useState(false);
  const [listening, setListening] = useState(false);
  const [result, setResult] = useState(null);
  const [feedback, setFeedback] = useState('');
  const [aiStatus, setAiStatus] = useState(false);
  const [useAi, setUseAi] = useState(true);
  const [dueAt, setDueAt] = useState('');
  const [notify, setNotify] = useState(false);
  const recognition = useRef(null);
  const notified = useRef(new Set());
  const importInput = useRef(null);
  const userId = user?.uid || null;
  const entries = userId && firebaseReady ? cloudEntries : localEntries;

  useEffect(() => { try { localStorage.setItem(STORE_KEY, JSON.stringify(localEntries)); } catch { setFeedback('Local storage is full. Export a backup and remove some images.'); } }, [localEntries]);
  useEffect(() => { fetch('/api/status').then(r => r.json()).then(x => setAiStatus(Boolean(x.aiEnabled))).catch(() => setAiStatus(false)); }, []);
  useEffect(() => watchUser(setUser), []);
  useEffect(() => {
    if (!userId || !firebaseReady) return undefined;
    return watchEntries(userId, setCloudEntries, () => setFeedback('Could not read cloud memories. Check Firebase setup and Firestore rules.'));
  }, [userId]);
  useEffect(() => {
    if (!notify || typeof Notification === 'undefined' || Notification.permission !== 'granted') return undefined;
    const check = () => {
      for (const entry of entries) {
        if (entry.type !== 'reminder' || entry.done || !entry.dueAt || notified.current.has(entry.id)) continue;
        const time = Date.parse(entry.dueAt);
        if (!Number.isNaN(time) && time <= Date.now()) {
          notified.current.add(entry.id);
          new Notification('INSTINCT reminder', { body: entry.title });
        }
      }
    };
    check();
    const timer = setInterval(check, 60_000);
    return () => clearInterval(timer);
  }, [entries, notify]);

  const pending = useMemo(() => entries.filter(x => x.type === 'reminder' && !x.done).sort((a, b) => (a.dueAt || '9999').localeCompare(b.dueAt || '9999')), [entries]);
  const filtered = useMemo(() => searchMemories(entries, search, filter), [entries, search, filter]);

  async function saveEntry(entry) {
    const normalized = normalizeEntry(entry);
    if (!normalized) throw new Error('Invalid memory.');
    if (userId && firebaseReady) await putCloudEntry(userId, normalized);
    else setLocalEntries(old => [normalized, ...old.filter(x => x.id !== normalized.id)]);
    return normalized;
  }

  async function deleteEntry(entry) {
    if (!window.confirm(`Delete "${entry.title}"? This cannot be undone unless you exported a backup.`)) return;
    try {
      if (userId && firebaseReady) await removeCloudEntry(userId, entry.id);
      else setLocalEntries(old => old.filter(x => x.id !== entry.id));
      setFeedback('Memory deleted.');
    } catch { setFeedback('Could not delete memory.'); }
  }

  async function doInterpret(value = input) {
    const original = value.trim();
    if (!original && !photo) return;
    setBusy(true); setFeedback(''); setResult(null);
    try {
      let instruction;
      if (useAi && aiStatus) {
        const response = await fetch('/api/interpret', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ text: original || 'Remember this picture', image: photo, memories: entries.slice(0, 30) })
        });
        if (!response.ok) throw new Error(`AI request failed (${response.status}).`);
        instruction = await response.json();
      } else instruction = interpretLocally(original || 'Remember this picture');
      if (instruction.notice) setFeedback(instruction.notice);
      if (instruction.intent === 'remember') {
        const proposed = instruction.entry || interpretLocally(original).entry;
        const saved = await saveEntry({ ...proposed, image: photo, dueAt, createdAt: new Date().toISOString() });
        setResult({ heading: 'Saved to your second brain', detail: 'You can search, correct, and delete this memory anytime.', items: [saved] });
      } else if (instruction.intent === 'context') {
        const context = instruction.query || original;
        const matched = matchContext(entries, context);
        setResult({ heading: matched.length ? 'While you’re at it…' : 'Nothing pending for that context', detail: matched.length ? `These reminders mention ${context}.` : 'You can add reminders for people, places, and activities.', items: matched });
      } else if (instruction.intent === 'retrieve') {
        const found = searchMemories(entries, instruction.query || original).slice(0, 8);
        setResult({ heading: found.length ? 'Here’s what you saved' : 'Nothing matching in your memory', detail: found.length ? 'Results are taken from your actual saved records.' : 'INSTINCT does not invent memories you never saved.', items: found });
      } else if (instruction.intent === 'fix') {
        setResult({ heading: 'UNDO IRL · Recovery guide', detail: instruction.answer || 'No recovery steps available.', items: [], solution: original });
      } else {
        setResult({ heading: 'INSTINCT', detail: instruction.answer || 'Save a memory, ask for one, describe where you are, or tell me what went wrong.', items: [] });
      }
      setInput(''); setPhoto(''); setDueAt('');
    } catch (error) { setFeedback(error.message || 'Something went wrong.'); }
    finally { setBusy(false); }
  }

  function startVoice() {
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Recognition) { setFeedback('Speech recognition is not supported in this browser. You can still type.'); return; }
    if (listening) { recognition.current?.stop(); return; }
    const active = new Recognition();
    active.lang = 'en-US'; active.interimResults = false; active.continuous = false;
    active.onresult = e => setInput(old => [old, e.results[0][0].transcript].filter(Boolean).join(' '));
    active.onerror = e => setFeedback(`Mic unavailable: ${e.error}. Use text instead.`);
    active.onend = () => setListening(false);
    recognition.current = active;
    try { active.start(); setListening(true); } catch { setFeedback('Could not start microphone.'); }
  }

  function exportData() {
    const data = JSON.stringify({ app: 'INSTINCT', exportedAt: new Date().toISOString(), memories: entries }, null, 2);
    const url = URL.createObjectURL(new Blob([data], { type: 'application/json' }));
    const a = document.createElement('a'); a.href = url; a.download = `instinct-backup-${new Date().toISOString().slice(0, 10)}.json`; a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  async function importData(file) {
    if (!file) return;
    try {
      const json = JSON.parse(await file.text());
      const list = Array.isArray(json) ? json : json.memories;
      if (!Array.isArray(list) || list.length > 5000) throw Error('Invalid or oversized backup.');
      const restored = list.map(normalizeEntry).filter(Boolean);
      if (!window.confirm(`Import ${restored.length} memories into ${userId ? 'cloud' : 'this browser'}? Existing items with the same ID will be updated.`)) return;
      if (userId && firebaseReady) for (const entry of restored) await putCloudEntry(userId, entry);
      else setLocalEntries(old => [...restored, ...old.filter(x => !restored.some(y => y.id === x.id))]);
      setFeedback(`Imported ${restored.length} memories.`);
    } catch (e) { setFeedback(e.message || 'Could not import backup.'); }
    if (importInput.current) importInput.current.value = '';
  }

  async function moveLocalToCloud() {
    if (!userId || !firebaseReady || !localEntries.length) return;
    if (!window.confirm(`Copy ${localEntries.length} local memories into your Google cloud account? Local copies will stay on this device.`)) return;
    try {
      for (const entry of localEntries) await putCloudEntry(userId, entry);
      setFeedback('Local memories copied to cloud. Original local copies were preserved.');
    } catch { setFeedback('Cloud copy incomplete. Please check your connection and try again.'); }
  }
  async function requestNotifications() {
    if (!('Notification' in window)) return setFeedback('Notifications are not supported by this browser.');
    const permission = await Notification.requestPermission();
    setNotify(permission === 'granted');
    setFeedback(permission === 'granted' ? 'Alerts enabled while INSTINCT is open. They are NOT background reminders.' : 'Notification permission was not granted.');
  }

  return <div className="app-shell">
    <header className="masthead">
      <div className="brandmark">I<span>.</span></div>
      <div className="brand"><strong>INSTINCT</strong><span>Your second brain. No bullshit.</span></div>
      <span className="mode">{userId ? '☁ Cloud sync' : '◉ This device'}</span>
    </header>

    <nav className="nav" aria-label="Main navigation">
      {['home', 'memory', 'recovery', 'settings'].map(view => <button type="button" key={view} className={tab === view ? 'active' : ''} onClick={() => { setTab(view); setEditing(null); }}>
        <span aria-hidden="true">{{ home: '⌂', memory: '▦', recovery: '↶', settings: '⚙' }[view]}</span>{view === 'recovery' ? 'Undo IRL' : view[0].toUpperCase() + view.slice(1)}
      </button>)}
    </nav>

    <main>
      {feedback && <div role="status" className="feedback">{feedback} <button type="button" aria-label="Dismiss message" onClick={() => setFeedback('')}>×</button></div>}
      {tab === 'home' && <>
        <section className="hero">
          <div className="eyebrow">REMEMBER LESS. GET MORE DONE.</div>
          <h1>What’s on your mind?</h1>
          <p>Tell INSTINCT something to remember, ask what it knows, say where you’re going, or explain what went wrong.</p>
          <form onSubmit={e => { e.preventDefault(); doInterpret(); }} className="capture">
            <textarea rows="3" value={input} maxLength="2000" onChange={e => setInput(e.target.value)} placeholder="Remind me to ask Dad about Friday…" aria-label="Ask INSTINCT" />
            {photo && <div className="photo-preview"><img src={photo} alt="Photo ready to save"/><button type="button" onClick={() => setPhoto('')}>Remove photo</button></div>}
            <div className="capture-controls">
              <div className="mini-tools">
                <button type="button" className={listening ? 'listening' : ''} onClick={startVoice} title="Speech to text">{listening ? '■ Stop' : '◉ Talk'}</button>
                <label className="file-button">▧ Photo<input hidden type="file" accept="image/*" onChange={async e => { try { setPhoto(await readFile(e.target.files?.[0])); setFeedback('Photo attached. Add text, or save it as a note.'); } catch (error) { setFeedback(error.message); } e.target.value = ''; }} /></label>
              </div>
              <button disabled={busy || (!input.trim() && !photo)} className="primary" type="submit">{busy ? 'Thinking…' : 'Send to INSTINCT →'}</button>
            </div>
            <details><summary>Optional reminder date</summary><input aria-label="Reminder date" type="datetime-local" value={dueAt} onChange={e => setDueAt(e.target.value)} /></details>
          </form>
          <div className="quick-label">TRY IT</div>
          <div className="quick-chips">{QUICK.map(q => <button type="button" key={q} onClick={() => { setInput(q); doInterpret(q); }}>{q}</button>)}</div>
        </section>
        {result && <section className="results" aria-live="polite"><div className="section-head"><h2>{result.heading}</h2><button type="button" onClick={() => setResult(null)}>Clear</button></div><p className="result-detail">{result.detail}</p>
          {result.items?.map(x => <EntryCard key={x.id} entry={x} onEdit={x => { setEditing(x); setTab('memory'); }} onDelete={deleteEntry} onComplete={x => saveEntry({ ...x, done: !x.done })} />)}
          {result.solution && <button type="button" className="outline" onClick={async () => { try { const saved = await saveEntry({ type: 'solution', title: result.solution.slice(0, 80), content: `${result.solution}\n\n${result.detail}`, tags: ['recovery'] }); setFeedback(`Saved solution: ${saved.title}`); } catch { setFeedback('Could not save solution.'); } }}>Save these steps to memory</button>}
        </section>}
        <section className="dashboard"><div className="section-head"><h2>While you’re at it</h2><button onClick={() => { setTab('memory'); setFilter('reminder'); }}>See all reminders</button></div>
          {pending.length ? pending.slice(0, 3).map(x => <EntryCard key={x.id} entry={x} onEdit={x => { setEditing(x); setTab('memory'); }} onDelete={deleteEntry} onComplete={x => saveEntry({ ...x, done: !x.done })} />) : <p className="empty">No unfinished reminders. Save one, then mention the relevant person or place to bring it back.</p>}
        </section>
      </>}

      {tab === 'memory' && <section className="page"><div className="section-head"><div><div className="eyebrow">ONE SEARCHABLE MEMORY</div><h1>Your memory</h1><p className="muted">{entries.length} saved {entries.length === 1 ? 'item' : 'items'}</p></div><button className="outline" onClick={exportData}>Export JSON</button></div>
        {editing ? <EditForm key={editing.id} initial={editing} onCancel={() => setEditing(null)} onSave={async updated => { try { await saveEntry(updated); setEditing(null); setFeedback('Memory updated.'); } catch { setFeedback('Unable to save changes.'); } }} /> : <>
          <div className="searchline"><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search everything you’ve saved" aria-label="Search memories" /><select value={filter} onChange={e => setFilter(e.target.value)} aria-label="Filter memories"><option value="all">All</option>{TYPES.map(type => <option value={type} key={type}>{type}</option>)}</select></div>
          <div className="entries">{filtered.map(x => <EntryCard key={x.id} entry={x} onEdit={setEditing} onDelete={deleteEntry} onComplete={x => saveEntry({ ...x, done: !x.done })} />)}{!filtered.length && <p className="empty">Nothing found. Change your search or add your first memory on the Home tab.</p>}</div>
        </>}
      </section>}

      {tab === 'recovery' && <section className="page"><div className="eyebrow">UNDO IRL</div><h1>Something go sideways?</h1><p className="muted">Describe the mistake. INSTINCT provides recovery steps and lets you save what worked for next time.</p><div className="example-grid">
        {['I deleted a photo by accident', 'I sent a message to the wrong person', 'I overwrote a document', 'I messed up a setting'].map(example => <button type="button" key={example} className="example-card" onClick={() => { setTab('home'); setInput(example); doInterpret(example); }}>{example} <span>↗</span></button>)}
      </div><p className="note">Recovery guides are suggestions, not guarantees. Avoid irreversible steps until you know the device and app involved.</p></section>}

      {tab === 'settings' && <section className="page"><div className="eyebrow">CONTROL YOUR DATA</div><h1>Settings</h1>
        <div className="setting"><div><h3>Use Gemini intelligence</h3><p>Send your input and a short preview of recent memories to Gemini for interpretation when a server key is configured.</p><small>Current server: {aiStatus ? 'Gemini ready' : 'Local rules only'}</small></div><label className="switch"><input type="checkbox" checked={useAi} onChange={e => setUseAi(e.target.checked)} /><span>{useAi ? 'On' : 'Off'}</span></label></div>
        <div className="setting"><div><h3>Google sign-in and sync</h3><p>{firebaseReady ? (userId ? `Signed in as ${user.email}. Cloud records are separate from local records until copied.` : 'Sign in to sync memories across devices.') : 'Not configured. Records are saved in this browser only, not across devices.'}</p></div>{firebaseReady && (userId ? <button onClick={() => logout().catch(() => setFeedback('Sign-out failed.'))}>Sign out</button> : <button onClick={() => login().catch(() => setFeedback('Sign-in failed. Check Google popup settings and Firebase configuration.'))}>Sign in with Google</button>)}</div>
        {userId && localEntries.length > 0 && <div className="setting"><div><h3>Copy device memories to cloud</h3><p>{localEntries.length} local memories. This preserves the local copies.</p></div><button onClick={moveLocalToCloud}>Copy to cloud</button></div>}
        <div className="setting"><div><h3>Reminder alerts</h3><p>Notifications run only while this webpage is open. This version does not yet send scheduled background push notifications.</p></div><button onClick={requestNotifications}>Enable alerts</button></div>
        <div className="setting"><div><h3>Download your memories</h3><p>Export a complete JSON backup, including saved reference images.</p></div><button onClick={exportData}>Export</button></div>
        <div className="setting"><div><h3>Restore a backup</h3><p>Import an INSTINCT JSON file into your active memory collection.</p></div><button onClick={() => importInput.current?.click()}>Import</button><input hidden ref={importInput} type="file" accept="application/json,.json" onChange={e => importData(e.target.files?.[0])} /></div>
        <div className="setting danger"><div><h3>Delete current collection</h3><p>Deletes all {entries.length} records in {userId ? 'your cloud account' : 'this browser'} after confirmation. Export a backup first.</p></div><button onClick={async () => { if (!window.confirm(`Permanently delete all ${entries.length} active memories?`)) return; if (userId) { try { for (const e of entries) await removeCloudEntry(userId, e.id); } catch { setFeedback('Some cloud records could not be removed.'); return; } } else setLocalEntries([]); setFeedback('Current memory collection cleared.'); }}>Delete all</button></div>
      </section>}
    </main>
    <footer>INSTINCT v0.1 · Private-by-default browser storage · Your memory stays under your control</footer>
  </div>;
}

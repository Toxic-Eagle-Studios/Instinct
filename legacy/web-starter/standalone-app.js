// INSTINCT standalone browser edition: no packages, server, or API key required.
const KEY = 'instinct-memories-v1';
let memories = [];
let editId = null;
let view = 'home';
let selectedPhoto = '';
let lastResult = '';
let lastFix = null;
const $ = id => document.getElementById(id);
const escapeHtml = val => String(val ?? '').replace(/[&<>"']/g, ch => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[ch]));
function announce(message) { $('notice').textContent = message; }
function readStore() { try { let x=JSON.parse(localStorage.getItem(KEY)||'[]'); memories=Array.isArray(x)?x.map(normalizeEntry).filter(Boolean):[]; } catch {memories=[];} }
function writeStore(){try {localStorage.setItem(KEY,JSON.stringify(memories));} catch {announce('Storage full. Export a backup and remove old photos.');}}
function addRecord(entry){let value=normalizeEntry(entry);memories=[value,...memories.filter(x=>x.id!==value.id)];writeStore();render();return value;}
function removeRecord(id){const entry=memories.find(x=>x.id===id);if(entry&&confirm(`Delete "${entry.title}"?`)){memories=memories.filter(x=>x.id!==id);writeStore();render();announce('Memory deleted.');}}
function setTab(next){view=next;document.querySelectorAll('.nav button').forEach(b=>b.classList.toggle('active',b.dataset.tab===next));document.querySelectorAll('[data-page]').forEach(p=>p.hidden=p.dataset.page!==next);render();}
function cards(entries){return entries.map(x=>`<article class="entry"><div class="entry-top"><span class="type ${escapeHtml(x.type)}">${escapeHtml(x.type)}</span><time>${escapeHtml(x.createdAt.slice(0,10))}</time></div><h3${x.done?' style="text-decoration:line-through"':''}>${escapeHtml(x.title)}</h3><p class="entry-content">${escapeHtml(x.content)}</p>${x.image?`<img class="saved-image" alt="Saved photo" src="${x.image}" />`:''}<div class="entry-meta">${x.context?`<span>⌁ ${escapeHtml(x.context)}</span>`:''}${x.dueAt?`<span>◷ ${escapeHtml(x.dueAt.replace('T',' '))}</span>`:''}</div><div class="entry-actions">${x.type==='reminder'?`<button data-do="complete" data-id="${escapeHtml(x.id)}" class="small">${x.done?'Reopen':'Mark done'}</button>`:''}<button data-do="edit" data-id="${escapeHtml(x.id)}" class="small">Edit</button><button data-do="delete" data-id="${escapeHtml(x.id)}" class="small danger-link">Delete</button></div></article>`).join('')}
function render(){ $('count').textContent=`${memories.length} saved items`;
const resultList=$('result');resultList.innerHTML=lastResult;
const filtered=searchMemories(memories,$('search').value,$('type-filter').value);$('memory-list').innerHTML=filtered.length?cards(filtered):'<p class="empty">Nothing saved that matches your search.</p>';
const pending=memories.filter(x=>x.type==='reminder'&&!x.done);$('pending-list').innerHTML=pending.length?cards(pending.slice(0,3)):'<p class="empty">No pending reminders. Save one to get started.</p>';
$('edit-form').hidden=!editId;
if(editId){const e=memories.find(x=>x.id===editId);if(e&&!$('edit-active').value){$('edit-active').value=e.id;$('edit-type').value=e.type;$('edit-title').value=e.title;$('edit-body').value=e.content;$('edit-context').value=e.context;$('edit-due').value=e.dueAt;}}
}
function submitMessage(value){let msg=value.trim();if(!msg&&!selectedPhoto)return;const instruction=interpretLocally(msg||'Remember this picture');lastFix=null;
 if(instruction.intent==='remember'){let saved=addRecord({...instruction.entry,image:selectedPhoto,dueAt:$('due').value});lastResult=`<h2>Saved to your second brain</h2>${cards([saved])}`;}
 else if(instruction.intent==='context'){let found=matchContext(memories,instruction.query||msg);lastResult=`<h2>${found.length?'While you’re at it…':'Nothing pending for that context'}</h2>${found.length?cards(found):'<p class="empty">No matching unfinished reminders.</p>'}`;}
 else if(instruction.intent==='retrieve'){let found=searchMemories(memories,instruction.query||msg);lastResult=`<h2>${found.length?'Here’s what you saved':'No matching memory'}</h2>${found.length?cards(found.slice(0,10)):'<p class="empty">Nothing saved about that yet. INSTINCT will not invent your memories.</p>'}`;}
 else if(instruction.intent==='fix'){lastResult=`<h2>Undo IRL · Recovery guide</h2><p class="result-detail">${escapeHtml(instruction.answer)}</p><button class="outline" id="save-solution">Save this solution</button>`;lastFix={title:msg,content:`${msg}\n${instruction.answer}`};}
 else {lastResult=`<h2>INSTINCT</h2><p>${escapeHtml(instruction.answer)}</p>`;}
 if(instruction.intent!=='fix')$('result').innerHTML=lastResult;
 $('prompt').value='';$('due').value='';$('photo-preview').innerHTML='';selectedPhoto='';render();

}
function enableSpeech(){const Recognizer=window.SpeechRecognition||window.webkitSpeechRecognition;if(!Recognizer){announce('This browser does not support speech input. Type instead.');return;}const r=new Recognizer();r.lang='en-US';r.interimResults=false;r.onresult=e=>{$('prompt').value=($('prompt').value+' '+e.results[0][0].transcript).trim();};r.onerror=()=>announce('Microphone unavailable or permission denied.');r.start();announce('Listening…');}
function exportJson(){const data=JSON.stringify({app:'INSTINCT',exportedAt:new Date().toISOString(),memories},null,2);const a=document.createElement('a');const url=URL.createObjectURL(new Blob([data],{type:'application/json'}));a.href=url;a.download='instinct-memory-backup.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function compressPhoto(file){if(!file)return; if(!file.type.startsWith('image/')||file.size>10000000){announce('Select an image under 10 MB.');return;}const url=URL.createObjectURL(file),img=new Image();img.onload=()=>{let scale=Math.min(1,600/Math.max(img.width,img.height));let canvas=document.createElement('canvas');canvas.width=Math.round(img.width*scale);canvas.height=Math.round(img.height*scale);canvas.getContext('2d').drawImage(img,0,0,canvas.width,canvas.height);URL.revokeObjectURL(url);const result=canvas.toDataURL('image/jpeg',.58);if(result.length>290000){announce('Photo too large after compression.');return;}selectedPhoto=result;$('photo-preview').innerHTML='<img alt="Attached image" class="saved-image" src="'+result+'"><button type="button" id="remove-photo" class="small">Remove</button>';$('remove-photo').onclick=()=>{selectedPhoto='';$('photo-preview').innerHTML='';};announce('Photo attached. Save it with a note.');};img.onerror=()=>{URL.revokeObjectURL(url);announce('Unable to read photo.');};img.src=url;}
function attachListeners(){document.querySelectorAll('.nav button').forEach(b=>b.onclick=()=>setTab(b.dataset.tab));$('capture').onsubmit=e=>{e.preventDefault();submitMessage($('prompt').value);};$('talk').onclick=enableSpeech;
$('photo').onchange=e=>{compressPhoto(e.target.files?.[0]);e.target.value='';};document.querySelectorAll('[data-try]').forEach(b=>b.onclick=()=>{setTab('home');submitMessage(b.dataset.try);});
$('search').oninput=render;$('type-filter').onchange=render;$('export').onclick=exportJson;$('export2').onclick=exportJson;
$('import').onchange=async e=>{try{let data=JSON.parse(await e.target.files?.[0]?.text());let arr=Array.isArray(data)?data:data.memories;if(!Array.isArray(arr)||arr.length>5000)throw Error();let restored=arr.map(normalizeEntry).filter(Boolean);if(confirm(`Import ${restored.length} memories?`)){memories=[...restored,...memories.filter(x=>!restored.some(y=>y.id===x.id))];writeStore();render();announce(`Imported ${restored.length} memories.`);}}catch{announce('Invalid backup JSON.');}e.target.value='';};
$('delete-all').onclick=()=>{if(confirm('Permanently delete all memories from this browser?')){memories=[];writeStore();render();announce('Memory cleared.');}};
$('edit-form').onsubmit=e=>{e.preventDefault();const original=memories.find(x=>x.id===editId);if(!original)return;addRecord({...original,type:$('edit-type').value,title:$('edit-title').value,content:$('edit-body').value,context:$('edit-context').value,dueAt:$('edit-due').value});editId=null;$('edit-active').value='';render();announce('Memory updated.');};
$('cancel-edit').onclick=()=>{editId=null;$('edit-active').value='';render();};
document.body.addEventListener('click',e=>{if(e.target.closest('#save-solution')&&lastFix){addRecord({type:'solution',...lastFix,tags:['recovery']});lastResult='<h2>Recovery guide saved</h2>';lastFix=null;render();announce('Solution saved to memory.');return;}const b=e.target.closest('[data-do]');if(!b)return;const item=memories.find(x=>x.id===b.dataset.id);if(!item)return;if(b.dataset.do==='delete')removeRecord(item.id);if(b.dataset.do==='complete'){addRecord({...item,done:!item.done});announce('Reminder updated.');}if(b.dataset.do==='edit'){editId=item.id;$('edit-active').value='';setTab('memory');window.scrollTo({top:0,behavior:'smooth'});}});
}
readStore();attachListeners();render();setTab('home');

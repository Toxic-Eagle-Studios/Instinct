import { initializeApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider, signInWithPopup, signOut, onAuthStateChanged } from 'firebase/auth';
import { getFirestore, collection, doc, onSnapshot, setDoc, deleteDoc } from 'firebase/firestore';

const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID
};

export const firebaseReady = Boolean(Object.values(config).every(Boolean));
const app = firebaseReady ? initializeApp(config) : null;
const auth = app ? getAuth(app) : null;
const db = app ? getFirestore(app) : null;

export function watchUser(callback) {
  if (!auth) { callback(null); return () => {}; }
  return onAuthStateChanged(auth, callback);
}

export async function login() {
  if (!auth) throw Error('Firebase has not been configured.');
  return signInWithPopup(auth, new GoogleAuthProvider());
}
export async function logout() { if (auth) await signOut(auth); }
export function watchEntries(userId, callback, onError) {
  return onSnapshot(collection(db, 'users', userId, 'memories'), snapshot => {
    callback(snapshot.docs.map(item => item.data()));
  }, onError);
}
export async function putCloudEntry(userId, entry) {
  await setDoc(doc(db, 'users', userId, 'memories', entry.id), entry);
}
export async function removeCloudEntry(userId, id) {
  await deleteDoc(doc(db, 'users', userId, 'memories', id));
}

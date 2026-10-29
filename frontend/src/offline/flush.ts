import {api,ApiError,refreshCsrf} from '../api';
import {db} from './db';import {done} from './done';import {refused} from './refused';import {purgeExpired} from './clean';
import {useSession} from '../stores/session';import {useExperiment} from '../stores/experiment';
import type {OutboxItem} from './types';import type {Appointment} from '../types';

let running:Promise<void>|null=null;
/** Vide la file du patient connecté. Un seul envoi par onglet, et un onglet à la fois (Web Locks). */
export function flush():Promise<void>{
 if(running)return running;
 const locks=typeof navigator!=='undefined'?navigator.locks:undefined;
 const p=(locks?locks.request('medislot-outbox',sendAll):sendAll()).then(()=>undefined).finally(()=>{running=null});
 running=p;
 return p;
}

async function sendAll(){
 const me=useSession().user?.id;
 if(me===undefined)return;                        // personne de connecté
 await purgeExpired();                            // 24 h au plus
 const all=await (await db()).getAll('outbox');
 for(const it of all.filter(i=>i.userId===me)){   // jamais la file d'un autre compte
  try{await done(it,await post(it))}
  catch(e){
   if(e instanceof TypeError)return;              // toujours hors ligne : on garde, on réessaiera
   await refused(it,e);
   if(e instanceof ApiError&&(e.status===401||e.status>=500))return; // session expirée ou serveur muet
  }
 }
}

async function post(it:OutboxItem):Promise<Appointment>{
 // Test A/B : l'exposition en attente part d'abord, et le jeton de variante accompagne la réservation.
 const ab=useExperiment();await ab.flushExposure();
 const headers:Record<string,string>={'Idempotency-Key':it.idemKey}; // la même clé à chaque essai
 if(ab.token)headers['X-AB-Token']=ab.token;
 const send=()=>api<Appointment>('/appointments',{method:'POST',body:JSON.stringify(it.body),headers});
 try{return await send()}
 catch(e){
  if(!(e instanceof ApiError)||e.status!==403)throw e;
  await refreshCsrf();                             // jeton CSRF périmé : un seul nouvel essai
  return send();
 }
}

/** Appelé une fois dans main.ts : retour du réseau, retour de l'onglet au premier plan. */
export function startFlushTriggers(){
 window.addEventListener('online',()=>void flush());
 document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible')void flush()});
}

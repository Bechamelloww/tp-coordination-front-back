import {defineStore} from 'pinia';import {ref,watch} from 'vue';
import {db} from '../offline/db';import {validate,toRequest} from '../offline/bookingRules';import {flush} from '../offline/flush';
import type {BookingDraft,StoredDraft,FieldError} from '../offline/types';import type {Professional,Slot,Appointment} from '../types';
export const DRAFT_VERSION=1,SAVE_DELAY_MS=500,DRAFT_TTL_MS=24*60*60*1000;
// Une clé par patient : jamais le brouillon d'un autre.
export const keyFor=(userId:number)=>`booking:${userId}`;
// Copie plate : IndexedDB refuse un Proxy réactif de Vue (DataCloneError).
export function toStored(d:BookingDraft):StoredDraft{return {...JSON.parse(JSON.stringify(d)) as BookingDraft,key:keyFor(d.userId),v:DRAFT_VERSION,savedAt:Date.now()}}
export function fromStored(s:StoredDraft):BookingDraft{const {key:_k,v:_v,savedAt:_s,...d}=s;return d}
/** Relit le brouillon du patient ; le refuse (et l'efface) s'il est d'une autre version, d'un autre patient ou périmé. */
export async function loadDraft(userId:number):Promise<StoredDraft|null>{
 const d=await (await db()).get('drafts',keyFor(userId));
 if(!d)return null;
 if(d.v!==DRAFT_VERSION||d.userId!==userId||Date.now()-d.savedAt>DRAFT_TTL_MS){await discardDraft(userId);return null}
 return d;
}
export async function discardDraft(userId:number){await (await db()).delete('drafts',keyFor(userId))}

export const useBookingDraft=defineStore('bookingDraft',()=>{
 const draft=ref<BookingDraft|null>(null);
 const open=ref(false);                           // formulaire affiché
 const errors=ref<FieldError[]>([]);
 const alternatives=ref<Slot[]>([]);              // créneaux proposés après un SLOT_TAKEN
 const confirmed=ref<Appointment|null>(null);     // dernière réservation acceptée (201)
 const needsLogin=ref(false);                     // 401 à l'envoi : la demande attend la reconnexion
 const storageOk=ref(true);                       // IndexedDB indisponible (quota, navigation privée…)
 let timer:ReturnType<typeof setTimeout>|undefined;

 function start(userId:number,professional:Professional,slot:Slot){
  confirmed.value=null;alternatives.value=[];errors.value=[];needsLogin.value=false;
  draft.value={userId,professionalId:professional.id,professionalName:professional.name,slotId:slot.id,startsAt:slot.startsAt,
   reasonCode:null,forWhom:null,relativeName:'',phone:'',status:'editing',idemKey:null,problem:null};
  open.value=true;void saveNow();
 }
 async function saveNow(){
  clearTimeout(timer);
  const d=draft.value;if(!d)return;
  try{await (await db()).put('drafts',toStored(d));storageOk.value=true}
  catch{storageOk.value=false}
 }
 // Écrit 500 ms après la dernière frappe, réseau ou pas.
 watch(draft,d=>{clearTimeout(timer);if(d)timer=setTimeout(()=>void saveNow(),SAVE_DELAY_MS)},{deep:true});
 // Onglet caché (appel, verrouillage) : tout de suite.
 if(typeof document!=='undefined')document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='hidden')void saveNow()});

 /** « Valider » ne touche pas au réseau : la demande entre dans la file. En ligne ou non, un seul chemin. */
 async function submit(){
  const d=draft.value;if(!d||d.status==='pending')return;
  errors.value=validate(d);if(errors.value.length)return;
  const idemKey=d.idemKey??crypto.randomUUID();
  const next:BookingDraft={...d,idemKey,status:'pending',problem:null};
  clearTimeout(timer);
  try{
   // File et brouillon écrits ensemble, ou pas du tout.
   const tx=(await db()).transaction(['drafts','outbox'],'readwrite');
   await Promise.all([
    tx.objectStore('outbox').put({idemKey,userId:d.userId,body:toRequest(d),queuedAt:Date.now()}),
    tx.objectStore('drafts').put(toStored(next)),
    tx.done,
   ]);
   storageOk.value=true;
  }catch{storageOk.value=false;return}
  draft.value=next;alternatives.value=[];
  void flush();
 }
 /** Après un refus : nouveau créneau = nouvelle intention = nouvelle clé. La saisie est gardée. */
 function chooseAgain(slot:Slot){
  const d=draft.value;if(!d)return;
  draft.value={...d,slotId:slot.id,startsAt:slot.startsAt,status:'editing',idemKey:null,problem:null};
  alternatives.value=[];errors.value=[];
 }
 function resume(stored:StoredDraft){draft.value=fromStored(stored);open.value=true;errors.value=[];}
 /** Demande en attente retrouvée au démarrage : gardée en mémoire, formulaire fermé, la file l'enverra. */
 function restorePending(stored:StoredDraft){draft.value=fromStored(stored);open.value=false;}
 /** Oublie la mémoire seulement (IndexedDB est nettoyée par done, discardDraft ou clearOfflineData). */
 function forget(){clearTimeout(timer);draft.value=null;open.value=false;errors.value=[];alternatives.value=[];needsLogin.value=false;}
 /** Fermer : une demande en attente continue en arrière-plan ; une saisie abandonnée est effacée. */
 async function close(){
  const d=draft.value;
  if(!d||d.status==='pending'){open.value=false;return}
  forget();await discardDraft(d.userId).catch(()=>{});
 }
 return {draft,open,errors,alternatives,confirmed,needsLogin,storageOk,start,saveNow,submit,chooseAgain,resume,restorePending,forget,close};
});

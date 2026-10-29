import {defineStore} from 'pinia';import {ref} from 'vue';import {api,ApiError,refreshCsrf} from '../api';import {isUnreachable} from './experiment';
// Mode hors ligne du groupe B : une demande de réservation faite sans réseau est gardée
// dans le navigateur, puis envoyée au serveur dès le retour du réseau.
// Le serveur reste seul juge : le créneau a pu être pris entre-temps (409).
export interface PendingBooking {userId:number;slotId:number;professionalName:string;startsAt:string;token:string|null;savedAt:string}
// confirmed : réservée · conflict : créneau pris · waiting : toujours hors ligne
// login : session expirée, la demande attend la reconnexion · failed : refusée, abandonnée · none : rien à envoyer
export type SendResult='none'|'confirmed'|'conflict'|'waiting'|'login'|'failed';
const KEY='medislot.offline-booking';
function read():PendingBooking|null{
 try{const p=JSON.parse(localStorage.getItem(KEY)||'null');return p&&typeof p.userId==='number'&&typeof p.slotId==='number'&&typeof p.startsAt==='string'?p:null}catch{return null}
}
function write(p:PendingBooking|null){try{if(p)localStorage.setItem(KEY,JSON.stringify(p));else localStorage.removeItem(KEY)}catch{}}
export const useOfflineBooking=defineStore('offlineBooking',()=>{
 const pending=ref<PendingBooking|null>(read());
 let sending:Promise<SendResult>|null=null;
 // Une seule demande en attente : une nouvelle remplace la précédente.
 function save(p:PendingBooking){pending.value=p;write(p)}
 function discard(){pending.value=null;write(null)}
 function post(p:PendingBooking){return api('/appointments',{method:'POST',body:JSON.stringify({slotId:p.slotId}),headers:p.token?{'X-AB-Token':p.token}:{}})}
 // beforeSend : envoyer d'abord l'exposition en attente, pour que la conversion puisse être comptée.
 function send(userId:number,beforeSend?:()=>Promise<void>):Promise<SendResult>{
  const p=pending.value;
  if(!p||p.userId!==userId)return Promise.resolve('none');
  if(!sending)sending=(async():Promise<SendResult>=>{
   try{
    await beforeSend?.();
    try{await post(p)}
    catch(e){if(!(e instanceof ApiError&&e.status===403))throw e;await refreshCsrf();await post(p);} // jeton CSRF périmé : un seul nouvel essai
    discard();return 'confirmed';
   }catch(e){
    if(isUnreachable(e))return 'waiting';
    if(e instanceof ApiError&&(e.status===401||e.status===403))return 'login';
    discard();return e instanceof ApiError&&e.status===409?'conflict':'failed';
   }finally{sending=null}
  })();
  return sending;
 }
 return {pending,save,discard,send};
});

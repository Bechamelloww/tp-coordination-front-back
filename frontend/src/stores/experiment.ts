import {defineStore} from 'pinia';import {ref,computed} from 'vue';import {api,ApiError} from '../api';
export type Variant='A'|'B';
// token : JWT signé par le serveur, null hors expérience.
export interface Assignment {experiment:string;enabled:boolean;participating:boolean;token:string|null}
// Seul le texte du bouton du formulaire change entre A et B : même style, même action.
export const confirmLabels:Record<Variant,string>={A:'Valider',B:'Réserver ce créneau'};
interface TokenPayload {sub?:string;experiment?:string;variant?:string;exp?:number}
// Lit la charge du jeton (partie centrale, base64url). Le front ne vérifie pas la signature :
// il n'a pas le secret. C'est le serveur qui la vérifie avant de compter.
export function tokenPayload(token:string):TokenPayload|null{
 try{return JSON.parse(atob(token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/')))}catch{return null}
}
export function variantFromToken(token:string):Variant|null{const v=tokenPayload(token)?.variant;return v==='A'||v==='B'?v:null}
// Réseau coupé ou serveur injoignable, par opposition à une réponse métier (400, 401, 409…).
export function isUnreachable(e:unknown){return e instanceof TypeError||(e instanceof ApiError&&[502,503,504].includes(e.status))}

// Jeton et exposition en attente, gardés dans le navigateur pour survivre à une coupure ou un rechargement.
const KEY='medislot.ab';
interface Saved {userId:number;token:string;pendingExposure:boolean}
function readSaved(userId:number):Saved|null{
 try{
  const s=JSON.parse(localStorage.getItem(KEY)||'null');
  if(!s||s.userId!==userId||typeof s.token!=='string')return null;
  const p=tokenPayload(s.token);
  if(!p||p.sub!==String(userId)||typeof p.exp!=='number'||p.exp*1000<=Date.now())return null;
  return {userId,token:s.token,pendingExposure:s.pendingExposure===true};
 }catch{return null}
}
function writeSaved(s:Saved|null){try{if(s)localStorage.setItem(KEY,JSON.stringify(s));else localStorage.removeItem(KEY)}catch{}}

export const useExperiment=defineStore('experiment',()=>{
 const variant=ref<Variant|null>(null),token=ref<string|null>(null);
 const online=ref(typeof navigator==='undefined'||navigator.onLine);
 let userId:number|null=null,pending:Promise<void>|null=null,exposedThisPage=false,pendingExposure=false,flushing:Promise<void>|null=null;
 function persist(){writeSaved(userId!==null&&token.value?{userId,token:token.value,pendingExposure}:null)}
 function reset(){variant.value=null;token.value=null;userId=null;pending=null;exposedThisPage=false;pendingExposure=false;}
 function clear(){reset();writeSaved(null)}
 function use(t:string|null,keepPendingExposure:boolean){
  const v=t?variantFromToken(t):null;
  variant.value=v??'A';token.value=v?t:null;pendingExposure=v!==null&&keepPendingExposure;persist();
 }
 // Une affectation par patient connecté. Serveur injoignable : on garde la version déjà reçue
 // (jeton encore valide) au lieu de basculer sur A. Autre erreur : A sans mesure.
 function load(id:number){
  if(userId===id&&pending)return pending;
  reset();userId=id;const saved=readSaved(id);
  pending=api<Assignment>('/ab/assignment')
   .then(a=>{if(userId!==id)return;use(a.enabled&&a.participating?a.token:null,saved?.pendingExposure??false);return flushExposure();})
   .catch(e=>{if(userId!==id)return;if(isUnreachable(e)&&saved)use(saved.token,saved.pendingExposure);else{variant.value='A';token.value=null;}});
  return pending;
 }
 // L'exposition est d'abord notée dans le navigateur, puis envoyée. Hors ligne, elle part au retour du réseau.
 // Ce mécanisme s'applique à A comme à B, pour mesurer les deux versions de la même façon.
 async function expose(){if(!token.value||exposedThisPage)return;exposedThisPage=true;pendingExposure=true;persist();await flushExposure();}
 function flushExposure():Promise<void>{
  const t=token.value;
  if(!t||!pendingExposure)return Promise.resolve();
  if(!flushing)flushing=(async()=>{
   try{await api('/ab/exposure',{method:'POST',headers:{'X-AB-Token':t}});pendingExposure=false;}
   catch(e){if(!isUnreachable(e))pendingExposure=false;}
   finally{persist();flushing=null;}
  })();
  return flushing;
 }
 if(typeof window!=='undefined'){
  window.addEventListener('online',()=>{online.value=true;flushExposure();});
  window.addEventListener('offline',()=>{online.value=false;});
 }
 const ready=computed(()=>variant.value!==null);
 const participating=computed(()=>token.value!==null);
 const confirmLabel=computed(()=>variant.value?confirmLabels[variant.value]:'Chargement…');
 return {variant,token,online,participating,ready,confirmLabel,load,reset,clear,expose,flushExposure};
});

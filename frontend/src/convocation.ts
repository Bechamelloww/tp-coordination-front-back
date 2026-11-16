// Convocation PDF d'un rendez-vous : récupération, affichage dans un onglet, enregistrement.
// Contrat : GET /api/appointments/{id}/convocation.pdf[?download=true] (voir CONVOCATION.md §1).
import {currentClinic} from './tenant';

/** Une phrase par code : le patient ne voit jamais de JSON. */
export const MESSAGES:Record<number,string>={
 401:'Votre session a expiré. Reconnectez-vous pour obtenir votre convocation.',
 404:'Ce rendez-vous est introuvable sur votre compte.',
 409:'Ce rendez-vous a été annulé : il n’a plus de convocation.',
 500:'La convocation n’a pas pu être préparée. Réessayez dans quelques minutes.',
};
export const FALLBACK='La convocation est momentanément indisponible. Réessayez plus tard.';
export const OFFLINE='Pas de connexion : la convocation ne peut pas être téléchargée pour le moment.';
export const NOT_PDF='Le serveur n’a pas renvoyé de PDF. Rechargez la page puis réessayez.';
/** Durée de vie de l'URL blob d'un onglet ouvert : le temps que le lecteur PDF la charge. */
export const REVOKE_AFTER_MS=60_000;

export class ConvocationError extends Error {
 constructor(message:string,public status:number){super(message);this.name='ConvocationError';}
}

export function convocationUrl(id:number,download=false):string{
 return `/api/appointments/${id}/convocation.pdf${download?'?download=true':''}`;
}

/** Le nom proposé par le serveur (Content-Disposition), sinon un nom neutre : le numéro seul. */
export function filenameFrom(header:string|null,id:number):string{
 const name=header?.match(/filename="?([^";]+)"?/)?.[1]?.trim();
 return name||`convocation-${id}.pdf`;
}

export interface Convocation {blob:Blob;filename:string}

/**
 * Récupère le PDF. Ordre : réseau, puis r.ok AVANT r.blob() (un 404 a aussi un corps, en JSON),
 * puis le type : un 200 qui n'est pas un PDF (index.html servi par le repli de Nginx ou de Vite) est refusé.
 */
export async function fetchConvocation(id:number,{download=false}:{download?:boolean}={}):Promise<Convocation>{
 let r:Response;
 try{
  const headers:Record<string,string>={Accept:'application/pdf, application/problem+json'},clinic=currentClinic();if(clinic)headers['X-Clinic']=clinic;
  r=await fetch(convocationUrl(id,download),{credentials:'same-origin',headers});
 }catch{throw new ConvocationError(OFFLINE,0);}
 if(!r.ok){
  let message=MESSAGES[r.status]??(r.status>=500?MESSAGES[500]:FALLBACK);
  const traceId=r.headers.get('X-Trace-Id');if(traceId&&r.status>=500)message+=` (réf. ${traceId})`;
  throw new ConvocationError(message,r.status);
 }
 if(!(r.headers.get('Content-Type')||'').toLowerCase().startsWith('application/pdf'))throw new ConvocationError(NOT_PDF,r.status);
 return {blob:await r.blob(),filename:filenameFrom(r.headers.get('Content-Disposition'),id)};
}

/** Un onglet ouvert d'avance, rempli quand le PDF arrive. */
export interface Tab {show(url:string):void;close():void}
/** Tout ce qui touche au navigateur passe par cet objet : les tests le remplacent. */
export interface Browser {
 /** Appelé pendant le clic, avant toute attente : sinon le bloqueur de fenêtres refuse. null si refusé. */
 openTab():Tab|null;
 save(blob:Blob,filename:string):void;
 objectUrl(blob:Blob):string;
 revoke(url:string):void;
 later(f:()=>void,ms:number):void;
}

/** Affiche la convocation dans un nouvel onglet. */
export async function openConvocation(id:number,browser:Browser):Promise<void>{
 const tab=browser.openTab(); // avant l'await : on est encore dans le geste de l'utilisateur
 try{
  const {blob,filename}=await fetchConvocation(id);
  if(!tab){browser.save(blob,filename);return;} // fenêtre bloquée : on enregistre plutôt que rien
  const url=browser.objectUrl(blob);
  tab.show(url);
  browser.later(()=>browser.revoke(url),REVOKE_AFTER_MS); // la mémoire du blob est rendue
 }catch(e){
  tab?.close(); // jamais d'onglet vide laissé ouvert
  throw e;
 }
}

/** Enregistre la convocation sous le nom proposé par le serveur. */
export async function saveConvocation(id:number,browser:Browser):Promise<void>{
 const {blob,filename}=await fetchConvocation(id,{download:true});
 browser.save(blob,filename);
}

/** Le vrai navigateur. */
export const realBrowser:Browser={
 openTab(){
  const w=window.open('','_blank');
  if(!w)return null;
  try{w.document.title='Convocation MediSlot';w.document.body.textContent='Préparation de votre convocation…';}catch{/* onglet déjà inaccessible */}
  return {show:url=>{w.location.href=url},close:()=>w.close()};
 },
 save(blob,filename){
  const url=URL.createObjectURL(blob);
  const a=document.createElement('a');a.href=url;a.download=filename;a.rel='noopener';
  document.body.append(a);a.click();a.remove();
  setTimeout(()=>URL.revokeObjectURL(url),10_000);
 },
 objectUrl:blob=>URL.createObjectURL(blob),
 revoke:url=>URL.revokeObjectURL(url),
 later:(f,ms)=>{setTimeout(f,ms)},
};

import type {BookingDraft,BookingRequestBody,FieldError,ReasonCode} from './types';
import type {Slot,Appointment} from '../types';
// Motifs : des codes de liste, jamais de texte libre sur la santé (minimisation).
export const reasons:Record<ReasonCode,string>={
 FIRST_VISIT:'Première consultation',FOLLOW_UP:'Consultation de suivi',
 PRESCRIPTION:'Renouvellement d’ordonnance',RESULTS:'Résultats d’examens',
};
// Mêmes règles que BookingRequest côté Spring : le front vérifie la forme sans réseau, le serveur a le dernier mot.
export const PHONE_PATTERN=/^0[1-9][0-9]{8}$/;
export function normalizePhone(raw:string):string|null{const p=raw.replace(/[\s.-]/g,'');return p===''?null:p}
export function validate(d:BookingDraft):FieldError[]{
 const errors:FieldError[]=[];
 if(!d.reasonCode)errors.push({field:'reasonCode',message:'Choisissez un motif.'});
 if(!d.forWhom)errors.push({field:'forWhom',message:'Indiquez pour qui est le rendez-vous.'});
 if(d.forWhom==='RELATIVE'){
  const name=d.relativeName.trim();
  if(!name)errors.push({field:'relativeName',message:'Indiquez le nom du proche.'});
  else if(name.length>120)errors.push({field:'relativeName',message:'120 caractères au plus.'});
 }
 const phone=normalizePhone(d.phone);
 if(phone!==null&&!PHONE_PATTERN.test(phone))errors.push({field:'phone',message:'Numéro attendu : 10 chiffres, commençant par 0.'});
 return errors;
}
export function toRequest(d:BookingDraft):BookingRequestBody{
 return {slotId:d.slotId,reasonCode:d.reasonCode!,forWhom:d.forWhom!,relativeName:d.forWhom==='RELATIVE'?d.relativeName.trim():null,phone:normalizePhone(d.phone)};
}
// Spring rapporte la règle croisée sous « relativeNamed » : on la remet sous le bon champ.
export function fromServer(errors?:FieldError[]):FieldError[]{
 return (errors??[]).map(e=>({field:e.field==='relativeNamed'?'relativeName':e.field,message:e.message}));
}
/**
 * Règle « un seul rendez-vous à venir par praticien » (le serveur la contrôle : 409 ALREADY_BOOKED).
 * Pour chaque praticien, le rendez-vous confirmé à venir le plus proche. excludeId : celui qu'on déplace, qui ne bloque pas.
 */
export function upcomingByPro(appointments:Appointment[],now:number,excludeId:number|null=null):Record<number,Appointment>{
 const byPro:Record<number,Appointment>={};
 for(const a of appointments){
  if(a.status!=='CONFIRMED'||a.id===excludeId||Date.parse(a.startsAt)<=now)continue;
  const current=byPro[a.professionalId];
  if(!current||Date.parse(a.startsAt)<Date.parse(current.startsAt))byPro[a.professionalId]=a;
 }
 return byPro;
}
/** Les n créneaux libres les plus proches de l'horaire perdu, hors ce créneau. */
export function nearest(slots:Slot[],slotId:number,startsAt:string,n=3):Slot[]{
 const t=Date.parse(startsAt);
 return slots.filter(s=>s.id!==slotId).sort((a,b)=>Math.abs(Date.parse(a.startsAt)-t)-Math.abs(Date.parse(b.startsAt)-t)).slice(0,n);
}

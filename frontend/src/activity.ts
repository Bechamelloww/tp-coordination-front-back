export type ActivityAction='APPOINTMENT_CREATED'|'APPOINTMENT_RESCHEDULED'|'APPOINTMENT_CANCELLED'|'ACCOUNT_CREATED'|'MEMBER_ADDED'|'CLINIC_CREATED'|'CLINIC_UPDATED'|'CLINIC_DELETED'|'LOGIN'|'GOOGLE_CALENDAR_LINKED'|'GOOGLE_CALENDAR_UNLINKED';
export interface ActivityEntry {id:number;createdAt:string;action:ActivityAction;detail:string|null;actorName:string|null;actorEmail:string|null;clinicSlug:string|null;clinicName:string|null}
export interface ActivityPage {entries:ActivityEntry[];more:boolean}
export interface ActivityFilters {clinic:string;action:''|ActivityAction;from:string;to:string;text:string}

export const OUTSIDE_CLINICS='__outside';

export const activityLabels:Record<ActivityAction,string>={
 APPOINTMENT_CREATED:'Rendez-vous réservé',
 APPOINTMENT_RESCHEDULED:'Rendez-vous déplacé',
 APPOINTMENT_CANCELLED:'Rendez-vous annulé',
 ACCOUNT_CREATED:'Inscription',
 MEMBER_ADDED:'Membre ajouté',
 CLINIC_CREATED:'Cabinet créé',
 CLINIC_UPDATED:'Cabinet modifié',
 CLINIC_DELETED:'Cabinet supprimé',
 LOGIN:'Connexion',
 GOOGLE_CALENDAR_LINKED:'Google Agenda connecté',
 GOOGLE_CALENDAR_UNLINKED:'Google Agenda déconnecté'
};

const clinicActions:ActivityAction[]=['APPOINTMENT_CREATED','APPOINTMENT_RESCHEDULED','APPOINTMENT_CANCELLED','ACCOUNT_CREATED','MEMBER_ADDED','CLINIC_CREATED','CLINIC_UPDATED'];

export function selectableActions(global:boolean):ActivityAction[]{
 return global?Object.keys(activityLabels) as ActivityAction[]:clinicActions;
}

export function activityKind(action:ActivityAction):'appointment'|'cancelled'|'admin'|'account'{
 if(action==='APPOINTMENT_CANCELLED')return 'cancelled';
 if(action.startsWith('APPOINTMENT_'))return 'appointment';
 if(action==='MEMBER_ADDED'||action.startsWith('CLINIC_'))return 'admin';
 return 'account';
}

const moment=new Intl.DateTimeFormat('fr-FR',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit',timeZone:'Europe/Paris'});
export function activityMoment(date:string):string{
 const part=Object.fromEntries(moment.formatToParts(new Date(date)).map(p=>[p.type,p.value]));
 return `${part.day}/${part.month}/${part.year} à ${part.hour}:${part.minute}`;
}

export const emptyFilters=():ActivityFilters=>({clinic:'',action:'',from:'',to:'',text:''});

export function activityPath(global:boolean,filters:ActivityFilters,before?:number):string{
 const query=new URLSearchParams();
 if(global&&filters.clinic===OUTSIDE_CLINICS)query.set('outside','true');
 else if(global&&filters.clinic)query.set('clinic',filters.clinic);
 if(filters.action)query.set('action',filters.action);
 if(filters.from)query.set('from',filters.from);
 if(filters.to)query.set('to',filters.to);
 if(filters.text.trim())query.set('text',filters.text.trim());
 if(before!==undefined)query.set('before',String(before));
 const search=query.toString();
 return `${global?'/master/activity':'/admin/activity'}${search?`?${search}`:''}`;
}

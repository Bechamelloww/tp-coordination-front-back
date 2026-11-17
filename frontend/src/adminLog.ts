import {api} from './api';

/** Ligne du journal d'activité (GET /api/admin/logs ou /api/master/logs). */
export interface LogEntry {id:number;createdAt:string;actor:string;action:string;detail:string;clinicSlug:string;clinicName:string}
/** Taille d'une page côté back : une page pleine veut dire qu'il en reste peut-être d'autres. */
export const LOG_PAGE=50;
export type LogScope='clinic'|'all';

const LABELS:Record<string,string>={
 APPOINTMENT_BOOKED:'Rendez-vous pris',APPOINTMENT_MOVED:'Rendez-vous déplacé',APPOINTMENT_CANCELLED:'Rendez-vous annulé',
 ACCOUNT_CREATED:'Compte créé',ACCOUNT_GOOGLE_LINKED:'Compte relié à Google',
 MEMBER_JOINED:'Nouveau membre',MEMBER_ADDED:'Membre ajouté',CLINIC_CREATED:'Cabinet créé',
};
export const actionLabel=(action:string)=>LABELS[action]??action;
/** Date complète, année comprise : le journal remonte loin. */
export const logDate=(date:string)=>new Intl.DateTimeFormat('fr-FR',{dateStyle:'short',timeStyle:'short',timeZone:'Europe/Paris'}).format(new Date(date));

/** clinic : journal du cabinet courant (admins) ; all : tous les cabinets (master). before : id de la dernière ligne déjà affichée. */
export function fetchLogs(scope:LogScope,before?:number):Promise<LogEntry[]>{
 const path=scope==='all'?'/master/logs':'/admin/logs';
 return api<LogEntry[]>(before===undefined?path:`${path}?before=${before}`);
}

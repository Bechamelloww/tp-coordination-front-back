export interface StatisticsSummary {total:number;confirmed:number;cancelled:number;upcoming:number;patients:number;lastThirtyDays:number;previousThirtyDays:number;upcomingSlots:number}
export interface ProfessionalStatistics {id:number;name:string;specialty:string;total:number;confirmed:number;cancelled:number;upcoming:number}
export interface ClinicStatistics {slug:string;name:string;city:string;color:string;summary:StatisticsSummary;professionals:ProfessionalStatistics[]}
export interface ClinicSummary {slug:string;name:string;city:string;color:string;professionals:number;summary:StatisticsSummary}
export interface NetworkStatistics {clinics:number;professionals:number;summary:StatisticsSummary;clinicSummaries:ClinicSummary[]}
export interface Indicator {label:string;value:string;detail:string}

const percentFormat=new Intl.NumberFormat('fr-FR',{style:'percent',maximumFractionDigits:0});
const numberFormat=new Intl.NumberFormat('fr-FR');

export function ratio(part:number,whole:number):number|null{return whole>0?part/whole:null}
export function percent(value:number|null):string{return value===null?'—':percentFormat.format(value)}
export function count(value:number):string{return numberFormat.format(value)}

export function trend(current:number,previous:number):string{
 if(previous===0)return current===0?'Aucune réservation sur les 60 derniers jours':'Pas de réservation sur les 30 jours précédents';
 const change=(current-previous)/previous;
 if(change===0)return 'Stable par rapport aux 30 jours précédents';
 return `${change>0?'+':'−'}${percentFormat.format(Math.abs(change))} par rapport aux 30 jours précédents`;
}

export function indicators(summary:StatisticsSummary):Indicator[]{
 return [
  {label:'Réservations totales',value:count(summary.total),detail:`${count(summary.confirmed)} confirmées · ${count(summary.upcoming)} à venir`},
  {label:'Taux d’annulation',value:percent(ratio(summary.cancelled,summary.total)),detail:`${count(summary.cancelled)} annulée${summary.cancelled>1?'s':''} sur ${count(summary.total)}`},
  {label:'Remplissage des créneaux à venir',value:percent(ratio(summary.upcoming,summary.upcomingSlots)),detail:`${count(summary.upcoming)} réservés sur ${count(summary.upcomingSlots)} créneaux`},
  {label:'Réservations sur 30 jours',value:count(summary.lastThirtyDays),detail:trend(summary.lastThirtyDays,summary.previousThirtyDays)},
  {label:'Patients distincts',value:count(summary.patients),detail:summary.patients?`${(summary.total/summary.patients).toLocaleString('fr-FR',{maximumFractionDigits:1})} réservation${summary.total/summary.patients>=2?'s':''} par patient`:'Aucun patient pour l’instant'},
 ];
}

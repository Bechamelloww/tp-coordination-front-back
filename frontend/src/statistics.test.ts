import {describe,expect,it} from 'vitest';
import {indicators,percent,ratio,trend,type StatisticsSummary} from './statistics';

const summary:StatisticsSummary={total:20,confirmed:16,cancelled:4,upcoming:6,patients:8,lastThirtyDays:12,previousThirtyDays:8,upcomingSlots:24};
const space=(text:string)=>text.replace(/[  ]/g,' ');

describe('statistiques',()=>{
 it('rend un ratio vide quand le total est nul',()=>{
  expect(ratio(3,0)).toBeNull();
  expect(percent(null)).toBe('—');
  expect(space(percent(ratio(1,4)))).toBe('25 %');
 });

 it('compare les 30 derniers jours aux 30 précédents',()=>{
  expect(space(trend(12,8))).toBe('+50 % par rapport aux 30 jours précédents');
  expect(space(trend(4,8))).toBe('−50 % par rapport aux 30 jours précédents');
  expect(trend(5,5)).toBe('Stable par rapport aux 30 jours précédents');
  expect(trend(3,0)).toBe('Pas de réservation sur les 30 jours précédents');
  expect(trend(0,0)).toBe('Aucune réservation sur les 60 derniers jours');
 });

 it('calcule les cinq indicateurs du cabinet',()=>{
  const values=indicators(summary).map(i=>[i.label,space(i.value)]);
  expect(values).toEqual([
   ['Réservations totales','20'],
   ['Taux d’annulation','20 %'],
   ['Remplissage des créneaux à venir','25 %'],
   ['Réservations sur 30 jours','12'],
   ['Patients distincts','8'],
  ]);
  expect(indicators(summary)[4].detail).toBe('2,5 réservations par patient');
 });

 it('reste lisible pour un cabinet sans réservation',()=>{
  const empty=indicators({total:0,confirmed:0,cancelled:0,upcoming:0,patients:0,lastThirtyDays:0,previousThirtyDays:0,upcomingSlots:0});
  expect(empty.map(i=>i.value)).toEqual(['0','—','—','0','0']);
  expect(empty[4].detail).toBe('Aucun patient pour l’instant');
 });
});

import {describe,it,expect} from 'vitest';
import {validate,toRequest,normalizePhone,fromServer,nearest,upcomingByPro} from './bookingRules';
import type {BookingDraft} from './types';
const draft=(over:Partial<BookingDraft>={}):BookingDraft=>({userId:1,professionalId:4,professionalName:'Dr Hugo Petit',slotId:10,startsAt:'2026-11-03T08:30:00Z',reasonCode:'FOLLOW_UP',forWhom:'SELF',relativeName:'',phone:'',status:'editing',idemKey:null,problem:null,...over});
describe('Règles du formulaire (mêmes que Spring)',()=>{
 it('accepte une demande complète, téléphone facultatif',()=>{expect(validate(draft())).toEqual([])});
 it('exige un motif et « pour qui »',()=>{
  expect(validate(draft({reasonCode:null,forWhom:null})).map(e=>e.field)).toEqual(['reasonCode','forWhom']);
 });
 it('exige le nom du proche, 120 caractères au plus',()=>{
  expect(validate(draft({forWhom:'RELATIVE'}))[0].field).toBe('relativeName');
  expect(validate(draft({forWhom:'RELATIVE',relativeName:'x'.repeat(121)}))[0].message).toBe('120 caractères au plus.');
 });
 it('normalise le téléphone et refuse un mauvais numéro',()=>{
  expect(normalizePhone('06 12.34-56 78')).toBe('0612345678');
  expect(normalizePhone('  ')).toBeNull();
  expect(validate(draft({phone:'12345'}))[0].field).toBe('phone');
 });
 it('construit le corps : créneau et champs, rien d’autre',()=>{
  expect(toRequest(draft({phone:'06 12 34 56 78',relativeName:'ignoré'}))).toEqual({slotId:10,reasonCode:'FOLLOW_UP',forWhom:'SELF',relativeName:null,phone:'0612345678'});
 });
 it('remet la règle croisée de Spring sous le champ du formulaire',()=>{
  expect(fromServer([{field:'relativeNamed',message:'Indiquez le nom du proche.'}])[0].field).toBe('relativeName');
 });
 it('propose les trois créneaux les plus proches, hors le créneau perdu',()=>{
  const at=(id:number,iso:string)=>({id,professionalId:4,startsAt:iso,endsAt:iso});
  const slots=[at(10,'2026-11-03T08:30:00Z'),at(11,'2026-11-03T09:00:00Z'),at(12,'2026-11-05T08:00:00Z'),at(13,'2026-11-03T13:00:00Z'),at(14,'2026-11-04T08:00:00Z')];
  expect(nearest(slots,10,'2026-11-03T08:30:00Z').map(s=>s.id)).toEqual([11,13,14]);
 });
});
describe('Un seul rendez-vous à venir par praticien',()=>{
 const now=Date.parse('2026-11-16T10:00:00Z');
 const appt=(id:number,professionalId:number,startsAt:string,status:'CONFIRMED'|'CANCELLED'='CONFIRMED')=>({id,slotId:id,professionalId,professionalName:'Dr',specialty:'general',address:'',city:'',startsAt,status});
 const list=[appt(1,3,'2026-11-20T08:00:00Z'),appt(2,3,'2026-11-18T08:00:00Z'),appt(3,4,'2026-11-10T08:00:00Z'),appt(4,5,'2026-11-19T08:00:00Z','CANCELLED'),appt(5,6,'2026-11-19T09:00:00Z')];
 it('garde le plus proche à venir par praticien, ignore passés et annulés',()=>{
  const m=upcomingByPro(list,now);
  expect(Object.keys(m).map(Number).sort()).toEqual([3,6]);
  expect(m[3].id).toBe(2);
 });
 it('le rendez-vous déplacé ne bloque pas son praticien',()=>{
  expect(upcomingByPro(list,now,5)[6]).toBeUndefined();
  expect(upcomingByPro(list,now,2)[3].id).toBe(1); // l'autre rendez-vous chez ce praticien bloque toujours
 });
});

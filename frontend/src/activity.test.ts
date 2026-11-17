import {describe,expect,it} from 'vitest';
import {activityMoment,activityPath,emptyFilters,OUTSIDE_CLINICS,selectableActions} from './activity';

describe('activityPath',()=>{
 it('interroge le cabinet courant pour un directeur, sans filtre de cabinet',()=>{
  expect(activityPath(false,{...emptyFilters(),clinic:'cabinet-b'})).toBe('/admin/activity');
 });
 it('interroge tous les cabinets pour le master',()=>{
  expect(activityPath(true,emptyFilters())).toBe('/master/activity');
  expect(activityPath(true,{...emptyFilters(),clinic:'cabinet-a'})).toBe('/master/activity?clinic=cabinet-a');
  expect(activityPath(true,{...emptyFilters(),clinic:OUTSIDE_CLINICS})).toBe('/master/activity?outside=true');
 });
 it('transmet les filtres et la page suivante',()=>{
  const path=activityPath(false,{clinic:'',action:'MEMBER_ADDED',from:'2026-11-01',to:'2026-11-17',text:'  alex  '},42);
  expect(path).toBe('/admin/activity?action=MEMBER_ADDED&from=2026-11-01&to=2026-11-17&text=alex&before=42');
 });
});

describe('activityMoment',()=>{
 it('affiche la date complète à l’heure de Paris',()=>{
  expect(activityMoment('2026-11-17T13:05:00Z')).toBe('17/11/2026 à 14:05');
 });
});

describe('selectableActions',()=>{
 it('masque au directeur les actions hors cabinet',()=>{
  expect(selectableActions(false)).not.toContain('LOGIN');
  expect(selectableActions(true)).toContain('LOGIN');
 });
});

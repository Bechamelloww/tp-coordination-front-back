import {describe,it,expect,vi,beforeEach} from 'vitest';
import {actionLabel,fetchLogs,logDate} from './adminLog';
import {setCurrentClinic} from './tenant';

beforeEach(()=>{vi.unstubAllGlobals();setCurrentClinic('cabinet-a')});
function stubFetch(){
 const fetchMock=vi.fn(async()=>new Response('[]',{status:200,headers:{'Content-Type':'application/json'}}));
 vi.stubGlobal('fetch',fetchMock);
 return fetchMock;
}

describe('journal d’administration',()=>{
 it('traduit les actions connues et garde le code des autres',()=>{
  expect(actionLabel('CLINIC_CREATED')).toBe('Cabinet créé');
  expect(actionLabel('MEMBER_ADDED')).toBe('Membre ajouté');
  expect(actionLabel('APPOINTMENT_BOOKED')).toBe('Rendez-vous pris');
  expect(actionLabel('NOUVELLE_ACTION')).toBe('NOUVELLE_ACTION');
  expect(logDate('2026-11-17T12:46:00Z')).toBe('17/11/2026 13:46');
 });

 it('lit le journal du cabinet courant, ou de tous les cabinets pour le master',async()=>{
  const fetchMock=stubFetch();
  await fetchLogs('clinic');
  await fetchLogs('all',42);
  const [[first,init],[second]]=fetchMock.mock.calls as unknown as [string,RequestInit][];
  expect(first).toBe('/api/admin/logs');
  expect(new Headers(init.headers).get('X-Clinic')).toBe('cabinet-a');
  expect(second).toBe('/api/master/logs?before=42');
 });
});

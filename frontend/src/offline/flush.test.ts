import {describe,it,expect,vi,beforeEach} from 'vitest';
import {createPinia,setActivePinia} from 'pinia';
import type {OutboxItem} from './types';

const outbox:OutboxItem[]=[];
vi.mock('./db',()=>({db:async()=>({getAll:async()=>outbox})}));
vi.mock('./clean',()=>({purgeExpired:async()=>{}}));
vi.mock('./done',()=>({done:vi.fn(async()=>{})}));
vi.mock('./refused',()=>({refused:vi.fn(async()=>{})}));
vi.mock('../stores/experiment',()=>({useExperiment:()=>({flushExposure:async()=>{},token:null})}));

import {flush} from './flush';
import {done} from './done';
import {refused} from './refused';
import {useSession} from '../stores/session';

const json=(status:number,body:unknown)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
const patientA={id:1,name:'A',email:'a@medislot.test',role:'PATIENT'};
const patientB={id:2,name:'B',email:'b@medislot.test',role:'PATIENT'};
const item={idemKey:'cle-a',userId:1,body:{slotId:7}} as unknown as OutboxItem;

function server(me:()=>unknown,booking:()=>Response){
 const calls:string[]=[];
 vi.stubGlobal('fetch',vi.fn(async(url:string)=>{
  calls.push(url);
  if(url==='/api/me')return json(200,me());
  if(url==='/api/csrf')return json(200,{token:'t',headerName:'X-CSRF-TOKEN'});
  if(url==='/api/appointments')return booking();
  return json(404,{});
 }));
 return calls;
}

describe('Envoi de la file hors ligne',()=>{
 beforeEach(()=>{
  vi.clearAllMocks();setActivePinia(createPinia());
  outbox.length=0;outbox.push(item);
  useSession().user=patientA;
 });
 it('envoie la réservation quand la session est toujours celle du patient',async()=>{
  const calls=server(()=>patientA,()=>json(201,{id:10}));
  await flush();
  expect(calls.filter(c=>c==='/api/appointments')).toHaveLength(1);
  expect(done).toHaveBeenCalledOnce();
 });
 it("n'envoie pas la file de A quand le cookie appartient désormais à B",async()=>{
  const calls=server(()=>patientB,()=>json(201,{id:10}));
  await flush();
  expect(calls).not.toContain('/api/appointments');
  expect(useSession().user?.id).toBe(2);
 });
 it('ne rejoue pas après un 403 si la session a changé entre-temps',async()=>{
  let me:unknown=patientA;
  const calls=server(()=>me,()=>{me=patientB;return json(403,{title:'refus'})});
  await flush();
  expect(calls.filter(c=>c==='/api/appointments')).toHaveLength(1);
  expect(done).not.toHaveBeenCalled();
  expect(vi.mocked(refused).mock.calls[0][1]).toMatchObject({status:401});
 });
});

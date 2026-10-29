import {describe,it,expect,vi,beforeEach} from 'vitest';
import {createPinia,setActivePinia} from 'pinia';
import {useOfflineBooking,type PendingBooking} from './offlineBooking';
const json=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
const request:PendingBooking={userId:1,slotId:42,professionalName:'Dr Amel Benali',startsAt:'2026-10-30T08:00:00Z',token:'jeton-B',savedAt:'2026-10-29T10:00:00Z'};
// Le serveur répond au jeton CSRF puis à la réservation avec la réponse fournie.
function server(booking:()=>Promise<Response>){
 const fetch=vi.fn(async(url:string,_init?:RequestInit)=>url==="/api/csrf"?json({token:'csrf',headerName:'X-CSRF-TOKEN'}):booking());
 vi.stubGlobal('fetch',fetch);return fetch;
}
describe('Mode hors ligne du groupe B',()=>{
 beforeEach(()=>{
  vi.restoreAllMocks();setActivePinia(createPinia());
  const data=new Map<string,string>();
  vi.stubGlobal('localStorage',{getItem:(k:string)=>data.get(k)??null,setItem:(k:string,v:string)=>{data.set(k,String(v))},removeItem:(k:string)=>{data.delete(k)}});
 });
 it('garde la demande après un rechargement',()=>{
  useOfflineBooking().save(request);
  setActivePinia(createPinia());
  expect(useOfflineBooking().pending?.slotId).toBe(42);
 });
 it('envoie la demande avec le jeton de variante au retour du réseau',async()=>{
  const fetch=server(async()=>json({id:7},201));
  const offline=useOfflineBooking();offline.save(request);
  expect(await offline.send(1)).toBe('confirmed');
  expect(offline.pending).toBeNull();
  const booking=fetch.mock.calls.find(([url])=>url==='/api/appointments')!;
  expect(new Headers((booking[1] as RequestInit).headers).get('X-AB-Token')).toBe('jeton-B');
 });
 it('garde la demande tant que le réseau est coupé',async()=>{
  server(async()=>{throw new TypeError('Failed to fetch')});
  const offline=useOfflineBooking();offline.save(request);
  expect(await offline.send(1)).toBe('waiting');
  expect(offline.pending).not.toBeNull();
 });
 it('abandonne la demande si le créneau a été pris entre-temps',async()=>{
  server(async()=>json({detail:'Ce créneau n’est plus disponible.'},409));
  const offline=useOfflineBooking();offline.save(request);
  expect(await offline.send(1)).toBe('conflict');
  expect(offline.pending).toBeNull();
 });
 it('attend la reconnexion si la session a expiré',async()=>{
  server(async()=>json({title:'Connexion requise'},401));
  const offline=useOfflineBooking();offline.save(request);
  expect(await offline.send(1)).toBe('login');
  expect(offline.pending).not.toBeNull();
 });
 it('n’envoie pas la demande d’un autre patient',async()=>{
  const fetch=server(async()=>json({id:7},201));
  const offline=useOfflineBooking();offline.save(request);
  expect(await offline.send(2)).toBe('none');
  expect(fetch).not.toHaveBeenCalled();
 });
});

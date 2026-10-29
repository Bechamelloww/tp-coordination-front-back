import {describe,it,expect,vi,beforeEach} from 'vitest';
import {createPinia,setActivePinia} from 'pinia';
import {useExperiment,variantFromToken} from './experiment';
const json=(body:unknown)=>new Response(JSON.stringify(body),{status:200,headers:{'Content-Type':'application/json'}});
const b64=(s:string)=>btoa(s).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
// Jeton au format JWT ; la signature n'est pas vérifiée côté front.
const token=(variant:string)=>`${b64('{"alg":"HS256","typ":"JWT"}')}.${b64(`{"sub":"1","experiment":"confirm_label_v1","variant":"${variant}","iat":0,"exp":9999999999}`)}.signature`;
const assignment=(t:string|null,participating=true)=>json({experiment:'confirm_label_v1',enabled:true,participating,token:t});
describe('Test A/B du bouton de confirmation',()=>{
 beforeEach(()=>{
  vi.restoreAllMocks();setActivePinia(createPinia());
  const data=new Map<string,string>();
  vi.stubGlobal('localStorage',{getItem:(k:string)=>data.get(k)??null,setItem:(k:string,v:string)=>{data.set(k,String(v))},removeItem:(k:string)=>{data.delete(k)}});
 });
 it('lit la variante dans la charge du jeton',()=>{
  expect(variantFromToken(token('B'))).toBe('B');
  expect(variantFromToken(token('C'))).toBeNull();
  expect(variantFromToken('pas-un-jeton')).toBeNull();
 });
 it('affiche le libellé B porté par le jeton',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(assignment(token('B'))));
  const ab=useExperiment();expect(ab.ready).toBe(false);
  await ab.load(1);
  expect(ab.confirmLabel).toBe('Réserver ce créneau');expect(ab.participating).toBe(true);
 });
 it('envoie le jeton avec l’exposition',async()=>{
  const fetch=vi.fn().mockResolvedValueOnce(assignment(token('B'))).mockResolvedValue(json({token:'csrf',headerName:'X-CSRF-TOKEN'}));vi.stubGlobal('fetch',fetch);
  const ab=useExperiment();await ab.load(1);await ab.expose();
  const exposure=fetch.mock.calls.find(([url])=>url==='/api/ab/exposure');
  expect(new Headers(exposure![1].headers).get('X-AB-Token')).toBe(token('B'));
 });
 it('retombe sur A sans mesure si l’affectation échoue',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockRejectedValue(new TypeError('réseau')));
  const ab=useExperiment();await ab.load(1);
  expect(ab.confirmLabel).toBe('Valider');expect(ab.participating).toBe(false);
 });
 it('ne déclare pas d’exposition hors expérience',async()=>{
  const fetch=vi.fn().mockResolvedValue(assignment(null,false));vi.stubGlobal('fetch',fetch);
  const ab=useExperiment();await ab.load(1);await ab.expose();
  expect(ab.confirmLabel).toBe('Valider');expect(fetch).toHaveBeenCalledTimes(1);
 });
 it('garde la version B si le serveur est injoignable au rechargement',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(assignment(token('B'))));
  await useExperiment().load(1);
  setActivePinia(createPinia());
  vi.stubGlobal('fetch',vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
  const ab=useExperiment();await ab.load(1);
  expect(ab.confirmLabel).toBe('Réserver ce créneau');expect(ab.participating).toBe(true);
 });
 it('renvoie l’exposition faite hors ligne au retour du réseau',async()=>{
  vi.stubGlobal('fetch',vi.fn(async(url:string)=>url==='/api/ab/assignment'?assignment(token('A')):Promise.reject(new TypeError('Failed to fetch'))));
  const ab=useExperiment();await ab.load(1);await ab.expose();
  const fetch=vi.fn(async(url:string)=>url==='/api/csrf'?json({token:'csrf',headerName:'X-CSRF-TOKEN'}):new Response(null,{status:204}));vi.stubGlobal('fetch',fetch);
  await ab.flushExposure();
  expect(fetch.mock.calls.some(([url])=>url==='/api/ab/exposure')).toBe(true);
 });
});

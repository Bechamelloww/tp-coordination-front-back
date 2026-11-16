import {describe,it,expect,vi,beforeEach} from 'vitest';
import {clinicFromPath,clinicFromHost,safeColor,withClinic,setCurrentClinic} from './tenant';
import {api} from './api';
describe('Cabinet lu dans l\'URL',()=>{
 it('lit le slug dans le chemin /c/<slug>/',()=>{
  expect(clinicFromPath('/c/cabinet-b/')).toBe('cabinet-b');
  expect(clinicFromPath('/c/cabinet-a/rendez-vous')).toBe('cabinet-a');
 });
 it('rend null sans slug valide',()=>{
  expect(clinicFromPath('/')).toBeNull();
  expect(clinicFromPath('/c/')).toBeNull();
  expect(clinicFromPath('/c/Cabinet_A/')).toBeNull();
  expect(clinicFromPath('/autre/cabinet-a')).toBeNull();
 });
 it('lit le slug dans le nom d\'hôte, port compris',()=>{
  expect(clinicFromHost('cabinet-a.medislot.test')).toBe('cabinet-a');
  expect(clinicFromHost('cabinet-b.medislot.test:8088')).toBe('cabinet-b');
 });
 it('refuse un nom d\'hôte qui ne se termine pas par le domaine',()=>{
  expect(clinicFromHost('cabinet-a.medislot.test.pirate.example')).toBeNull();
  expect(clinicFromHost('pirate-medislot.test')).toBeNull();
  expect(clinicFromHost('localhost')).toBeNull();
 });
});
describe('Couleur du bandeau',()=>{
 it('accepte #rrggbb',()=>expect(safeColor('#0F766E')).toBe('#0f766e'));
 it('remplace tout le reste par la couleur par défaut',()=>{
  for(const bad of ['red','#fff','url(javascript:alert(1))','#12345g',null,undefined,42])expect(safeColor(bad)).toBe('#0873b9');
 });
});
describe('En-tête X-Clinic',()=>{
 beforeEach(()=>{vi.restoreAllMocks();setCurrentClinic(null)});
 it('est envoyé quand un cabinet est posé',async()=>{
  const fetchMock=vi.fn().mockResolvedValue(new Response('[]',{status:200}));vi.stubGlobal('fetch',fetchMock);
  setCurrentClinic('cabinet-b');await api('/professionals');
  expect(new Headers(fetchMock.mock.calls[0][1].headers).get('X-Clinic')).toBe('cabinet-b');
 });
 it('est absent sans cabinet',async()=>{
  const fetchMock=vi.fn().mockResolvedValue(new Response('[]',{status:200}));vi.stubGlobal('fetch',fetchMock);
  await api('/professionals');
  expect(new Headers(fetchMock.mock.calls[0][1].headers).has('X-Clinic')).toBe(false);
 });
 it('ignore un slug invalide',()=>{setCurrentClinic('A B');expect(withClinic('/api/events')).toBe('/api/events')});
 it('passe dans l\'adresse des flux temps réel',()=>{setCurrentClinic('cabinet-a');expect(withClinic('/api/events')).toBe('/api/events?clinic=cabinet-a')});
});

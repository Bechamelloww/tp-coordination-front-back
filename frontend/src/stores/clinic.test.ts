// @vitest-environment happy-dom
import {describe,it,expect,vi,beforeEach} from 'vitest';
import {createPinia,setActivePinia} from 'pinia';
import {useClinic} from './clinic';
import {currentClinic,setCurrentClinic} from '../tenant';
vi.mock('../api',()=>({api:vi.fn()}));
import {api} from '../api';
const A={slug:'cabinet-a',name:'Cabinet des Tilleuls',city:'Paris',color:'#1d4ed8'};
const B={slug:'cabinet-b',name:'Maison de santé du Canal',city:'Lyon',color:'#0f766e'};
describe('Store du cabinet courant',()=>{
 beforeEach(()=>{setActivePinia(createPinia());setCurrentClinic(null);vi.mocked(api).mockReset();history.replaceState(null,'','/')});
 it('au chargement, prend le cabinet de l\'URL',async()=>{
  history.replaceState(null,'','/c/cabinet-b/');vi.mocked(api).mockResolvedValue(B);
  const clinic=useClinic();await clinic.init();
  expect(currentClinic()).toBe('cabinet-b');expect(clinic.current?.name).toBe(B.name);
 });
 it('sans cabinet dans l\'URL, prend cabinet-a et corrige l\'adresse',async()=>{
  vi.mocked(api).mockResolvedValue(A);const clinic=useClinic();await clinic.init();
  expect(currentClinic()).toBe('cabinet-a');expect(location.pathname).toBe('/c/cabinet-a/');
 });
 it('bascule vers un cabinet dont on est membre : URL et couleur suivent',async()=>{
  vi.mocked(api).mockResolvedValueOnce(A).mockResolvedValueOnce([A,B]);
  const clinic=useClinic();await clinic.init();await clinic.loadMine();
  expect(clinic.switchTo('cabinet-b')).toBe(true);
  expect(currentClinic()).toBe('cabinet-b');expect(location.pathname).toBe('/c/cabinet-b/');
  expect(document.documentElement.style.getPropertyValue('--clinic')).toBe('#0f766e');
 });
 it('refuse un cabinet dont on n\'est pas membre',async()=>{
  vi.mocked(api).mockResolvedValueOnce(A).mockResolvedValueOnce([A]);
  const clinic=useClinic();await clinic.init();await clinic.loadMine();
  expect(clinic.switchTo('cabinet-b')).toBe(false);
  expect(currentClinic()).toBe('cabinet-a');expect(clinic.current?.slug).toBe('cabinet-a');
 });
 it('refuse un slug inventé',async()=>{
  vi.mocked(api).mockResolvedValueOnce(A).mockResolvedValueOnce([A,B]);
  const clinic=useClinic();await clinic.init();await clinic.loadMine();
  expect(clinic.switchTo('../admin')).toBe(false);
 });
 it('admin seulement dans les cabinets où le serveur le dit',async()=>{
  vi.mocked(api).mockResolvedValueOnce(A).mockResolvedValueOnce([{...A,role:'ADMIN'},{...B,role:'MEMBER'}]);
  const clinic=useClinic();await clinic.init();await clinic.loadMine();
  expect(clinic.isAdmin).toBe(true);
  clinic.switchTo('cabinet-b');
  expect(clinic.isAdmin).toBe(false);
 });
});

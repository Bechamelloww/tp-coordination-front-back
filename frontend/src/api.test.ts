import {describe,it,expect,vi,beforeEach} from 'vitest';
import {api,ApiError} from './api';
describe('Front/back : statut et corps',()=>{
 beforeEach(()=>vi.restoreAllMocks());
 it('expose le message métier pour un conflit HTTP',async()=>{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({detail:'Créneau déjà réservé'}),{status:409})));await expect(api('/appointments')).rejects.toEqual(new ApiError('Créneau déjà réservé',409,{detail:'Créneau déjà réservé'}));});
 it('garde le code stable du ProblemDetail',async()=>{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({title:'Créneau déjà pris',detail:'Ce créneau n’est plus disponible.',code:'SLOT_TAKEN',professionalId:4}),{status:409})));const e=await api("/appointments").catch((x:unknown)=>x) as ApiError;expect(e).toBeInstanceOf(ApiError);expect(e.problem?.code).toBe('SLOT_TAKEN');expect(e.problem?.professionalId).toBe(4);});
 it('conserve un résultat vide comme succès',async()=>{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({items:[],total:0}),{status:200})));await expect(api('/professionals')).resolves.toEqual({items:[],total:0});});
 it('propage une panne réseau',async()=>{vi.stubGlobal('fetch',vi.fn().mockRejectedValue(new TypeError('Network failure')));await expect(api('/professionals')).rejects.toThrow('Network failure');});
});

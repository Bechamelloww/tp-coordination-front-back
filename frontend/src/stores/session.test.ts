import {describe,it,expect,vi,beforeEach} from 'vitest';
import {createPinia,setActivePinia} from 'pinia';
import {useSession} from './session';

const json=(status:number,body:unknown)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
const patient={id:1,name:'Camille',email:'patient@medislot.test',role:'PATIENT'};

function server(routes:Record<string,()=>Response>){
 const calls:{url:string;body:unknown}[]=[];
 vi.stubGlobal('fetch',vi.fn(async(url:string,init?:RequestInit)=>{
  calls.push({url,body:init?.body});
  if(url==='/api/csrf')return json(200,{token:'t',headerName:'X-CSRF-TOKEN'});
  return (routes[url]??(()=>json(404,{})))();
 }));
 return calls;
}

describe('Connexion en deux étapes',()=>{
 beforeEach(()=>{vi.restoreAllMocks();setActivePinia(createPinia());});
 it("demande le code quand le serveur répond 202, sans ouvrir la session",async()=>{
  const calls=server({'/api/login':()=>json(202,{etape:'code',destination:'p***@medislot.test'})});
  const s=useSession();
  expect(await s.login('patient@medislot.test','mot-de-passe-long')).toEqual({destination:'p***@medislot.test'});
  expect(s.user).toBeNull();
  expect(calls.map(c=>c.url)).not.toContain('/api/me');
 });
 it('ouvre la session directement sur un appareil reconnu (204)',async()=>{
  server({'/api/login':()=>new Response(null,{status:204}),'/api/me':()=>json(200,patient)});
  const s=useSession();
  expect(await s.login('patient@medislot.test','mot-de-passe-long')).toBeNull();
  expect(s.user?.email).toBe('patient@medislot.test');
 });
 it('envoie le code et le choix de mémoriser l’appareil, puis charge le compte',async()=>{
  const calls=server({'/api/login/code':()=>new Response(null,{status:204}),'/api/me':()=>json(200,patient)});
  const s=useSession();
  await s.verifyCode('123456',true);
  expect(JSON.parse(String(calls.find(c=>c.url==='/api/login/code')?.body))).toEqual({code:'123456',remember:true});
  expect(s.user?.id).toBe(1);
 });
 it('remonte le code d’erreur du serveur pour un code faux',async()=>{
  server({'/api/login/code':()=>json(401,{title:'Code incorrect.',code:'CODE_INCORRECT'})});
  await expect(useSession().verifyCode('000000',false)).rejects.toMatchObject({status:401,problem:{code:'CODE_INCORRECT'}});
 });
});

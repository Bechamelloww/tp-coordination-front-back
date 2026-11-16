import {describe,it,expect,vi,beforeEach} from 'vitest';
import {googleLoginEnabled,startGoogleLogin,readLoginReturn,LOGIN_RETURN_MESSAGES,LOGIN_START_FAILED} from './googleLogin';
import type {Browser} from './googleCalendar';

const CSRF={token:'jeton-csrf',headerName:'X-XSRF-TOKEN'};

function json(body:unknown,status=200){return new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}})}
function stubServer(routes:Record<string,()=>Response>){
 const calls:{method:string;path:string;csrf:string|null}[]=[];
 vi.stubGlobal('fetch',vi.fn(async(path:string,init:RequestInit={})=>{
  const method=(init.method||'GET').toUpperCase();
  calls.push({method,path,csrf:new Headers(init.headers).get(CSRF.headerName)});
  if(path==='/api/csrf')return json(CSRF);
  return routes[`${method} ${path}`]?.()??json({detail:'Introuvable.'},404);
 }));
 return calls;
}
function browserAt(url='http://localhost/'):Browser{return {currentUrl:()=>url,navigateTo:vi.fn(),replaceCurrentUrl:vi.fn()}}

beforeEach(()=>vi.unstubAllGlobals());

describe('connexion avec Google',()=>{
 it('le bouton n’apparaît que si le back l’active, et reste caché si le back ne répond pas',async()=>{
  stubServer({'GET /api/auth/google/enabled':()=>json({enabled:true})});
  expect(await googleLoginEnabled()).toBe(true);
  stubServer({});
  expect(await googleLoginEnabled()).toBe(false);
 });

 it('démarre par un POST protégé par CSRF puis envoie le navigateur chez Google',async()=>{
  const calls=stubServer({'POST /api/auth/google/start':()=>json({url:'https://accounts.google.com/o/oauth2/v2/auth?state=abc'})});
  const browser=browserAt();
  await startGoogleLogin(browser);
  expect(calls.find(c=>c.path==='/api/auth/google/start')?.csrf).toBe(CSRF.token);
  expect(browser.navigateTo).toHaveBeenCalledWith('https://accounts.google.com/o/oauth2/v2/auth?state=abc');
 });

 it('refuse une adresse de consentement qui n’est pas en HTTPS',async()=>{
  stubServer({'POST /api/auth/google/start':()=>json({url:'http://piege.test/'})});
  const browser=browserAt();
  await expect(startGoogleLogin(browser)).rejects.toThrow(LOGIN_START_FAILED);
  expect(browser.navigateTo).not.toHaveBeenCalled();
 });

 it.each(['google','refuse','erreur'] as const)('lit le retour %s et le retire de l’adresse',outcome=>{
  const browser=browserAt(`http://localhost/?connexion=${outcome}#haut`);
  expect(readLoginReturn(browser)).toEqual({outcome,message:LOGIN_RETURN_MESSAGES[outcome]});
  expect(browser.replaceCurrentUrl).toHaveBeenCalledWith('/#haut');
 });

 it('traite une valeur inconnue comme une erreur et ignore une adresse sans retour',()=>{
  expect(readLoginReturn(browserAt('http://localhost/?connexion=<script>'))?.outcome).toBe('erreur');
  expect(readLoginReturn(browserAt('http://localhost/'))).toBeNull();
 });
});

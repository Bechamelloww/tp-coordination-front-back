import {describe,it,expect,vi,beforeEach} from 'vitest';
import {fetchGoogleStatus,connectGoogle,disconnectGoogle,readGoogleReturn,googleErrorMessage,realBrowser,RETURN_MESSAGES,SESSION_EXPIRED_MESSAGE,OFFLINE_MESSAGE,FALLBACK_MESSAGE,INVALID_CONSENT_URL_MESSAGE,type Browser} from './googleCalendar';
import {ApiError} from './api';

const CSRF_TOKEN={token:'jeton-csrf',headerName:'X-XSRF-TOKEN'};
const CONSENT_URL='https://accounts.google.com/o/oauth2/v2/auth?client_id=medislot&state=abc';

interface RecordedRequest {method:string;path:string;csrfHeader:string|null}

function jsonResponse(body:unknown,httpStatus=200):Response{
 return new Response(JSON.stringify(body),{status:httpStatus,headers:{'Content-Type':'application/json'}});
}

function stubServer(responsesByRoute:Record<string,()=>Response>):RecordedRequest[]{
 const recordedRequests:RecordedRequest[]=[];
 const fakeFetch=vi.fn(async(path:string,init:RequestInit={})=>{
  const method=(init.method||'GET').toUpperCase();
  recordedRequests.push({method,path,csrfHeader:new Headers(init.headers).get(CSRF_TOKEN.headerName)});
  if(path==='/api/csrf')return jsonResponse(CSRF_TOKEN);
  const buildResponse=responsesByRoute[`${method} ${path}`];
  return buildResponse?buildResponse():jsonResponse({detail:'Introuvable.'},404);
 });
 vi.stubGlobal('fetch',fakeFetch);
 return recordedRequests;
}

function fakeBrowserAt(pageUrl='http://localhost/'):Browser{
 return {currentUrl:()=>pageUrl,navigateTo:vi.fn(),replaceCurrentUrl:vi.fn()};
}

beforeEach(()=>{
 vi.restoreAllMocks();
 vi.unstubAllGlobals();
});

describe('Statut de la liaison Google',()=>{
 it.each(['NON_CONNECTE','CONNECTE','A_RECONNECTER'] as const)('lit le statut %s',async expectedStatus=>{
  const recordedRequests=stubServer({'GET /api/google/status':()=>jsonResponse({enabled:true,status:expectedStatus})});
  await expect(fetchGoogleStatus()).resolves.toEqual({enabled:true,status:expectedStatus});
  expect(recordedRequests).toEqual([{method:'GET',path:'/api/google/status',csrfHeader:null}]);
 });

 it('rend enabled à false quand le serveur n’a pas de client Google',async()=>{
  stubServer({'GET /api/google/status':()=>jsonResponse({enabled:false,status:'NON_CONNECTE'})});
  expect((await fetchGoogleStatus()).enabled).toBe(false);
 });
});

describe('Connexion à Google',()=>{
 it('demande l’URL de consentement avec le jeton CSRF puis y envoie le navigateur',async()=>{
  const recordedRequests=stubServer({'POST /api/google/authorize':()=>jsonResponse({url:CONSENT_URL})});
  const browser=fakeBrowserAt();
  await connectGoogle(browser);
  expect(recordedRequests.at(-1)).toEqual({method:'POST',path:'/api/google/authorize',csrfHeader:CSRF_TOKEN.token});
  expect(browser.navigateTo).toHaveBeenCalledWith(CONSENT_URL);
 });

 it.each(['javascript:alert(1)','http://accounts.google.com/','pas une url'])('refuse de suivre « %s »',async unsafeUrl=>{
  stubServer({'POST /api/google/authorize':()=>jsonResponse({url:unsafeUrl})});
  const browser=fakeBrowserAt();
  await expect(connectGoogle(browser)).rejects.toThrow(INVALID_CONSENT_URL_MESSAGE);
  expect(browser.navigateTo).not.toHaveBeenCalled();
 });

 it('reste sur la page si le serveur refuse',async()=>{
  stubServer({'POST /api/google/authorize':()=>jsonResponse({detail:'Google Agenda n’est pas configuré.'},503)});
  const browser=fakeBrowserAt();
  await expect(connectGoogle(browser)).rejects.toBeInstanceOf(ApiError);
  expect(browser.navigateTo).not.toHaveBeenCalled();
 });
});

describe('Déconnexion de Google',()=>{
 it('supprime la liaison avec le jeton CSRF et accepte une réponse 204',async()=>{
  const recordedRequests=stubServer({'DELETE /api/google/link':()=>new Response(null,{status:204})});
  await expect(disconnectGoogle()).resolves.toBeUndefined();
  expect(recordedRequests.at(-1)).toEqual({method:'DELETE',path:'/api/google/link',csrfHeader:CSRF_TOKEN.token});
 });
});

describe('Messages d’erreur',()=>{
 it('traduit la session expirée, le réseau coupé et les pannes',()=>{
  expect(googleErrorMessage(new ApiError('Non authentifié',401))).toBe(SESSION_EXPIRED_MESSAGE);
  expect(googleErrorMessage(new ApiError('Google ne répond pas. (réf. abc)',502))).toBe('Google ne répond pas. (réf. abc)');
  expect(googleErrorMessage(new TypeError('Failed to fetch'))).toBe(OFFLINE_MESSAGE);
  expect(googleErrorMessage(new Error(INVALID_CONSENT_URL_MESSAGE))).toBe(INVALID_CONSENT_URL_MESSAGE);
  expect(googleErrorMessage('inattendu')).toBe(FALLBACK_MESSAGE);
 });
});

describe('Retour du consentement Google',()=>{
 it.each(['connecte','refuse','erreur'] as const)('affiche le message « %s » et nettoie l’adresse',outcome=>{
  const browser=fakeBrowserAt(`http://localhost/?google=${outcome}`);
  expect(readGoogleReturn(browser)).toEqual({outcome,message:RETURN_MESSAGES[outcome]});
  expect(browser.replaceCurrentUrl).toHaveBeenCalledWith('/');
 });

 it('conserve les autres paramètres et l’ancre',()=>{
  const browser=fakeBrowserAt('http://localhost/rdv?vue=liste&google=connecte#haut');
  expect(readGoogleReturn(browser)?.outcome).toBe('connecte');
  expect(browser.replaceCurrentUrl).toHaveBeenCalledWith('/rdv?vue=liste#haut');
 });

 it.each(['inconnu','toString',''])('traite la valeur « %s » comme un échec',unexpectedValue=>{
  const browser=fakeBrowserAt(`http://localhost/?google=${unexpectedValue}`);
  expect(readGoogleReturn(browser)).toEqual({outcome:'erreur',message:RETURN_MESSAGES.erreur});
  expect(browser.replaceCurrentUrl).toHaveBeenCalledWith('/');
 });

 it('ne fait rien quand la page ne vient pas de Google',()=>{
  const browser=fakeBrowserAt('http://localhost/?q=martin');
  expect(readGoogleReturn(browser)).toBeNull();
  expect(browser.replaceCurrentUrl).not.toHaveBeenCalled();
 });

 it('réécrit la vraie adresse avec history.replaceState sans ajouter d’entrée',()=>{
  const historyState={vue:'accueil'};
  const replaceState=vi.fn();
  const pushState=vi.fn();
  vi.stubGlobal('window',{location:{href:'http://localhost/?google=connecte',assign:vi.fn()},history:{state:historyState,replaceState,pushState}});
  expect(readGoogleReturn(realBrowser)?.outcome).toBe('connecte');
  expect(replaceState).toHaveBeenCalledWith(historyState,'','/');
  expect(pushState).not.toHaveBeenCalled();
 });

 it('quitte la page avec location.assign pour aller chez Google',()=>{
  const assign=vi.fn();
  vi.stubGlobal('window',{location:{href:'http://localhost/',assign},history:{state:null,replaceState:vi.fn()}});
  realBrowser.navigateTo(CONSENT_URL);
  expect(assign).toHaveBeenCalledWith(CONSENT_URL);
 });
});

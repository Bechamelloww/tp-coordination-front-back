import {describe,it,expect,vi,beforeEach} from 'vitest';
import {MESSAGES,FALLBACK,OFFLINE,NOT_PDF,REVOKE_AFTER_MS,ConvocationError,convocationUrl,filenameFrom,fetchConvocation,openConvocation,saveConvocation,type Browser,type Tab} from './convocation';

const PDF_HEADERS={'Content-Type':'application/pdf','Content-Disposition':'inline; filename="convocation-42.pdf"'};
const pdf=(headers:Record<string,string>=PDF_HEADERS)=>new Response(new Blob(['%PDF-1.7']),{status:200,headers});
const problem=(status:number)=>new Response(JSON.stringify({status,detail:'Rendez-vous introuvable.'}),{status,headers:{'Content-Type':'application/problem+json'}});

/** Faux navigateur : enregistre ce qui se passe, et dans quel ordre. */
function fakeBrowser(open=true){
 const events:string[]=[];const tab:Tab={show:vi.fn(url=>{events.push(`show ${url}`)}),close:vi.fn(()=>{events.push('close')})};
 let pending:(()=>void)|null=null;
 const browser:Browser={
  openTab:vi.fn(()=>{events.push('openTab');return open?tab:null}),
  save:vi.fn((_b:Blob,name:string)=>{events.push(`save ${name}`)}),
  objectUrl:vi.fn(()=>'blob:convocation-1'),
  revoke:vi.fn(url=>{events.push(`revoke ${url}`)}),
  later:vi.fn((f:()=>void)=>{pending=f}),
 };
 return {browser,tab,events,runLater:()=>pending?.()};
}

beforeEach(()=>{vi.restoreAllMocks();vi.unstubAllGlobals();});

describe('Contrat',()=>{
 it('construit l’URL d’affichage et celle d’enregistrement',()=>{
  expect(convocationUrl(42)).toBe('/api/appointments/42/convocation.pdf');
  expect(convocationUrl(42,true)).toBe('/api/appointments/42/convocation.pdf?download=true');
 });
 it('lit le nom proposé par le serveur, avec ou sans guillemets',()=>{
  expect(filenameFrom('attachment; filename="convocation-42.pdf"',42)).toBe('convocation-42.pdf');
  expect(filenameFrom('inline; filename=convocation-42.pdf',42)).toBe('convocation-42.pdf');
 });
 it('se rabat sur un nom neutre sans en-tête exploitable',()=>{
  expect(filenameFrom(null,7)).toBe('convocation-7.pdf');
  expect(filenameFrom('inline',7)).toBe('convocation-7.pdf');
 });
});

describe('Récupération du PDF',()=>{
 it('demande le PDF avec le cookie de session et rend blob + nom',async()=>{
  const f=vi.fn().mockResolvedValue(pdf());vi.stubGlobal('fetch',f);
  const c=await fetchConvocation(42);
  expect(f).toHaveBeenCalledWith('/api/appointments/42/convocation.pdf',expect.objectContaining({credentials:'same-origin'}));
  expect(c.filename).toBe('convocation-42.pdf');expect(await c.blob.text()).toBe('%PDF-1.7');
 });
 it.each([401,404,409,500])('transforme un %i en phrase, sans lire le corps comme un PDF',async status=>{
  const r=problem(status);const blob=vi.spyOn(r,'blob');vi.stubGlobal('fetch',vi.fn().mockResolvedValue(r));
  await expect(fetchConvocation(42)).rejects.toEqual(new ConvocationError(MESSAGES[status],status));
  expect(blob).not.toHaveBeenCalled();
 });
 it('donne un message générique pour un code imprévu, et la référence de trace pour une panne',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(problem(418)));
  await expect(fetchConvocation(42)).rejects.toThrow(FALLBACK);
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response('',{status:503,headers:{'X-Trace-Id':'abc123'}})));
  await expect(fetchConvocation(42)).rejects.toThrow(`${MESSAGES[500]} (réf. abc123)`);
 });
 it('refuse une réponse 200 qui n’est pas un PDF',async()=>{
  // Cas réel : le repli SPA de Nginx (try_files … /index.html) ou de Vite renvoie la page d'accueil en 200.
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response('<!doctype html><title>MediSlot</title>',{status:200,headers:{'Content-Type':'text/html'}})));
  await expect(fetchConvocation(42)).rejects.toEqual(new ConvocationError(NOT_PDF,200));
 });
 it('dit « hors ligne » quand le réseau échoue',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
  await expect(fetchConvocation(42)).rejects.toEqual(new ConvocationError(OFFLINE,0));
 });
});

describe('Affichage et mémoire',()=>{
 it('ouvre l’onglet avant la réponse, y affiche le PDF puis libère l’URL',async()=>{
  let answer!:(r:Response)=>void;vi.stubGlobal('fetch',vi.fn(()=>new Promise<Response>(ok=>{answer=ok})));
  const {browser,tab,events,runLater}=fakeBrowser();
  const done=openConvocation(42,browser);
  expect(events).toEqual(['openTab']); // déjà ouvert alors que le serveur n'a pas répondu
  answer(pdf());await done;
  expect(tab.show).toHaveBeenCalledWith('blob:convocation-1');
  expect(browser.later).toHaveBeenCalledWith(expect.any(Function),REVOKE_AFTER_MS);
  expect(browser.revoke).not.toHaveBeenCalled();runLater();
  expect(events).toEqual(['openTab','show blob:convocation-1','revoke blob:convocation-1']);
 });
 it('ferme l’onglet et remonte la phrase en cas d’erreur',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(problem(409)));
  const {browser,tab}=fakeBrowser();
  await expect(openConvocation(42,browser)).rejects.toThrow(MESSAGES[409]);
  expect(tab.close).toHaveBeenCalled();expect(tab.show).not.toHaveBeenCalled();expect(browser.objectUrl).not.toHaveBeenCalled();
 });
 it('enregistre le fichier si la fenêtre est bloquée',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(pdf()));
  const {browser,events}=fakeBrowser(false);
  await openConvocation(42,browser);
  expect(events).toEqual(['openTab','save convocation-42.pdf']);
 });
 it('enregistre avec download=true sous le nom proposé par le serveur',async()=>{
  const f=vi.fn().mockResolvedValue(pdf({'Content-Type':'application/pdf','Content-Disposition':'attachment; filename="convocation-42.pdf"'}));vi.stubGlobal('fetch',f);
  const {browser}=fakeBrowser();
  await saveConvocation(42,browser);
  expect(f.mock.calls[0][0]).toBe('/api/appointments/42/convocation.pdf?download=true');
  expect(browser.save).toHaveBeenCalledWith(expect.any(Blob),'convocation-42.pdf');
  expect(browser.openTab).not.toHaveBeenCalled();
 });
});

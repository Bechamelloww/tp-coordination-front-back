import {test,expect,type APIRequestContext} from '@playwright/test';
// Preuve de bout en bout : le navigateur ouvre la trace, le back s'y accroche, Jaeger reçoit les deux.
// Prérequis : docker compose up -d --build (Jaeger publié sur 127.0.0.1:16686).
const JAEGER=process.env.JAEGER_URL||'http://localhost:16686';
interface JSpan{spanID:string;operationName:string;processID:string;references:{refType:string;spanID:string}[];tags:{key:string;value:unknown}[]}
interface JTrace{spans:JSpan[];processes:Record<string,{serviceName:string}>}
/** Attend que la trace contienne des spans des deux services (le navigateur envoie par lots, toutes les 5 s environ). */
async function traceComplete(request:APIRequestContext,traceId:string):Promise<JTrace>{
 let trace:JTrace|undefined;
 await expect.poll(async()=>{
  const r=await request.get(`${JAEGER}/api/traces/${traceId}`);if(!r.ok())return [];
  trace=(await r.json()).data[0] as JTrace;
  return [...new Set(trace.spans.map(s=>trace!.processes[s.processID].serviceName))].sort();
 },{timeout:30_000,intervals:[1000]}).toEqual(['medislot-api','medislot-web']);
 return trace!;
}
test('une recherche produit une trace front puis back',async({page,request})=>{
 const saisie='Martin';
 await page.goto('/');await expect(page.getByText(/praticiens? à découvrir/)).toBeVisible();
 await page.getByPlaceholder('Nom, praticien…').fill(saisie);
 const appel=page.waitForRequest(r=>r.url().includes('/api/professionals?')&&r.url().includes(`q=${saisie}`));
 await page.getByRole('button',{name:/^Rechercher/}).click();
 const traceparent=(await (await appel).allHeaders())['traceparent'];
 expect(traceparent,'traceparent absent').toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/);
 const [,traceId,parentId]=traceparent.split('-');
 const trace=await traceComplete(request,traceId);
 const service=(s:JSpan)=>trace.processes[s.processID].serviceName;
 // Le span du navigateur : un span client « GET », dont l'identifiant est le parent-id de l'en-tête.
 const navigateur=trace.spans.find(s=>s.spanID===parentId);
 expect(navigateur&&service(navigateur)).toBe('medislot-web');
 expect(navigateur?.operationName).toBe('GET');
 // Le span serveur du back est l'ENFANT direct du span du navigateur.
 const serveur=trace.spans.find(s=>service(s)==='medislot-api'&&s.operationName.startsWith('GET /api/professionals'));
 expect(serveur,'span serveur du back absent').toBeDefined();
 expect(serveur!.references).toContainEqual(expect.objectContaining({refType:'CHILD_OF',spanID:parentId}));
 // Aucune saisie dans un attribut, ni côté navigateur ni côté back.
 for(const s of trace.spans)for(const t of s.tags)expect(String(t.value),`${service(s)} ${s.operationName} ${t.key}`).not.toContain(saisie);
});

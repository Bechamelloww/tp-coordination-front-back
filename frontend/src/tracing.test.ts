// @vitest-environment happy-dom
import {describe,it,expect,vi,beforeAll,beforeEach} from 'vitest';
import {SpanKind} from '@opentelemetry/api';
import {ATTR_URL_FULL} from '@opentelemetry/semantic-conventions';
import {InMemorySpanExporter,SimpleSpanProcessor} from '@opentelemetry/sdk-trace';
import {demarrerTraces,sansRequete} from './tracing';

// Le faux fetch est posé AVANT l'instrumentation, qui l'enveloppe comme le vrai.
const recus:Headers[]=[];
vi.stubGlobal('fetch',vi.fn(async(_url:unknown,init?:RequestInit)=>{recus.push(new Headers(init?.headers));return new Response('{"items":[],"total":0}',{status:200,headers:{'Content-Type':'application/json'}});}));

// Les spans restent en mémoire : on peut lire leurs identifiants et leurs attributs.
const exportateur=new InMemorySpanExporter();
demarrerTraces({processeur:new SimpleSpanProcessor({exporter:exportateur})});
let api:typeof import('./api').api;
beforeAll(async()=>{({api}=await import('./api'));});
beforeEach(()=>{recus.length=0;exportateur.reset();});
const TRACEPARENT=/^00-([0-9a-f]{32})-([0-9a-f]{16})-01$/;
/** Le span du navigateur dont l'identifiant est le parent-id envoyé dans traceparent (les spans se terminent après la réponse). */
async function spanDe(entetes:Headers){const [,traceId,parentId]=TRACEPARENT.exec(entetes.get('traceparent')??'')??[];return vi.waitFor(()=>{const s=exportateur.getFinishedSpans().find(x=>x.spanContext().traceId===traceId&&x.spanContext().spanId===parentId);expect(s).toBeDefined();return s!;});}

describe('Traces du navigateur',()=>{
 it('ajoute un en-tête traceparent W3C échantillonné',async()=>{
  await api('/professionals');
  expect(recus[0].get('traceparent')).toMatch(TRACEPARENT);
 });
 it('le span du navigateur est le parent annoncé au back',async()=>{
  await api('/professionals');
  const span=await spanDe(recus[0]);
  expect(span.kind).toBe(SpanKind.CLIENT);
  expect(span.resource.attributes['service.name']).toBe('medislot-web');
 });
 it('ne garde pas la saisie d\'une recherche dans url.full',async()=>{
  await api('/professionals?'+new URLSearchParams({q:'dermatologue',city:'Lyon'}));
  const url=String((await spanDe(recus[0])).attributes[ATTR_URL_FULL]);
  expect(url).toMatch(/\/api\/professionals$/);
  expect(url).not.toContain('dermatologue');
  expect(sansRequete('http://x/a?q=1#b')).toBe('http://x/a');
 });
 it('ne trace pas une tuile de carte, dont l\'adresse situe la personne',async()=>{
  await fetch('https://tile.openstreetmap.org/14/8411/5860.png');
  await api('/professionals');// témoin : celui-ci est tracé
  await spanDe(recus[1]);
  expect(recus[0].get('traceparent')).toBeNull();
  expect(exportateur.getFinishedSpans().map(s=>String(s.attributes[ATTR_URL_FULL]))).not.toContainEqual(expect.stringContaining('openstreetmap'));
 });
});

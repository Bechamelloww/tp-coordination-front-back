// Traces du navigateur : chaque fetch devient un span client « GET », et part avec l'en-tête traceparent.
// Le back (agent Java) lit cet en-tête et accroche ses spans sous celui du navigateur : une seule trace.
import {context,propagation,trace,type Context} from '@opentelemetry/api';
import {W3CTraceContextPropagator} from '@opentelemetry/core';
import {registerInstrumentations} from '@opentelemetry/instrumentation';
import {FetchInstrumentation} from '@opentelemetry/instrumentation-fetch';
import {OTLPTraceExporter} from '@opentelemetry/exporter-trace-otlp-http';
import {resourceFromAttributes} from '@opentelemetry/resources';
import {ATTR_SERVICE_NAME,ATTR_SERVICE_VERSION,ATTR_URL_FULL} from '@opentelemetry/semantic-conventions';
import {URLS_GEO_NON_TRACEES} from './geo';
import {BatchSpanProcessor,ParentBasedSampler,StackContextManager,TraceIdRatioBasedSampler,TracerProvider,type ReadableSpan,type Span,type SpanProcessor} from '@opentelemetry/sdk-trace';

/** Ancien nom de url.full, encore émis par certaines instrumentations. */
const HTTP_URL='http.url';

/** Retire la partie « ?… » (et « #… ») d'une adresse : la saisie d'une recherche ne quitte pas le navigateur. */
export function sansRequete(url:string):string{return url.split(/[?#]/,1)[0];}

/**
 * Garde-fou données de santé, côté SDK : appelé au démarrage de chaque span, quand il est encore modifiable.
 * Réécrit url.full sans sa requête (?q=dermatologue&city=… ne part jamais vers le collecteur).
 */
export class ProcesseurSansSaisie implements SpanProcessor {
 onStart(span:Span,_parent:Context):void{
  for(const cle of [ATTR_URL_FULL,HTTP_URL]){
   const valeur=span.attributes[cle];
   if(typeof valeur==='string'&&/[?#]/.test(valeur))span.setAttribute(cle,sansRequete(valeur));
  }
 }
 onEnd(_span:ReadableSpan):void{}
 forceFlush():Promise<void>{return Promise.resolve();}
 shutdown():Promise<void>{return Promise.resolve();}
}

export interface OptionsTraces {
 /** Processeur qui reçoit les spans terminés. Par défaut : envoi par lots vers /otlp/ (même origine, relayé par Nginx). Les tests passent un SimpleSpanProcessor en mémoire. */
 processeur?:SpanProcessor;
 /** Part des traces gardées quand le navigateur ouvre la trace. 1 en formation ; 0,1 garde une trace sur dix. */
 ratio?:number;
}

let fournisseur:TracerProvider|null=null;

/** À appeler avant createApp : le premier fetch est déjà tracé. Sans effet au second appel. */
export function demarrerTraces(options:OptionsTraces={}):TracerProvider{
 if(fournisseur)return fournisseur;
 // 1. Le fournisseur : nom du service dans Jaeger, à côté de medislot-api, et version du code.
 // 2. L'échantillonnage : le navigateur décide, le back suit (ParentBased des deux côtés).
 // 3. Les processeurs : le garde-fou d'abord, puis l'envoi (par lots, vers la même origine que la page).
 const processeur=options.processeur??new BatchSpanProcessor({exporter:new OTLPTraceExporter({url:location.origin+'/otlp/v1/traces'})});
 fournisseur=new TracerProvider({
  resource:resourceFromAttributes({[ATTR_SERVICE_NAME]:'medislot-web',[ATTR_SERVICE_VERSION]:import.meta.env.VITE_GIT_SHA||'local'}),
  sampler:new ParentBasedSampler({root:new TraceIdRatioBasedSampler(options.ratio??1)}),
  spanProcessors:[new ProcesseurSansSaisie(),processeur],
 });
 trace.setGlobalTracerProvider(fournisseur);
 // 4. Contexte et propagateur : sans ces deux lignes, les spans existent mais traceparent ne part pas.
 context.setGlobalContextManager(new StackContextManager().enable());
 propagation.setGlobalPropagator(new W3CTraceContextPropagator());
 // 5. L'instrumentation fetch : un span par appel, sauf l'envoi des traces lui-même (sinon chaque lot en crée un autre)
 //    et les services géographiques, dont le chemin contient la position de la personne (donnée à ne pas tracer).
 registerInstrumentations({instrumentations:[new FetchInstrumentation({ignoreUrls:[/\/otlp\//,...URLS_GEO_NON_TRACEES]})]});
 return fournisseur;
}

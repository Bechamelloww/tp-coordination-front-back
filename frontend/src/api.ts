let csrf:{token:string;headerName:string}|null=null;
// Corps ProblemDetail (RFC 9457) renvoyé par Spring, avec les extensions de MediSlot.
export interface Problem {title?:string;detail?:string;code?:string;professionalId?:number;errors?:{field:string;message:string}[]}
/** Erreur d'API. fields : le message de chaque champ fautif, tiré de errors[] (plusieurs règles d'un même champ réunies). traceId : en-tête X-Trace-Id du back, à recopier dans un ticket. */
export class ApiError extends Error {
 public fields:Record<string,string>;
 constructor(message:string,public status:number,public problem:Problem|null=null,public traceId=''){
  super(message);
  this.fields={};
  for(const {field,message:m} of problem?.errors??[])this.fields[field]=this.fields[field]?`${this.fields[field]} ${m}`:m;
 }
}
export async function refreshCsrf(){const r=await fetch('/api/csrf',{credentials:'same-origin'});if(!r.ok)throw new ApiError('Impossible de sécuriser la session.',r.status);csrf=await r.json();}
export async function api<T>(path:string,init:RequestInit={}):Promise<T>{
 const method=(init.method||'GET').toUpperCase();const headers=new Headers(init.headers);
 if(!['GET','HEAD','OPTIONS'].includes(method)){if(!csrf)await refreshCsrf();headers.set(csrf!.headerName,csrf!.token);}
 if(init.body&&!(init.body instanceof URLSearchParams))headers.set('Content-Type','application/json');
 const response=await fetch('/api'+path,{...init,headers,credentials:'same-origin'});
 if(!response.ok){let message='Le service est momentanément indisponible.',problem:Problem|null=null;try{problem=await response.json();message=problem?.detail||problem?.title||message}catch{}
  // Panne côté serveur : la référence de trace s'affiche, le support la colle dans Jaeger (Lookup by Trace ID).
  const traceId=response.headers.get('X-Trace-Id')||'';if(traceId&&response.status>=500)message+=` (réf. ${traceId})`;
  throw new ApiError(message,response.status,problem,traceId);}
 if(response.status===204)return undefined as T;
 return response.json();
}

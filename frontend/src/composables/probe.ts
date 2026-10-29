export const PROBE_TIMEOUT_MS=3000;
/**
 * navigator.onLine dit « peut-être » ; la route de santé de Spring dit « oui ».
 * On exige "UP" : sans la règle nginx dédiée, /actuator/health renverrait l'index.html de Vue en 200.
 */
export async function probe():Promise<boolean>{
 try{
  const res=await fetch('/actuator/health',{cache:'no-store',signal:AbortSignal.timeout(PROBE_TIMEOUT_MS)});
  if(!res.ok)return false;
  const body=(await res.json()) as {status?:string};
  return body.status==='UP';
 }catch{return false} // réseau, délai dépassé, ou HTML au lieu de JSON
}

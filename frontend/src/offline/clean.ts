import {db} from './db';import {keyFor,DRAFT_TTL_MS} from '../stores/bookingDraft';
/** Une demande en attente ou un brouillon ne survivent pas plus de 24 h sur l'appareil. */
export async function purgeExpired(){
 const d=await db();const now=Date.now();
 for(const it of await d.getAll('outbox'))if(now-it.queuedAt>DRAFT_TTL_MS)await d.delete('outbox',it.idemKey);
 for(const dr of await d.getAll('drafts'))if(now-dr.savedAt>DRAFT_TTL_MS)await d.delete('drafts',dr.key);
}
/** Déconnexion volontaire (appareil partagé) : brouillon et file de ce patient effacés. Une session expirée ne l'appelle pas. */
export async function clearOfflineData(userId:number){
 const tx=(await db()).transaction(['outbox','drafts'],'readwrite');
 const items=await tx.objectStore('outbox').getAll();
 await Promise.all([
  ...items.filter(i=>i.userId===userId).map(i=>tx.objectStore('outbox').delete(i.idemKey)),
  tx.objectStore('drafts').delete(keyFor(userId)),
  tx.done,
 ]);
}
export async function hasPending(userId:number){return (await (await db()).getAll('outbox')).some(i=>i.userId===userId)}

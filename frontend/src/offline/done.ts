import {db} from './db';import {keyFor,useBookingDraft} from '../stores/bookingDraft';
import type {OutboxItem} from './types';import type {Appointment} from '../types';
/** Réponse 201 : l'entrée et son brouillon sont effacés ensemble, après la réponse et jamais avant. */
export async function done(it:OutboxItem,a:Appointment){
 const store=useBookingDraft();
 const mine=store.draft?.idemKey===it.idemKey;
 if(mine)store.forget();                          // aussi l'écriture différée en cours
 const tx=(await db()).transaction(['outbox','drafts'],'readwrite');
 const stored=await tx.objectStore('drafts').get(keyFor(it.userId));
 await Promise.all([
  tx.objectStore('outbox').delete(it.idemKey),
  stored?.idemKey===it.idemKey?tx.objectStore('drafts').delete(keyFor(it.userId)):Promise.resolve(),
  tx.done,
 ]);
 store.confirmed=a;                               // « Rendez-vous confirmé »
}

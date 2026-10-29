import {api,ApiError} from '../api';import {db} from './db';
import {useBookingDraft,loadDraft,fromStored} from '../stores/bookingDraft';import {fromServer,nearest} from './bookingRules';
import type {OutboxItem,BookingDraft} from './types';import type {Slot} from '../types';
/** Refus du serveur : la saisie est gardée, l'ancienne clé oubliée, et un créneau pris devient une proposition. */
export async function refused(it:OutboxItem,e:unknown){
 if(!(e instanceof ApiError)||e.status>=500)return;   // panne serveur : on réessaiera
 const store=useBookingDraft();
 if(e.status===401){store.needsLogin=true;return}     // session expirée : reconnexion, tout est gardé
 await (await db()).delete('outbox',it.idemKey);
 const stored=store.draft?.idemKey===it.idemKey?null:await loadDraft(it.userId);
 const before:BookingDraft|null=store.draft?.idemKey===it.idemKey?store.draft:stored?fromStored(stored):null;
 if(!before||before.idemKey!==it.idemKey)return;
 const d:BookingDraft={...before,status:'rejected',idemKey:null,problem:e.problem?.code??'INVALID'};
 store.draft=d;store.open=true;store.errors=fromServer(e.problem?.errors);
 if(d.problem!=='SLOT_TAKEN'){store.alternatives=[];return}
 try{store.alternatives=nearest(await api<Slot[]>(`/professionals/${d.professionalId}/slots`),d.slotId,d.startsAt,3)}
 catch{store.alternatives=[]}
}

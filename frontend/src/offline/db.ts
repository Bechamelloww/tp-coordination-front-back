import {openDB,type DBSchema,type IDBPDatabase} from 'idb';
import type {StoredDraft,OutboxItem} from './types';
// IndexedDB garde les données de l'utilisateur ; elle ne dépend pas du réseau.
interface MediSlotDB extends DBSchema {
 drafts:{key:string;value:StoredDraft};   // clé : booking:<userId>
 outbox:{key:string;value:OutboxItem};    // clé : idemKey
}
let opening:Promise<IDBPDatabase<MediSlotDB>>|null=null;
export function db(){
 opening??=openDB<MediSlotDB>('medislot',1,{upgrade(d){
  d.createObjectStore('drafts',{keyPath:'key'});
  d.createObjectStore('outbox',{keyPath:'idemKey'});
 }});
 return opening;
}

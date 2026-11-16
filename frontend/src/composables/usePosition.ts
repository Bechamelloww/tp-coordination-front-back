import {readonly,ref} from 'vue';
import type {Point} from '../geo';

export type EtatPosition='inconnue'|'recherche'|'connue'|'refusee'|'indisponible';

// État partagé par toute l'application, en mémoire seulement : ni localStorage, ni envoi au serveur.
// Recharger la page oublie la position.
const position=ref<Point|null>(null);
const etat=ref<EtatPosition>('inconnue');
const message=ref('');

/** Position de la personne, demandée seulement sur un geste explicite (bouton « Me localiser »). */
export function usePosition(){
 function localiser():Promise<Point|null>{
  if(!('geolocation' in navigator)){etat.value='indisponible';message.value='Votre navigateur ne permet pas la localisation.';return Promise.resolve(null);}
  etat.value='recherche';message.value='';
  return new Promise(resolve=>navigator.geolocation.getCurrentPosition(
   p=>{position.value={latitude:p.coords.latitude,longitude:p.coords.longitude};etat.value='connue';resolve(position.value);},
   e=>{
    position.value=null;
    if(e.code===e.PERMISSION_DENIED){etat.value='refusee';message.value='Localisation refusée. Vous pouvez l’autoriser dans les réglages du navigateur.';}
    else{etat.value='indisponible';message.value='Votre position n’a pas pu être déterminée. Réessayez.';}
    resolve(null);
   },
   {enableHighAccuracy:false,timeout:10_000,maximumAge:300_000},
  ));
 }
 function oublier(){position.value=null;etat.value='inconnue';message.value='';}
 return {position:readonly(position),etat:readonly(etat),message:readonly(message),localiser,oublier};
}

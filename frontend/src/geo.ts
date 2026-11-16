// « Autour de moi » : distance à vol d'oiseau et praticiens dans un rayon, calculés dans le navigateur.
// Confidentialité : la position de la personne n'est jamais envoyée à MediSlot ni gardée (ni stockage, ni journal).
// Seules les tuiles de la carte viennent d'OpenStreetMap ; leur adresse situe la zone affichée.
import type {Professional} from './types';

export interface Point {latitude:number;longitude:number}

/** Rayon de la recherche « Autour de moi », en kilomètres. */
export const RAYON_KM=5;
/** Adresses à ne jamais tracer (OpenTelemetry) : elles situent la personne (tuiles OpenStreetMap, géocodage IGN). */
export const URLS_GEO_NON_TRACEES=[/^https:\/\/([a-z0-9-]+\.)*openstreetmap\.(org|de)\//,/^https:\/\/data\.geopf\.fr\//];

const RAYON_TERRE_KM=6371.0088;
const rad=(deg:number)=>deg*Math.PI/180;

/** Distance à vol d'oiseau (formule de haversine), en kilomètres. */
export function distanceKm(a:Point,b:Point):number{
 const dLat=rad(b.latitude-a.latitude),dLon=rad(b.longitude-a.longitude);
 const h=Math.sin(dLat/2)**2+Math.cos(rad(a.latitude))*Math.cos(rad(b.latitude))*Math.sin(dLon/2)**2;
 return 2*RAYON_TERRE_KM*Math.asin(Math.min(1,Math.sqrt(h)));
}

export interface Proche {professional:Professional;distanceKm:number}
/**
 * Praticiens dont le cabinet est à moins de rayonKm (bord compris), du plus proche au plus loin.
 * Ceux sans position sont écartés ; à distance égale, l'ordre du nom puis de l'identifiant rend le tri stable.
 */
export function autourDe(centre:Point,praticiens:Professional[],rayonKm=RAYON_KM):Proche[]{
 return praticiens
  .filter(p=>p.latitude!=null&&p.longitude!=null)
  .map(p=>({professional:p,distanceKm:distanceKm(centre,{latitude:p.latitude!,longitude:p.longitude!})}))
  .filter(r=>r.distanceKm<=rayonKm)
  .sort((a,b)=>a.distanceKm-b.distanceKm||a.professional.name.localeCompare(b.professional.name,'fr')||a.professional.id-b.professional.id);
}

const nombre=(n:number,decimales:number)=>new Intl.NumberFormat('fr-FR',{maximumFractionDigits:decimales}).format(n);
/** « 850 m », « 3,2 km », « 465 km ». */
export function formatDistance(km:number):string{
 if(km<1)return `${nombre(Math.max(10,Math.round(km*100)*10),0)} m`;
 return `${nombre(km<10?Math.round(km*10)/10:Math.round(km),1)} km`;
}

/** Le cabinet seul, sur openstreetmap.org (aucune position de la personne dans l'adresse). */
export function lienCabinet(p:Point):string{
 return `https://www.openstreetmap.org/?mlat=${p.latitude}&mlon=${p.longitude}#map=17/${p.latitude}/${p.longitude}`;
}

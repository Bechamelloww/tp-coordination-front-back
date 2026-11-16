<script setup lang="ts">
// « Autour de moi » sur une carte OpenStreetMap : la position de la personne, le cercle du rayon de recherche
// et un marqueur par praticien trouvé. La carte double la liste, elle ne la remplace pas : tout ce qu'elle montre
// (nom, distance, ordre) se lit aussi dans la liste, au clavier comme au lecteur d'écran.
// Chargé à la demande (defineAsyncComponent) : Leaflet ne pèse sur la page qu'à la première recherche autour de soi.
import {onBeforeUnmount,onMounted,ref,watch} from 'vue';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import {formatDistance,type Point,type Proche} from '../geo';
import type {Professional} from '../types';

const props=defineProps<{centre:Point;rayonKm:number;proches:Proche[]}>();
const emit=defineEmits<{profile:[professional:Professional]}>();

// Objets Leaflet hors du système réactif de Vue (ils ne doivent pas être enveloppés dans des proxys).
const conteneur=ref<HTMLElement|null>(null);
let carte:L.Map|null=null,calque:L.LayerGroup|null=null,observateur:ResizeObserver|null=null;

const ll=(p:Point):L.LatLngTuple=>[p.latitude,p.longitude];
function texte(contenu:string):HTMLElement{const span=document.createElement('span');span.textContent=contenu;return span;}
/** Le cercle entier dans le cadre : la zone cherchée se voit, même sans aucun praticien dedans. */
function cadrer(){
 if(!carte)return;
 carte.invalidateSize();
 carte.fitBounds(L.latLng(ll(props.centre)).toBounds(props.rayonKm*2000),{padding:[16,16]});
}
function dessiner(){
 if(!carte)return;
 calque?.remove();
 calque=L.layerGroup().addTo(carte);
 L.circle(ll(props.centre),{radius:props.rayonKm*1000,color:'#0873b9',weight:2,fillColor:'#0873b9',fillOpacity:.06,interactive:false}).addTo(calque);
 for(const {professional:p,distanceKm} of props.proches){
  L.circleMarker([p.latitude!,p.longitude!],{radius:9,color:'#ffffff',weight:3,fillColor:'#0873b9',fillOpacity:1})
   .bindTooltip(texte(`${p.name} · ${formatDistance(distanceKm)}`),{direction:'top',offset:[0,-8]})
   .on('click',()=>emit('profile',p))
   .addTo(calque);
 }
 L.circleMarker(ll(props.centre),{radius:8,color:'#ffffff',weight:3,fillColor:'#34b8b1',fillOpacity:1})
  .bindTooltip('Vous',{permanent:true,direction:'right',offset:[10,0]}).addTo(calque);
 cadrer();
}

onMounted(()=>{
 if(!conteneur.value)return;
 // zoomSnap fractionnaire : le cercle remplit la hauteur de la carte au lieu de s'arrêter au zoom entier inférieur.
 carte=L.map(conteneur.value,{scrollWheelZoom:false,zoomSnap:.25});
 L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors'}).addTo(carte);
 dessiner();
 // La largeur du conteneur dépend de l'écran : Leaflet remesure et recadre à chaque changement (sinon zone grise).
 observateur=new ResizeObserver(cadrer);
 observateur.observe(conteneur.value);
});
watch(()=>[props.centre,props.rayonKm,props.proches],dessiner);
onBeforeUnmount(()=>{observateur?.disconnect();carte?.remove();carte=null;calque=null;});
</script>

<template>
 <figure class="nearby-map">
  <div ref="conteneur" class="nearby-map-canvas" role="region" :aria-label="`Carte OpenStreetMap : ${proches.length} praticien${proches.length>1?'s':''} à moins de ${rayonKm} km de votre position`"></div>
  <figcaption class="hint">Cercle : {{rayonKm}} km à vol d’oiseau autour de vous. Touchez un point pour ouvrir la fiche ; la liste ci-dessous reprend les mêmes praticiens, du plus proche au plus loin.</figcaption>
 </figure>
</template>

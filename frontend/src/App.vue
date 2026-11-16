<script setup lang="ts">
import {ref,computed,onMounted,onBeforeUnmount,watch,nextTick,defineAsyncComponent} from 'vue';import {usePosition} from './composables/usePosition';import {distanceKm,autourDe,formatDistance,lienCabinet,RAYON_KM,type Proche} from './geo';import ProfessionalCard from './components/ProfessionalCard.vue';import BookingForm from './components/BookingForm.vue';import ConvocationButton from './components/ConvocationButton.vue';import GoogleCalendarCard from './components/GoogleCalendarCard.vue';import {readGoogleReturn,realBrowser as googleBrowser} from './googleCalendar';import {googleLoginEnabled,startGoogleLogin,readLoginReturn} from './googleLogin';import NetworkBanner from './components/NetworkBanner.vue';import {api,ApiError} from './api';import {useSession} from './stores/session';import {useLive} from './composables/useLive';import {useExperiment} from './stores/experiment';import {useBookingDraft,loadDraft,discardDraft} from './stores/bookingDraft';import {flush} from './offline/flush';import {upcomingByPro} from './offline/bookingRules';import {clearOfflineData} from './offline/clean';import type {StoredDraft} from './offline/types';import {specialties,dateLabel,timeLabel,type Professional,type Slot,type Page,type Appointment,type Notice} from './types';
import {useClinic} from './stores/clinic';import ClinicSwitcher from './components/ClinicSwitcher.vue';import {withClinic} from './tenant';
// Carte « Autour de moi » chargée à la demande (Leaflet n'alourdit pas la page d'accueil).
const NearbyMap=defineAsyncComponent(()=>import('./components/NearbyMap.vue'));
const localisation=usePosition();
// autour : la liste montre les praticiens à moins de RAYON_KM de la personne, du plus proche au plus loin.
// La position ne quitte pas le navigateur : MediSlot renvoie l'annuaire filtré (nom, spécialité), le rayon se calcule ici.
const autour=ref(false),proches=ref<Proche[]>([]);
/** Distance à vol d'oiseau depuis la position de la personne, calculée dans le navigateur ; null si inconnue. */
function distanceDe(p:Professional):number|null{const pos=localisation.position.value;return pos&&p.latitude!=null&&p.longitude!=null?distanceKm(pos,{latitude:p.latitude,longitude:p.longitude}):null}
const session=useSession(),clinic=useClinic(),live=useLive(),personalLive=useLive(),ab=useExperiment(),booking=useBookingDraft();
const view=ref<'search'|'appointments'>('search'),loading=ref(false),busy=ref(false),error=ref(''),success=ref('');
const q=ref(''),city=ref(''),specialty=ref(''),page=ref(0),total=ref(0),doctors=ref<Professional[]>([]),slots=ref<Record<number,Slot[]>>({});
const appointments=ref<Appointment[]>([]),notices=ref<Notice[]>([]),showNotices=ref(false),profile=ref<Professional|null>(null);
const googleLogin=ref(false),googleBusy=ref(false);
const authOpen=ref(false),register=ref(false),email=ref(''),password=ref(''),name=ref(''),authError=ref(''),fieldErrors=ref<Record<string,string>>({});
// chosen : confirmation d'un déplacement (PATCH). Une nouvelle réservation passe par le formulaire et la file d'envoi.
const chosen=ref<{professional:Professional;slot:Slot}|null>(null),moveId=ref<number|null>(null),history=ref<{action:string;createdAt:string}[]|null>(null),cancelId=ref<number|null>(null);
const pendingChoice=ref<{professional:Professional;slot:Slot}|null>(null),offer=ref<StoredDraft|null>(null);

// RGAA 8.6 et 12.7 : chaque vue a son titre d'onglet ; en changeant de vue, le focus va sur son h1 (tabindex="-1").
const viewTitles={search:'Trouver un praticien',appointments:'Mes rendez-vous'} as const;
function focusHeading(){document.querySelector<HTMLElement>('main h1')?.focus()}

// RGAA 7.1, 7.3, 12.8 : une seule source dit quelle fenêtre est ouverte (null = aucune), dans l'ordre du template.
const modal=ref<HTMLElement|null>(null);let priorFocus:HTMLElement|null=null;
const modalKind=computed(()=>authOpen.value?'auth':booking.open&&booking.draft?'booking':chosen.value?'move':profile.value?'profile':history.value?'history':cancelId.value!==null?'cancel':null);
watch(modalKind,(kind,before)=>{
 if(kind&&!before)priorFocus=document.activeElement as HTMLElement|null;   // mémorisé à l'ouverture seulement
 void nextTick(()=>{
  if(kind){modal.value?.querySelector<HTMLElement>('#modal-title')?.focus();return}   // ouverture ou changement de contenu : le titre
  if(priorFocus&&priorFocus!==document.body&&priorFocus.isConnected)priorFocus.focus();else focusHeading();
  priorFocus=null;
 });
});
watch(view,v=>{document.title=`${viewTitles[v]} · MediSlot`},{immediate:true});
watch(view,()=>void nextTick(()=>{if(!modalKind.value)focusHeading()}));
// Quitter la recherche abandonne un déplacement en cours : sinon le créneau suivant, chez n'importe quel praticien, déplacerait l'ancien rendez-vous au lieu d'en réserver un.
watch(view,v=>{if(v!=='search'&&moveId.value){moveId.value=null;chosen.value=null;success.value='';}});

function closeModal(){if(busy.value)return;if(authOpen.value){authOpen.value=false;pendingChoice.value=null;return}if(booking.open){void booking.close();return}chosen.value=null;profile.value=null;history.value=null;cancelId.value=null;}
// Piège du focus : summary compris, champs masqués ou indisponibles exclus. Écouté sur le document : Échap marche même si le focus a glissé.
const FOCUSABLE='a[href],button,input,select,textarea,summary,[tabindex]:not([tabindex="-1"])';
function focusables(){return [...(modal.value?.querySelectorAll<HTMLElement>(FOCUSABLE)??[])].filter(e=>!e.matches(':disabled')&&e.getClientRects().length>0)}
function trap(e:KeyboardEvent){
 if(!modalKind.value||!modal.value)return;
 if(e.key==='Escape'){e.preventDefault();closeModal();return}
 if(e.key!=='Tab')return;
 const items=focusables();if(!items.length)return;
 const first=items[0],last=items[items.length-1],current=document.activeElement as HTMLElement|null;
 const inside=!!current&&items.includes(current);
 if(e.shiftKey&&(current===first||!inside)){e.preventDefault();last.focus()}
 else if(!e.shiftKey&&(current===last||!inside)){e.preventDefault();first.focus()}
}

let searchGeneration=0;
async function search(reset=false){if(reset)page.value=0;const generation=++searchGeneration;loading.value=true;error.value='';try{
 const centre=autour.value?localisation.position.value:null;
 if(centre){const r=autourDe(centre,await annuaireComplet());if(generation!==searchGeneration)return;proches.value=r;doctors.value=r.map(x=>x.professional);total.value=r.length;}
 else{const result=await api<Page<Professional>>('/professionals?'+new URLSearchParams({q:q.value,city:city.value,specialty:specialty.value,page:String(page.value),size:'6'}));if(generation!==searchGeneration)return;doctors.value=result.items;total.value=result.total;}
 await loadSlots();}catch(e){error.value=message(e)}finally{if(generation===searchGeneration)loading.value=false}}
/** Tout l'annuaire filtré par nom et spécialité (la ville cède la place à la position), par pages de 50. */
async function annuaireComplet(){const tous:Professional[]=[];for(let p=0;;p++){const r=await api<Page<Professional>>('/professionals?'+new URLSearchParams({q:q.value,specialty:specialty.value,page:String(p),size:'50'}));tous.push(...r.items);if(!r.items.length||tous.length>=r.total)return tous;}}
/** « Autour de moi » : la position n'est demandée qu'ici, sur ce clic. Un refus laisse la liste telle quelle. */
async function autourDeMoi(){if(!await localisation.localiser())return;autour.value=true;city.value='';await search(true);}
function quitterAutour(){autour.value=false;proches.value=[];void search(true);}
function oublierPosition(){localisation.oublier();if(autour.value)quitterAutour();}
async function loadSlots(){const entries=await Promise.all(doctors.value.map(async p=>[p.id,await api<Slot[]>(`/professionals/${p.id}/slots`)] as const));slots.value=Object.fromEntries(entries);}
function message(e:unknown){return e instanceof TypeError?'Connexion impossible. Vérifiez le réseau puis réessayez.':e instanceof Error?e.message:'Une erreur est survenue.'}
/**
 * Règle « un rendez-vous à venir par praticien » (contrôlée par le serveur : 409 ALREADY_BOOKED) : le plus proche par praticien.
 * Pendant un déplacement, le rendez-vous déplacé ne bloque pas son propre praticien.
 */
const upcomingByProfessional=computed(()=>upcomingByPro(appointments.value,Date.now(),moveId.value));
async function refreshAppointments(){if(!session.user)return;appointments.value=await api<Appointment[]>('/appointments');}
async function refreshNotices(){if(session.user)notices.value=await api<Notice[]>('/notifications');}
// Temps 1 : choisir un créneau ouvre le formulaire (après connexion si besoin).
function choose(professional:Professional,slot:Slot){success.value='';error.value='';profile.value=null;
 if(moveId.value){chosen.value={professional,slot};return}
 if(!session.user){pendingChoice.value={professional,slot};openAuth();return}
 startBooking(professional,slot);
}
function startBooking(professional:Professional,slot:Slot){
 if(!session.user)return;
 if(booking.draft?.status==='pending'){error.value='Une demande est déjà en attente d’envoi. Elle partira dès le retour du réseau.';return}
 offer.value=null;booking.start(session.user.id,professional,slot);
}
// RGAA 11.10 : erreur par champ (errors[] du ProblemDetail), focus sur le premier champ invalide.
const authFields=['name','email','password'] as const;
function describedBy(...ids:(string|false)[]){return ids.filter(Boolean).join(' ')||undefined}
async function authenticate(){if(busy.value)return;busy.value=true;authError.value='';fieldErrors.value={};try{if(register.value)await api('/register',{method:'POST',body:JSON.stringify({name:name.value,email:email.value,password:password.value})});await session.login(email.value,password.value);if(session.user)await ab.load(session.user.id);authOpen.value=false;password.value='';const c=pendingChoice.value;pendingChoice.value=null;if(c)startBooking(c.professional,c.slot);await refreshAppointments();}
 catch(e){authError.value=message(e);if(e instanceof ApiError)fieldErrors.value=e.fields;await nextTick();const first=authFields.find(f=>fieldErrors.value[f]);if(first)document.getElementById(`auth-${first}`)?.focus();}
 finally{busy.value=false}}
async function confirm(){if(busy.value||!chosen.value||!moveId.value)return;busy.value=true;error.value='';try{await api(`/appointments/${moveId.value}`,{method:'PATCH',body:JSON.stringify({slotId:chosen.value.slot.id})});success.value='Votre rendez-vous a été déplacé.';chosen.value=null;moveId.value=null;await refreshAppointments();await refreshNotices();await loadSlots();view.value='appointments';}catch(e){error.value=message(e);if(e instanceof ApiError&&e.status===409){chosen.value=null;await loadSlots();}}finally{busy.value=false}}
async function cancel(){if(busy.value||cancelId.value===null)return;busy.value=true;try{await api(`/appointments/${cancelId.value}/cancel`,{method:'POST'});cancelId.value=null;success.value='Votre rendez-vous a été annulé.';await refreshAppointments();await loadSlots();await refreshNotices();}catch(e){error.value=message(e)}finally{busy.value=false}}
async function move(a:Appointment){moveId.value=a.id;autour.value=false;q.value=a.professionalName;city.value='';specialty.value='';view.value='search';await search(true);success.value='Choisissez un nouveau créneau. Votre réservation actuelle reste conservée jusqu’à confirmation.';}
async function showHistory(id:number){try{history.value=await api(`/appointments/${id}/history`)}catch(e){error.value=message(e)}}
// Déconnexion volontaire : brouillon et file effacés de l'appareil (une session expirée, elle, garde tout).
async function logout(){const id=session.user?.id;try{await session.logout();if(id!==undefined)await clearOfflineData(id).catch(()=>{});booking.forget();offer.value=null;ab.clear();personalLive.close();notices.value=[];appointments.value=[];showNotices.value=false;view.value='search';moveId.value=null;}catch(e){error.value=message(e)}}
async function loginWithGoogle(){if(googleBusy.value)return;googleBusy.value=true;authError.value='';try{await startGoogleLogin(googleBrowser)}catch(e){authError.value=message(e);googleBusy.value=false}}
function openAuth(){authOpen.value=true;authError.value='';fieldErrors.value={};register.value=false;}
function toggleRegister(){register.value=!register.value;authError.value='';fieldErrors.value={};}
function myAppointments(){if(!session.user){openAuth();return;}view.value='appointments';refreshAppointments().catch(e=>error.value=message(e));}
// Bonus : un brouillon relu au démarrage est proposé (« Reprendre » ou « Recommencer ») ; une demande en attente repart seule.
function resume(){if(offer.value)booking.resume(offer.value);offer.value=null}
async function restart(){if(offer.value)await discardDraft(offer.value.userId).catch(()=>{});offer.value=null}
function connectPersonal(){personalLive.connect(withClinic('/api/notifications/events'),()=>{refreshNotices().catch(()=>{});refreshAppointments().catch(()=>{})},'notifications-changed')}
// Changement de cabinet : l'écran repart de zéro, les flux temps réel sont rouverts sur le nouveau cabinet.
async function onClinicChanged(){
 moveId.value=null;chosen.value=null;profile.value=null;history.value=null;cancelId.value=null;success.value='';error.value='';
 appointments.value=[];notices.value=[];slots.value={};
 live.connect(withClinic('/api/events'),()=>loadSlots().catch(()=>{}));
 if(session.user){connectPersonal();refreshNotices().catch(()=>{});refreshAppointments().catch(()=>{})}
 await search(true);
}
watch(()=>session.user,async user=>{personalLive.close();offer.value=null;
 if(!user){ab.reset();booking.forget();clinic.reset();return}
 clinic.loadMine().catch(()=>{});
 ab.load(user.id);refreshNotices().catch(()=>{});refreshAppointments().catch(()=>{});connectPersonal();
 const d=await loadDraft(user.id).catch(()=>null);
 if(!d||booking.draft||session.user?.id!==user.id)return;
 if(d.status==='pending'){booking.restorePending(d);void flush()}else offer.value=d;
});
// Temps 6 : issue de l'envoi. Le message va dans la région status toujours présente ; le focus va sur « Mes rendez-vous ».
watch(()=>booking.confirmed,a=>{if(!a)return;error.value='';success.value=`Votre rendez-vous est confirmé : ${a.professionalName}, ${dateLabel(a.startsAt)} à ${timeLabel(a.startsAt)}.`;view.value='appointments';refreshAppointments().catch(()=>{});refreshNotices().catch(()=>{});loadSlots().catch(()=>{});});
// Session expirée pendant la coupure : reconnexion demandée, la demande attend dans IndexedDB.
watch(()=>booking.needsLogin,async needed=>{if(!needed)return;success.value='';error.value='Votre session a expiré. Reconnectez-vous : votre demande en attente partira ensuite.';await session.load().catch(()=>{});if(!session.user)openAuth();});
onMounted(async()=>{document.addEventListener('keydown',trap);
 const googleReturn=readGoogleReturn(googleBrowser),loginReturn=readLoginReturn(googleBrowser);
 googleLoginEnabled().then(v=>googleLogin.value=v);
 try{await clinic.init()}catch(e){error.value=message(e);return}
 await search();try{await session.load()}catch(e){error.value=message(e)}
 if(loginReturn&&loginReturn.outcome==='google'&&session.user)success.value=loginReturn.message;else if(loginReturn&&loginReturn.outcome!=='google'){openAuth();authError.value=loginReturn.message;}
 if(googleReturn){success.value=googleReturn.message;if(session.user){view.value='appointments';refreshAppointments().catch(e=>error.value=message(e));}}
 live.connect(withClinic('/api/events'),()=>loadSlots().catch(()=>{}));});
onBeforeUnmount(()=>document.removeEventListener('keydown',trap));
</script>
<template>
 <a href="#main" class="skip">Aller au contenu</a>
 <header class="header"><button class="brand" @click="view='search';moveId=null">MediSlot<span class="brand-dot">.</span></button><ClinicSwitcher @switched="onClinicChanged"/><nav aria-label="Navigation principale"><button :aria-current="view==='search'?'page':undefined" @click="view='search'">Trouver un praticien</button><button :aria-current="view==='appointments'?'page':undefined" @click="myAppointments">Mes rendez-vous</button><button v-if="session.user" @click="showNotices=!showNotices" :aria-expanded="showNotices">Notifications <span class="count">{{notices.length}}</span></button><button v-if="!session.user" class="login" @click="openAuth">Se connecter</button><button v-else class="login" @click="logout">{{session.user.name.split(' ')[0]}} · Déconnexion</button></nav></header>
 <!-- Une seule région principale : elle commence au bandeau de recherche et à son h1 (cible du lien d'évitement). -->
 <main id="main" tabindex="-1">
 <NetworkBanner/>
 <section v-if="view==='search'" class="hero"><div class="hero-inner"><p class="eyebrow">VOTRE PROCHAIN RENDEZ-VOUS</p><h1 tabindex="-1">Trouvez le bon praticien.<br>Prenez un moment pour vous.</h1><p class="hero-copy">Un professionnel près de chez vous, au créneau qui vous convient.</p><form class="searchbar" @submit.prevent="search(true)"><label><span>Nom du praticien</span><input v-model="q" placeholder="Nom, praticien…" autocomplete="off"></label><label><span>Spécialité</span><select v-model="specialty"><option value="">Toutes les spécialités</option><option v-for="(label,key) in specialties" :key="key" :value="key">{{label}}</option></select></label><label><span>Ville</span><input v-model="city" placeholder="Paris, Lyon…" autocomplete="address-level2"></label><button type="submit" :aria-disabled="loading">{{loading?'Recherche…':'Rechercher'}} <span aria-hidden="true">→</span></button></form><div class="hero-bottom"><span>Des créneaux visibles en quelques clics</span><span class="live" :class="{offline:!live.connected.value}"><i></i>{{live.connected.value?'Disponibilités en direct':'Reconnexion du direct…'}}</span></div></div></section>
 <div class="main">
  <div v-if="showNotices&&session.user" class="notices"><div class="section-title"><h2>Vos notifications</h2><button class="text-button" @click="showNotices=false">Fermer</button></div><p v-if="!notices.length">Aucune notification pour le moment.</p><p v-for="n in notices" :key="n.id">{{n.message}} <small>{{dateLabel(n.createdAt)}}</small></p></div>
  <div v-if="offer" class="move-banner" role="status"><span><strong>Vos réponses ont été conservées</strong> · {{offer.professionalName}} · {{dateLabel(offer.startsAt)}} à {{timeLabel(offer.startsAt)}}. Enregistrées sur cet appareil à {{timeLabel(new Date(offer.savedAt).toISOString())}}, effacées après 24 h.</span><span><button class="text-button" @click="resume">Reprendre</button> · <button class="text-button" @click="restart">Recommencer</button></span></div>
  <div v-if="booking.draft?.status==='pending'&&!booking.open" class="move-banner" role="status"><span>En attente d'envoi : {{booking.draft.professionalName}}, {{dateLabel(booking.draft.startsAt)}} à {{timeLabel(booking.draft.startsAt)}}. Le créneau n'est pas encore réservé.</span><button class="text-button" @click="booking.open=true">Voir la demande</button></div>
  <!-- RGAA 7.5 : les régions existent toujours, seul le paragraphe à l'intérieur apparaît. -->
  <div role="alert"><p v-if="error" class="alert error">{{error}} <button class="text-button" @click="search()">Réessayer</button></p></div>
  <div role="status"><p v-if="success" class="alert success">{{success}}</p></div>
  <template v-if="view==='search'"><div class="section-title"><div><p class="eyebrow dark">VOTRE RECHERCHE</p><h2>{{total}} praticien{{total>1?'s':''}} {{autour?`à moins de ${RAYON_KM} km`:'à découvrir'}}</h2></div><div class="locate"><button v-if="!autour" type="button" class="secondary nearby-button" :aria-disabled="localisation.etat.value==='recherche'" @click="localisation.etat.value!=='recherche'&&autourDeMoi()"><span aria-hidden="true">⌖</span> {{localisation.etat.value==='recherche'?'Localisation…':`Autour de moi (${RAYON_KM} km)`}}</button><p v-if="autour" class="subtle-small">Du plus proche au plus loin, à vol d’oiseau · <button type="button" class="text-button" @click="quitterAutour()">Tous les praticiens</button></p><p v-if="localisation.etat.value==='connue'" class="subtle-small">Votre position reste dans ce navigateur · <button type="button" class="text-button" @click="oublierPosition()">Oublier ma position</button></p><p v-if="localisation.message.value" class="error-text" role="alert">{{localisation.message.value}}</p></div></div>
   <div v-if="moveId" class="move-banner">Déplacement du rendez-vous n° {{moveId}} <button class="text-button" @click="moveId=null;success=''">Abandonner le déplacement</button></div>
   <div role="status"><p v-if="loading" class="loading">Nous recherchons vos prochains créneaux…</p></div>
   <NearbyMap v-if="autour&&localisation.position.value" :centre="localisation.position.value" :rayon-km="RAYON_KM" :proches="proches" @profile="profile=$event"/>
   <template v-if="!loading"><div v-if="!doctors.length" class="empty"><h3>{{autour?`Aucun praticien à moins de ${RAYON_KM} km`:'Aucun praticien ne correspond à cette recherche'}}</h3><p>{{autour?'Retirez un filtre ou affichez tous les praticiens.':'Essayez une autre ville ou une spécialité différente.'}}</p><button class="primary" @click="q='';city='';specialty='';autour=false;search(true)">Voir tous les praticiens</button></div>
   <div v-else class="results"><div class="cards"><ProfessionalCard v-for="p in doctors" :key="p.id" :professional="p" :slots="slots[p.id]||[]" :distance="distanceDe(p)" :booked="upcomingByProfessional[p.id]??null" @choose="choose" @profile="profile=$event" @mine="myAppointments"/></div><aside class="help"><span class="help-icon" aria-hidden="true">+</span><h2>Votre rendez-vous,<br>en toute simplicité.</h2><ol><li>Choisissez votre praticien.</li><li>Sélectionnez un créneau.</li><li>Retrouvez la confirmation dans votre espace.</li></ol><hr><p>Un changement de programme ? Vous pouvez déplacer ou annuler votre rendez-vous depuis votre espace.</p><p class="demo-note">Démonstration pédagogique.<br>Praticiens et données fictifs.</p></aside></div></template>
   <div v-if="!autour&&total>6" class="pagination"><button :disabled="page===0" @click="page--;search()">Précédent</button><span>Page {{page+1}}</span><button :disabled="(page+1)*6>=total" @click="page++;search()">Suivant</button></div>
  </template>
  <template v-else><div class="section-title"><div><p class="eyebrow dark">VOTRE ESPACE</p><h1 tabindex="-1">Mes rendez-vous</h1></div><button class="primary" @click="view='search'">Prendre rendez-vous</button></div><GoogleCalendarCard/><p v-if="!appointments.length" class="empty">Vous n’avez pas encore de rendez-vous.</p><article v-for="a in appointments" :key="a.id" class="appointment"><div class="appointment-date"><strong>{{dateLabel(a.startsAt)}}</strong><span>{{timeLabel(a.startsAt)}}</span></div><div class="appointment-person"><h2>{{a.professionalName}}</h2><p>{{specialties[a.specialty]}} · {{a.city}}</p><span class="status" :class="{cancelled:a.status==='CANCELLED'}">{{a.status==='CONFIRMED'?'Confirmé':'Annulé'}}</span></div><div class="appointment-actions"><button v-if="a.status==='CONFIRMED'" @click="move(a)">Déplacer <span class="sr-only">le rendez-vous du {{dateLabel(a.startsAt)}} à {{timeLabel(a.startsAt)}} avec {{a.professionalName}}</span></button><button v-if="a.status==='CONFIRMED'" class="danger-link" @click="cancelId=a.id">Annuler <span class="sr-only">le rendez-vous du {{dateLabel(a.startsAt)}} à {{timeLabel(a.startsAt)}} avec {{a.professionalName}}</span></button><ConvocationButton v-if="a.status==='CONFIRMED'" :appointment-id="a.id" :context="`du rendez-vous du ${dateLabel(a.startsAt)} à ${timeLabel(a.startsAt)} avec ${a.professionalName}`" @outdated="refreshAppointments"/><p v-else class="convocation-off">Plus de convocation : rendez-vous annulé.</p><button class="text-button" @click="showHistory(a.id)">Historique <span class="sr-only">du rendez-vous du {{dateLabel(a.startsAt)}} à {{timeLabel(a.startsAt)}} avec {{a.professionalName}}</span></button></div></article></template>
 </div>
 </main><footer><span class="brand footer-brand">MediSlot.</span><p>Votre parcours de soins, simplement.</p><small>Application de formation · Données fictives · Horaires Europe/Paris</small></footer>
 <div v-if="modalKind" class="overlay" @click.self="closeModal"><section ref="modal" class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title"><button class="close" aria-label="Fermer" @click="closeModal">×</button>
  <template v-if="modalKind==='auth'"><p class="eyebrow dark">VOTRE ESPACE MEDISLOT</p><h2 id="modal-title" tabindex="-1">{{register?'Créer votre compte':'Heureux de vous retrouver'}}</h2><p class="subtle">Connectez-vous pour confirmer et retrouver vos rendez-vous.</p>
   <template v-if="googleLogin"><button type="button" class="google-button" :aria-disabled="googleBusy" @click="loginWithGoogle"><svg aria-hidden="true" viewBox="0 0 48 48" width="20" height="20"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/><path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/></svg>{{googleBusy?'Redirection vers Google…':'Continuer avec Google'}}</button><p class="auth-separator"><span>ou avec votre e-mail</span></p></template>
   <form class="auth-form" novalidate @submit.prevent="authenticate">
    <p class="form-note">Tous les champs sont obligatoires.</p>
    <div v-if="register" class="field"><label for="auth-name">Votre nom</label><input id="auth-name" v-model="name" required maxlength="120" autocomplete="name" :aria-invalid="!!fieldErrors.name" :aria-describedby="describedBy(!!fieldErrors.name&&'auth-name-error')"><p id="auth-name-error" class="error-text"><template v-if="fieldErrors.name">Erreur : {{fieldErrors.name}}</template></p></div>
    <div class="field"><label for="auth-email">Adresse e-mail</label><input id="auth-email" v-model="email" type="email" required autocomplete="email" :aria-invalid="!!fieldErrors.email" :aria-describedby="describedBy(!!fieldErrors.email&&'auth-email-error')"><p id="auth-email-error" class="error-text"><template v-if="fieldErrors.email">Erreur : {{fieldErrors.email}}</template></p></div>
    <div class="field"><label for="auth-password">Mot de passe</label><p v-if="register" id="auth-password-hint" class="hint">12 caractères au moins.</p><input id="auth-password" v-model="password" type="password" required maxlength="72" :autocomplete="register?'new-password':'current-password'" :aria-invalid="!!fieldErrors.password" :aria-describedby="describedBy(!!fieldErrors.password&&'auth-password-error',register&&'auth-password-hint')"><p id="auth-password-error" class="error-text"><template v-if="fieldErrors.password">Erreur : {{fieldErrors.password}}</template></p></div>
    <p role="alert" class="error-text"><template v-if="authError">{{authError}}</template></p>
    <button type="submit" class="primary" :aria-disabled="busy">{{busy?'Connexion…':register?'Créer mon compte':'Se connecter'}}</button>
   </form>
   <button class="text-button" @click="toggleRegister">{{register?'J’ai déjà un compte':'Créer un compte'}}</button></template>
  <template v-else-if="modalKind==='booking'"><BookingForm/></template>
  <template v-else-if="modalKind==='move'&&chosen"><p class="eyebrow dark">DERNIÈRE ÉTAPE</p><h2 id="modal-title" tabindex="-1">Confirmer le déplacement</h2><div class="booking-summary"><h3>{{chosen.professional.name}}</h3><p>{{specialties[chosen.professional.specialty]}}</p><strong>{{dateLabel(chosen.slot.startsAt)}} à {{timeLabel(chosen.slot.startsAt)}}</strong><p>{{chosen.professional.address}}, {{chosen.professional.city}}</p></div><p class="subtle">Le créneau sera réservé après confirmation du serveur. Une autre personne peut encore le sélectionner entre-temps.</p><p class="error-text" role="alert"><template v-if="error">{{error}}</template></p><button class="primary wide" :aria-disabled="busy" @click="confirm">{{busy?'Confirmation…':'Confirmer le déplacement'}}</button></template>
  <template v-else-if="modalKind==='profile'&&profile"><p class="eyebrow dark">VOTRE PRATICIEN</p><h2 id="modal-title" tabindex="-1">{{profile.name}}</h2><p class="specialty">{{specialties[profile.specialty]}}</p><p>{{profile.bio}}</p><h3>Le cabinet</h3><p>{{profile.address}}, {{profile.city}}<template v-if="distanceDe(profile)!=null"> · à {{formatDistance(distanceDe(profile)!)}} de vous</template></p><p v-if="profile.latitude!=null&&profile.longitude!=null" class="carte-lien"><a :href="lienCabinet({latitude:profile.latitude,longitude:profile.longitude})" target="_blank" rel="noopener">Voir le cabinet sur OpenStreetMap <span aria-hidden="true">↗</span><span class="sr-only">(nouvel onglet)</span></a></p><h3>Consultation</h3><p>{{profile.fee}} € · 30 minutes · Au cabinet</p><button class="primary wide" @click="profile=null">Voir les disponibilités</button></template>
  <template v-else-if="modalKind==='history'"><h2 id="modal-title" tabindex="-1">Historique du rendez-vous</h2><ol class="timeline"><li v-for="(h,i) in history" :key="i"><strong>{{({CREATED:'Réservation confirmée',RESCHEDULED:'Rendez-vous déplacé',CANCELLED:'Rendez-vous annulé'} as Record<string,string>)[h.action]||h.action}}</strong><p>{{dateLabel(h.createdAt)}} à {{timeLabel(h.createdAt)}}</p></li></ol></template>
  <template v-else-if="modalKind==='cancel'"><h2 id="modal-title" tabindex="-1">Annuler ce rendez-vous ?</h2><p>Le créneau sera remis à disposition. Vous pourrez prendre un autre rendez-vous quand vous le souhaitez.</p><button class="primary wide" :aria-disabled="busy" @click="cancel">Confirmer l’annulation</button><button class="text-button" @click="cancelId=null">Conserver mon rendez-vous</button></template>
 </section></div>
</template>

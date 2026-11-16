<script setup lang="ts">
import {watch,nextTick} from 'vue';import {useBookingDraft} from '../stores/bookingDraft';import {useExperiment} from '../stores/experiment';
import {reasons} from '../offline/bookingRules';import {dateLabel,timeLabel} from '../types';
const booking=useBookingDraft(),ab=useExperiment();
const errorFor=(field:string)=>booking.errors.find(e=>e.field===field)?.message;
const errorId=(field:string)=>errorFor(field)?`booking-${field}-error`:undefined;
// Test A/B : le bouton « Valider » (A) ou « Réserver ce créneau » (B) est rendu → exposition, une fois par patient.
watch(()=>ab.ready,ready=>{if(ready)void ab.expose()},{immediate:true,flush:'post'});
// Le bouton garde le focus (aria-disabled) : le double envoi est bloqué ici et dans le store.
async function send(){
 if(!booking.draft||booking.draft.status==='pending'||!ab.ready)return;
 await booking.submit();
 if(!booking.errors.length)return;
 await nextTick();
 document.querySelector<HTMLElement>('.booking-form [aria-invalid="true"]')?.focus();   // RGAA 11.10 : premier champ invalide
}
</script>
<template>
 <template v-if="booking.draft">
  <p class="eyebrow dark">VOTRE DEMANDE</p>
  <h2 id="modal-title" tabindex="-1">Prendre rendez-vous</h2>
  <div class="booking-summary"><h3>{{booking.draft.professionalName}}</h3><strong>{{dateLabel(booking.draft.startsAt)}} à {{timeLabel(booking.draft.startsAt)}}</strong></div>
  <!-- RGAA 7.5 : la région d'alerte existe toujours ; seul son texte apparaît, il est donc lu à coup sûr. -->
  <div role="alert">
   <p v-if="booking.draft.status==='rejected'&&booking.draft.problem==='SLOT_TAKEN'" class="error-text">Ce créneau n’est plus disponible. Choisissez-en un autre. Vos réponses sont conservées.</p>
   <p v-else-if="booking.draft.status==='rejected'&&booking.draft.problem==='ALREADY_BOOKED'" class="error-text">Vous avez déjà un rendez-vous à venir avec {{booking.draft.professionalName}}. Pour changer d’horaire, déplacez-le depuis « Mes rendez-vous ».</p>
   <p v-else-if="booking.draft.status==='rejected'" class="error-text">Votre demande a été refusée. Vérifiez vos réponses puis validez à nouveau.</p>
  </div>
  <template v-if="booking.draft.status==='rejected'&&booking.draft.problem==='SLOT_TAKEN'">
   <div v-if="booking.alternatives.length" class="alternatives"><p class="subtle">Autres créneaux avec {{booking.draft.professionalName}} :</p><button v-for="s in booking.alternatives" :key="s.id" type="button" class="slot" @click="booking.chooseAgain(s)">{{dateLabel(s.startsAt)}} · {{timeLabel(s.startsAt)}}</button></div>
   <p v-else class="subtle">Aucun autre créneau libre chez ce praticien dans les 14 prochains jours.</p>
  </template>
  <form class="auth-form booking-form" novalidate @submit.prevent="send">
   <fieldset :disabled="booking.draft.status==='pending'">
    <label>Motif du rendez-vous<select v-model="booking.draft.reasonCode" :aria-invalid="!!errorFor('reasonCode')" :aria-describedby="errorId('reasonCode')"><option :value="null" disabled>Choisissez un motif</option><option v-for="(label,code) in reasons" :key="code" :value="code">{{label}}</option></select></label>
    <p v-if="errorFor('reasonCode')" id="booking-reasonCode-error" class="error-text">Erreur : {{errorFor('reasonCode')}}</p>
    <fieldset class="choices" :aria-describedby="errorId('forWhom')"><legend>Pour qui ?</legend>
     <label><input v-model="booking.draft.forWhom" type="radio" value="SELF" :aria-invalid="!!errorFor('forWhom')"> Pour moi</label>
     <label><input v-model="booking.draft.forWhom" type="radio" value="RELATIVE" :aria-invalid="!!errorFor('forWhom')"> Pour un proche</label>
    </fieldset>
    <p v-if="errorFor('forWhom')" id="booking-forWhom-error" class="error-text">Erreur : {{errorFor('forWhom')}}</p>
    <label v-if="booking.draft.forWhom==='RELATIVE'">Nom du proche<input v-model="booking.draft.relativeName" maxlength="120" autocomplete="off" :aria-invalid="!!errorFor('relativeName')" :aria-describedby="errorId('relativeName')"></label>
    <p v-if="errorFor('relativeName')" id="booking-relativeName-error" class="error-text">Erreur : {{errorFor('relativeName')}}</p>
    <label>Téléphone (facultatif)<input v-model="booking.draft.phone" type="tel" inputmode="tel" autocomplete="tel" placeholder="06 12 34 56 78" :aria-invalid="!!errorFor('phone')" :aria-describedby="errorId('phone')"></label>
    <p v-if="errorFor('phone')" id="booking-phone-error" class="error-text">Erreur : {{errorFor('phone')}}</p>
   </fieldset>
   <div role="status"><p v-if="booking.draft.status==='pending'" class="pending">En attente d'envoi : le créneau n'est pas encore réservé.</p></div>
   <div role="alert">
    <p v-if="booking.needsLogin" class="error-text">Votre session a expiré. Reconnectez-vous : votre demande attend.</p>
    <p v-if="!booking.storageOk" class="error-text">Vos réponses ne peuvent pas être enregistrées sur cet appareil (navigation privée ou espace plein).</p>
   </div>
   <button class="primary wide" type="submit" :aria-disabled="booking.draft.status==='pending'||!ab.ready">{{ab.confirmLabel}}</button>
  </form>
 </template>
</template>

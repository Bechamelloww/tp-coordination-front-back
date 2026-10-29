<script setup lang="ts">
import {watch,onBeforeUnmount} from 'vue';import {useNetworkStatus} from '../composables/useNetworkStatus';import {flush} from '../offline/flush';
const RETRY_MS=15_000;
const {state,check}=useNetworkStatus();
let retry:ReturnType<typeof setTimeout>|undefined;
watch(state,(s,before)=>{
 clearTimeout(retry);
 // Serveur muet : nouvel essai de la sonde toutes les 15 s.
 if(s==='unreachable')retry=setTimeout(()=>void check(),RETRY_MS);
 // MediSlot répond de nouveau (sans forcément d'événement « online ») : on vide la file.
 if(s==='online'&&before!=='online')void flush();
});
onBeforeUnmount(()=>clearTimeout(retry));
</script>
<template>
 <!-- Toujours présent : un lecteur d'écran annonce chaque changement. Ne bloque jamais le formulaire. -->
 <div class="net" role="status" aria-live="polite">
  <p v-if="state==='offline'" class="net-offline">Hors ligne, vos réponses sont gardées</p>
  <p v-else-if="state==='unreachable'" class="net-down">MediSlot ne répond pas. Nouvel essai dans 15 s. <button class="text-button" @click="check">Réessayer</button></p>
 </div>
</template>

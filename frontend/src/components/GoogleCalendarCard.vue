<script setup lang="ts">
import {ref,computed,onMounted} from 'vue';
import {fetchGoogleStatus,connectGoogle,disconnectGoogle,googleErrorMessage,realBrowser,type Browser} from '../googleCalendar';
import type {GoogleStatus} from '../types';
const props=withDefaults(defineProps<{browser?:Browser}>(),{browser:()=>realBrowser});
type PendingAction='connect'|'disconnect';
const googleStatus=ref<GoogleStatus|null>(null);
const pendingAction=ref<PendingAction|null>(null);
const errorText=ref('');
const statusLabels={NON_CONNECTE:'Non connecté',CONNECTE:'Connecté',A_RECONNECTER:'À reconnecter'} as const;
const isLinked=computed(()=>googleStatus.value?.status!=='NON_CONNECTE');
async function refreshGoogleStatus(){
 googleStatus.value=await fetchGoogleStatus();
}
onMounted(()=>refreshGoogleStatus().catch(()=>{googleStatus.value=null}));
async function startConnection(){
 if(pendingAction.value)return;
 pendingAction.value='connect';
 errorText.value='';
 try{
  await connectGoogle(props.browser);
 }catch(error){
  errorText.value=googleErrorMessage(error);
  pendingAction.value=null;
 }
}
async function removeConnection(){
 if(pendingAction.value)return;
 pendingAction.value='disconnect';
 errorText.value='';
 try{
  await disconnectGoogle();
  await refreshGoogleStatus();
 }catch(error){
  errorText.value=googleErrorMessage(error);
 }finally{
  pendingAction.value=null;
 }
}
</script>
<template>
 <section v-if="googleStatus?.enabled" class="google-card" aria-labelledby="google-card-title">
  <div class="google-card-text">
   <h2 id="google-card-title">Google Agenda</h2>
   <p>
    <span class="status" :class="{cancelled:googleStatus.status!=='CONNECTE'}">{{statusLabels[googleStatus.status]}}</span>
    <template v-if="googleStatus.status==='A_RECONNECTER'"> L’accès a expiré ou a été retiré : reconnectez Google pour reprendre la synchronisation.</template>
   </p>
   <p class="google-card-note">Vos rendez-vous sont copiés dans un agenda « MediSlot » créé pour l’occasion : titre « Rendez-vous MediSlot », nom du praticien, adresse du cabinet et référence du rendez-vous. La spécialité n’est jamais transmise à Google.</p>
  </div>
  <div class="google-card-actions">
   <button v-if="googleStatus.status!=='CONNECTE'" type="button" class="primary" :aria-disabled="pendingAction!==null" :aria-busy="pendingAction==='connect'" @click="startConnection">
    {{pendingAction==='connect'?'Redirection…':googleStatus.status==='A_RECONNECTER'?'Reconnecter':'Connecter'}}<span class="sr-only"> mon agenda Google</span>
   </button>
   <button v-if="isLinked" type="button" class="secondary" :aria-disabled="pendingAction!==null" :aria-busy="pendingAction==='disconnect'" @click="removeConnection">
    {{pendingAction==='disconnect'?'Déconnexion…':'Déconnecter'}}<span class="sr-only"> mon agenda Google</span>
   </button>
  </div>
  <p v-if="errorText" class="google-card-error" role="alert">{{errorText}}</p>
 </section>
</template>
<style scoped>
.google-card{display:flex;flex-wrap:wrap;gap:16px 28px;align-items:center;background:white;border:1px solid var(--line);border-radius:12px;padding:22px 26px;margin-bottom:24px}
.google-card-text{flex:1 1 320px}
.google-card h2{font-size:19px;margin:0 0 10px}
.google-card p{margin:0 0 8px;font-size:14px}
.google-card-note{color:var(--muted);font-size:13px!important}
.google-card-actions{display:flex;flex-wrap:wrap;gap:8px}
.google-card-actions button[aria-busy=true]{cursor:progress}
.google-card-error{flex-basis:100%;margin:0!important;color:#9e2c1b;font-weight:600;font-size:13px}
</style>

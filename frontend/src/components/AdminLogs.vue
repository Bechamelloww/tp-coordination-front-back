<script setup lang="ts">
import {ref,watch} from 'vue';
import {actionLabel,fetchLogs,logDate,LOG_PAGE,type LogEntry,type LogScope} from '../adminLog';
// version : le tableau de bord l'incrémente après chaque action, pour relire le journal.
const props=defineProps<{master:boolean;clinicSlug?:string;version:number}>();
const scope=ref<LogScope>('clinic'),entries=ref<LogEntry[]>([]),more=ref(false),busy=ref(false),error=ref('');
async function load(append=false){
 if(busy.value)return;busy.value=true;error.value='';
 try{
  const page=await fetchLogs(scope.value,append?entries.value.at(-1)?.id:undefined);
  entries.value=append?[...entries.value,...page]:page;more.value=page.length===LOG_PAGE;
 }catch(e){error.value=e instanceof Error?e.message:'Journal indisponible.'}
 finally{busy.value=false}
}
watch(()=>[props.clinicSlug,props.version,scope.value],()=>void load(),{immediate:true});
</script>
<template>
 <section class="admin-block" aria-labelledby="admin-logs">
  <div class="admin-block-head">
   <h2 id="admin-logs">Journal d’activité</h2>
   <label v-if="master" class="admin-logs-scope"><span class="sr-only">Journal affiché</span><select v-model="scope"><option value="clinic">Ce cabinet</option><option value="all">Tous les cabinets</option></select></label>
  </div>
  <p class="hint admin-logs-note">Rendez-vous, comptes et membres du cabinet. Visible uniquement par ses admins et le master ; rien ne peut y être modifié ni supprimé.</p>
  <div role="alert"><p v-if="error" class="alert error">{{error}}</p></div>
  <p v-if="!entries.length&&!busy&&!error" class="empty-small">Aucune activité enregistrée pour l’instant.</p>
  <div v-else-if="entries.length" class="admin-table"><table>
   <thead><tr><th scope="col">Date</th><th v-if="scope==='all'" scope="col">Cabinet</th><th scope="col">Par</th><th scope="col">Action</th><th scope="col">Détail</th></tr></thead>
   <tbody><tr v-for="e in entries" :key="e.id">
    <td class="admin-logs-date">{{logDate(e.createdAt)}}</td>
    <td v-if="scope==='all'">{{e.clinicName}}</td>
    <td>{{e.actor}}</td><td>{{actionLabel(e.action)}}</td><td>{{e.detail}}</td>
   </tr></tbody>
  </table></div>
  <button v-if="more" class="text-button" :aria-disabled="busy" @click="load(true)">{{busy?'Chargement…':'Afficher l’activité plus ancienne'}}</button>
 </section>
</template>

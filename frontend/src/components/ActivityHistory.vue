<script setup lang="ts">
import {computed,ref,watch} from 'vue';
import {api} from '../api';
import {useClinic} from '../stores/clinic';
import {activityKind,activityLabels,activityMoment,activityPath,emptyFilters,OUTSIDE_CLINICS,selectableActions,type ActivityEntry,type ActivityPage} from '../activity';
const props=defineProps<{global:boolean;clinics:{slug:string;name:string}[]}>();
const clinic=useClinic();
const draft=ref(emptyFilters()),applied=ref(emptyFilters());
const entries=ref<ActivityEntry[]>([]),more=ref(false),loading=ref(false),error=ref('');
const actions=computed(()=>selectableActions(props.global));
const filtered=computed(()=>Object.values(applied.value).some(v=>v!==''));
const summary=computed(()=>loading.value?'Chargement de l’historique…':`${entries.value.length}${more.value?'+':''} action${entries.value.length>1?'s':''} affichée${entries.value.length>1?'s':''}${filtered.value?' avec ces filtres':''}.`);

async function load(append=false){
 if(loading.value)return;
 loading.value=true;error.value='';
 try{
  const before=append?entries.value.at(-1)?.id:undefined;
  const page=await api<ActivityPage>(activityPath(props.global,applied.value,before));
  entries.value=append?[...entries.value,...page.entries]:page.entries;
  more.value=page.more;
 }catch(e){error.value=e instanceof Error?e.message:'Chargement impossible.'}
 finally{loading.value=false}
}
function apply(){applied.value={...draft.value};void load()}
function reset(){draft.value=emptyFilters();apply()}
watch(()=>props.global?null:clinic.current?.slug,()=>void load(),{immediate:true});
defineExpose({reload:()=>load()});
</script>
<template>
 <section class="admin-block" aria-labelledby="activity-title">
  <h2 id="activity-title">Historique des actions</h2>
  <p class="hint activity-scope">{{global?'Toutes les actions de tous les cabinets, ainsi que les connexions et les liaisons Google Agenda.':`Les actions effectuées dans ${clinic.current?.name??'ce cabinet'}.`}}</p>
  <form class="admin-form activity-filters" role="search" aria-label="Filtrer l’historique" novalidate @submit.prevent="apply">
   <div class="activity-filter-grid">
    <div v-if="global" class="field"><label for="activity-clinic">Cabinet</label><select id="activity-clinic" v-model="draft.clinic"><option value="">Tous les cabinets</option><option :value="OUTSIDE_CLINICS">Hors cabinet (connexions, Google Agenda)</option><option v-for="c in clinics" :key="c.slug" :value="c.slug">{{c.name}}</option></select></div>
    <div class="field"><label for="activity-action">Type d’action</label><select id="activity-action" v-model="draft.action"><option value="">Toutes les actions</option><option v-for="a in actions" :key="a" :value="a">{{activityLabels[a]}}</option></select></div>
    <div class="field"><label for="activity-from">Du</label><input id="activity-from" v-model="draft.from" type="date" :max="draft.to||undefined"></div>
    <div class="field"><label for="activity-to">Au</label><input id="activity-to" v-model="draft.to" type="date" :min="draft.from||undefined"></div>
    <div class="field activity-text"><label for="activity-text">Rechercher</label><input id="activity-text" v-model="draft.text" type="search" maxlength="100" autocomplete="off" placeholder="Nom, e-mail, praticien…"></div>
   </div>
   <div class="admin-form-actions"><button type="submit" class="primary" :aria-disabled="loading">Filtrer</button><button type="button" class="text-button" @click="reset">Réinitialiser</button></div>
  </form>
  <div role="alert"><p v-if="error" class="alert error">{{error}}</p></div>
  <p role="status" class="activity-summary">{{summary}}</p>
  <p v-if="!loading&&!error&&!entries.length" class="empty-small">Aucune action ne correspond à ces filtres.</p>
  <div v-else-if="entries.length" class="admin-table" :aria-busy="loading"><table>
   <caption class="sr-only">Historique des actions, de la plus récente à la plus ancienne</caption>
   <thead><tr><th scope="col">Date</th><th scope="col">Action</th><th scope="col">Détail</th><th scope="col">Auteur</th><th v-if="global" scope="col">Cabinet</th></tr></thead>
   <tbody><tr v-for="e in entries" :key="e.id">
    <td class="activity-date">{{activityMoment(e.createdAt)}}</td>
    <td><span class="activity-tag" :data-kind="activityKind(e.action)">{{activityLabels[e.action]??e.action}}</span></td>
    <td>{{e.detail||'—'}}</td>
    <td>{{e.actorName??'Compte supprimé'}}<small v-if="e.actorEmail" class="activity-email">{{e.actorEmail}}</small></td>
    <td v-if="global">{{e.clinicName??'Hors cabinet'}}</td>
   </tr></tbody>
  </table></div>
  <button v-if="more" type="button" class="secondary activity-more" :aria-disabled="loading" @click="load(true)">{{loading?'Chargement…':'Afficher plus'}}</button>
 </section>
</template>

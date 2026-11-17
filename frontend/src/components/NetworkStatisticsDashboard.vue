<script setup lang="ts">
import {computed,nextTick,onMounted,ref} from 'vue';
import {api} from '../api';
import {safeColor} from '../tenant';
import StatisticsIndicators from './StatisticsIndicators.vue';
import ClinicStatisticsPanel from './ClinicStatisticsPanel.vue';
import {count,percent,ratio,type ClinicStatistics,type NetworkStatistics} from '../statistics';
const network=ref<NetworkStatistics|null>(null),detail=ref<ClinicStatistics|null>(null),selected=ref('');
const loading=ref(false),detailLoading=ref(false),error=ref(''),detailError=ref('');
const busiest=computed(()=>Math.max(1,...(network.value?.clinicSummaries??[]).map(c=>c.summary.total)));
async function load(){
 loading.value=true;error.value='';
 try{
  network.value=await api<NetworkStatistics>('/master/statistics');
  const slugs=network.value.clinicSummaries.map(c=>c.slug);
  await select(slugs.includes(selected.value)?selected.value:slugs[0]??'');
 }catch(e){error.value=e instanceof Error?e.message:'Chargement impossible.'}
 finally{loading.value=false}
}
async function select(slug:string){
 selected.value=slug;detailError.value='';
 if(!slug){detail.value=null;return}
 detailLoading.value=true;
 try{detail.value=await api<ClinicStatistics>(`/master/statistics/${encodeURIComponent(slug)}`)}
 catch(e){detailError.value=e instanceof Error?e.message:'Chargement impossible.'}
 finally{detailLoading.value=false}
}
async function showDetail(slug:string){
 await select(slug);
 await nextTick();
 document.getElementById('stats-clinic-detail')?.focus();
}
onMounted(()=>void load());
</script>
<template>
 <div class="admin">
  <div class="section-title"><div><p class="eyebrow dark">STATISTIQUES GLOBALES</p><h1 tabindex="-1">Tous les cabinets</h1></div><button class="secondary" :aria-disabled="loading" @click="!loading&&load()">Actualiser</button></div>
  <div role="alert"><p v-if="error" class="alert error">{{error}}</p></div>
  <template v-if="network">
   <p class="stats-scope">{{count(network.clinics)}} cabinet{{network.clinics>1?'s':''}} · {{count(network.professionals)}} praticien{{network.professionals>1?'s':''}}</p>
   <StatisticsIndicators :summary="network.summary"/>
   <section class="admin-block" aria-labelledby="stats-clinics">
    <h2 id="stats-clinics">Réservations par cabinet</h2>
    <div class="admin-table"><table>
     <thead><tr><th scope="col">Cabinet</th><th scope="col" class="stats-bar-column">Réservations</th><th scope="col" class="numeric">Praticiens</th><th scope="col" class="numeric">À venir</th><th scope="col" class="numeric">Annulation</th><th scope="col" class="numeric">Remplissage</th><th scope="col"><span class="sr-only">Détail</span></th></tr></thead>
     <tbody>
      <tr v-for="c in network.clinicSummaries" :key="c.slug" :class="{selected:c.slug===selected}" :style="{'--option':safeColor(c.color)}">
       <th scope="row"><span class="stats-clinic-dot" aria-hidden="true"></span>{{c.name}} <small>{{c.city}}</small></th>
       <td class="stats-bar-column"><span class="stats-bar"><span class="stats-bar-track"><span class="stats-bar-fill" :style="{width:`${c.summary.total/busiest*100}%`}"></span></span><strong>{{count(c.summary.total)}}</strong></span></td>
       <td class="numeric">{{count(c.professionals)}}</td>
       <td class="numeric">{{count(c.summary.upcoming)}}</td>
       <td class="numeric">{{percent(ratio(c.summary.cancelled,c.summary.total))}}</td>
       <td class="numeric">{{percent(ratio(c.summary.upcoming,c.summary.upcomingSlots))}}</td>
       <td><button class="text-button" :aria-label="`Voir le détail de ${c.name}`" :aria-current="c.slug===selected?'true':undefined" @click="showDetail(c.slug)">Voir le détail</button></td>
      </tr>
     </tbody>
    </table></div>
   </section>
   <section v-if="network.clinicSummaries.length" class="admin-block stats-detail" aria-labelledby="stats-clinic-detail">
    <div class="admin-block-head">
     <h2 id="stats-clinic-detail" tabindex="-1">Détail : {{detail?.name??'…'}}</h2>
     <div class="stats-select"><label for="stats-clinic-select">Cabinet</label><select id="stats-clinic-select" :value="selected" @change="select(($event.target as HTMLSelectElement).value)"><option v-for="c in network.clinicSummaries" :key="c.slug" :value="c.slug">{{c.name}}</option></select></div>
    </div>
    <div role="alert"><p v-if="detailError" class="alert error">{{detailError}}</p></div>
    <p v-if="detailLoading&&!detail" class="empty-small">Chargement…</p>
    <ClinicStatisticsPanel v-if="detail" :statistics="detail" :aria-busy="detailLoading"/>
   </section>
  </template>
 </div>
</template>

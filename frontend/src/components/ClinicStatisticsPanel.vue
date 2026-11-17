<script setup lang="ts">
import {computed} from 'vue';
import StatisticsIndicators from './StatisticsIndicators.vue';
import {count,percent,ratio,type ClinicStatistics} from '../statistics';
import {specialties} from '../types';
const props=defineProps<{statistics:ClinicStatistics}>();
const busiest=computed(()=>Math.max(1,...props.statistics.professionals.map(p=>p.total)));
</script>
<template>
 <div class="stats-panel">
  <StatisticsIndicators :summary="statistics.summary"/>
  <section class="admin-block" :aria-labelledby="`stats-professionals-${statistics.slug}`">
   <h2 :id="`stats-professionals-${statistics.slug}`">Réservations par praticien</h2>
   <p v-if="!statistics.professionals.length" class="empty-small">Aucun praticien dans ce cabinet.</p>
   <div v-else class="admin-table"><table>
    <thead><tr><th scope="col">Praticien</th><th scope="col" class="stats-bar-column">Réservations</th><th scope="col" class="numeric">Confirmées</th><th scope="col" class="numeric">Annulées</th><th scope="col" class="numeric">À venir</th><th scope="col" class="numeric">Annulation</th></tr></thead>
    <tbody>
     <tr v-for="p in statistics.professionals" :key="p.id">
      <th scope="row">{{p.name}} <small>{{specialties[p.specialty]??p.specialty}}</small></th>
      <td class="stats-bar-column"><span class="stats-bar"><span class="stats-bar-track"><span class="stats-bar-fill" :style="{width:`${p.total/busiest*100}%`}"></span></span><strong>{{count(p.total)}}</strong></span></td>
      <td class="numeric">{{count(p.confirmed)}}</td>
      <td class="numeric">{{count(p.cancelled)}}</td>
      <td class="numeric">{{count(p.upcoming)}}</td>
      <td class="numeric">{{percent(ratio(p.cancelled,p.total))}}</td>
     </tr>
    </tbody>
   </table></div>
  </section>
 </div>
</template>

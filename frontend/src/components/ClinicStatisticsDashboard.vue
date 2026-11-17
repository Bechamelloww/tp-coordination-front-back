<script setup lang="ts">
import {ref,watch} from 'vue';
import {api} from '../api';
import {useClinic} from '../stores/clinic';
import ClinicStatisticsPanel from './ClinicStatisticsPanel.vue';
import type {ClinicStatistics} from '../statistics';
const clinic=useClinic();
const statistics=ref<ClinicStatistics|null>(null),loading=ref(false),error=ref('');
async function load(){
 loading.value=true;error.value='';
 try{statistics.value=await api<ClinicStatistics>('/admin/statistics')}
 catch(e){error.value=e instanceof Error?e.message:'Chargement impossible.'}
 finally{loading.value=false}
}
watch(()=>clinic.current?.slug,()=>void load(),{immediate:true});
</script>
<template>
 <div class="admin">
  <div class="section-title"><div><p class="eyebrow dark">STATISTIQUES DU CABINET</p><h1 tabindex="-1">{{clinic.current?.name}}</h1></div><button class="secondary" :aria-disabled="loading" @click="!loading&&load()">Actualiser</button></div>
  <div role="alert"><p v-if="error" class="alert error">{{error}}</p></div>
  <ClinicStatisticsPanel v-if="statistics" :statistics="statistics"/>
 </div>
</template>

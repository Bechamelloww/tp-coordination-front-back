<script setup lang="ts">
import {useClinic} from '../stores/clinic';
const clinic=useClinic();
const emit=defineEmits<{switched:[slug:string]}>();
function change(e:Event){const slug=(e.target as HTMLSelectElement).value;if(clinic.switchTo(slug))emit('switched',slug)}
</script>
<template>
 <div v-if="clinic.current" class="clinic-switcher">
  <span class="clinic-dot" aria-hidden="true"></span>
  <label v-if="clinic.mine.length>1" class="sr-only" for="clinic-select">Cabinet</label>
  <select v-if="clinic.mine.length>1" id="clinic-select" :value="clinic.current.slug" @change="change">
   <option v-for="c in clinic.mine" :key="c.slug" :value="c.slug">{{c.name}}</option>
  </select>
  <span v-else class="clinic-name">{{clinic.current.name}}</span>
 </div>
</template>

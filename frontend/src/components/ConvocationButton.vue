<script setup lang="ts">
import {ref} from 'vue';
import {openConvocation,saveConvocation,ConvocationError,FALLBACK,realBrowser,type Browser} from '../convocation';
const props=withDefaults(defineProps<{appointmentId:number;context?:string;browser?:Browser}>(),{context:'',browser:()=>realBrowser});
/** 404 ou 409 : la liste affichée n'est plus à jour (annulé ailleurs, autre onglet). */
const emit=defineEmits<{outdated:[]}>();
type Action='open'|'save';
const state=ref<'idle'|'loading'|'error'>('idle'),action=ref<Action|null>(null),error=ref('');
async function run(kind:Action){
 if(state.value==='loading')return; // aria-disabled garde le focus mais ne bloque pas le clic
 state.value='loading';action.value=kind;error.value='';
 try{
  // openConvocation ouvre l'onglet tout de suite, pendant le clic : aucune attente avant cet appel.
  await (kind==='open'?openConvocation(props.appointmentId,props.browser):saveConvocation(props.appointmentId,props.browser));
  state.value='idle';
 }catch(e){
  state.value='error';
  error.value=e instanceof ConvocationError?e.message:FALLBACK;
  if(e instanceof ConvocationError&&(e.status===404||e.status===409))emit('outdated');
 }finally{action.value=null}
}
</script>
<template>
 <button type="button" :aria-disabled="state==='loading'" :aria-busy="action==='open'" @click="run('open')">
  {{action==='open'?'Préparation…':'Ma convocation (PDF)'}}<span class="sr-only"> {{context}}</span>
 </button>
 <button type="button" :aria-disabled="state==='loading'" :aria-busy="action==='save'" @click="run('save')">
  {{action==='save'?'Préparation…':'Enregistrer'}}<span class="sr-only"> la convocation {{context}}</span>
 </button>
 <p v-if="state==='error'" class="convocation-error" role="alert">{{error}}</p>
</template>

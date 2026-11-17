<script setup lang="ts">
import {ref,computed,watch,onMounted,onBeforeUnmount} from 'vue';
import {dateLabel,timeLabel,type Notice} from '../types';
const props=defineProps<{notices:Notice[]}>();
const emit=defineEmits<{read:[ids:number[]]}>();
const open=ref(false),nouvelles=ref<number[]>([]),menu=ref<HTMLElement|null>(null),bouton=ref<HTMLButtonElement|null>(null);
const nonLues=computed(()=>props.notices.filter(n=>!n.readAt).length);
const cloche='M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0';
const icones={confirmed:'M20 6 9 17l-5-5',moved:'M8 2v4M16 2v4M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM3 10h18M10 16h6M14 14l2 2-2 2',cancelled:'M18 6 6 18M6 6l12 12'};
const genre=(message:string)=>message.includes('annulé')?'cancelled':message.includes('déplacé')?'moved':'confirmed';
watch(()=>open.value&&props.notices,liste=>{
 const ids=liste?liste.filter(n=>!n.readAt).map(n=>n.id):[];
 if(!ids.length)return;
 nouvelles.value.push(...ids);emit('read',ids);
});
watch(open,o=>{if(!o)nouvelles.value=[]});
function fermer(e:Event){
 if(!open.value)return;
 if(e instanceof KeyboardEvent){if(e.key!=='Escape')return;open.value=false;bouton.value?.focus();return}
 if(!menu.value?.contains(e.target as Node))open.value=false;
}
onMounted(()=>{document.addEventListener('pointerdown',fermer);document.addEventListener('keydown',fermer)});
onBeforeUnmount(()=>{document.removeEventListener('pointerdown',fermer);document.removeEventListener('keydown',fermer)});
</script>
<template>
 <div ref="menu" class="notices-menu">
  <button ref="bouton" type="button" class="notices-button" :aria-expanded="open" aria-controls="notices-panel" @click="open=!open"><svg class="bell" aria-hidden="true" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path :d="cloche"/></svg>Notifications<span v-if="nonLues" class="count unread">{{nonLues}}<span class="sr-only"> non lue{{nonLues>1?'s':''}}</span></span></button>
  <div v-show="open" id="notices-panel" class="notices-panel" role="region" aria-labelledby="notices-title">
   <div class="notices-panel-head"><h2 id="notices-title">Notifications</h2><span v-if="nouvelles.length" class="notices-panel-new">{{nouvelles.length}} nouvelle{{nouvelles.length>1?'s':''}}</span></div>
   <p v-if="!notices.length" class="notices-empty"><svg aria-hidden="true" viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path :d="cloche"/></svg>Aucune notification pour le moment.</p>
   <ul v-else class="notice-list">
    <li v-for="n in notices" :key="n.id" class="notice-item" :class="[genre(n.message),{fresh:nouvelles.includes(n.id)}]">
     <span class="notice-icon" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path :d="icones[genre(n.message)]"/></svg></span>
     <span class="notice-body"><span class="notice-message">{{n.message}}<span v-if="nouvelles.includes(n.id)" class="notice-dot"><span class="sr-only"> (nouvelle)</span></span></span><small>{{dateLabel(n.createdAt)}} à {{timeLabel(n.createdAt)}}</small></span>
    </li>
   </ul>
  </div>
 </div>
</template>

<script setup lang="ts">
import {ref,computed,watch,onMounted,onBeforeUnmount} from 'vue';
import {dateLabel,timeLabel,type Notice} from '../types';

const props=defineProps<{notices:Notice[]}>();
const emit=defineEmits<{read:[ids:number[]]}>();

const open=ref(false),freshIds=ref<number[]>([]);
const root=ref<HTMLElement|null>(null),toggleButton=ref<HTMLButtonElement|null>(null);
const unreadCount=computed(()=>props.notices.filter(n=>!n.readAt).length);

type NoticeKind='confirmed'|'moved'|'cancelled';
function kindOf(message:string):NoticeKind{
 if(message.includes('annulé'))return 'cancelled';
 if(message.includes('déplacé'))return 'moved';
 return 'confirmed';
}

watch(()=>open.value&&props.notices,visible=>{
 if(!visible)return;
 const unreadIds=visible.filter(n=>!n.readAt).map(n=>n.id);
 if(!unreadIds.length)return;
 freshIds.value=[...freshIds.value,...unreadIds];
 emit('read',unreadIds);
});
watch(open,isOpen=>{if(!isOpen)freshIds.value=[]});

function close(returnFocus=false){if(!open.value)return;open.value=false;if(returnFocus)toggleButton.value?.focus()}
function onPointerDown(e:PointerEvent){if(root.value&&!root.value.contains(e.target as Node))close()}
function onKeyDown(e:KeyboardEvent){if(e.key==='Escape'&&open.value){e.preventDefault();close(true)}}
onMounted(()=>{document.addEventListener('pointerdown',onPointerDown);document.addEventListener('keydown',onKeyDown)});
onBeforeUnmount(()=>{document.removeEventListener('pointerdown',onPointerDown);document.removeEventListener('keydown',onKeyDown)});
</script>

<template>
 <div ref="root" class="notices-menu">
  <button ref="toggleButton" type="button" class="notices-button" :aria-expanded="open" aria-controls="notices-panel" @click="open=!open">
   <svg class="bell" aria-hidden="true" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg>
   <span class="notices-label">Notifications</span>
   <span v-if="unreadCount" class="count unread">{{unreadCount}}<span class="sr-only"> non lue{{unreadCount>1?'s':''}}</span></span>
  </button>
  <div v-show="open" id="notices-panel" class="notices-panel" role="region" aria-labelledby="notices-panel-title">
   <div class="notices-panel-head">
    <h2 id="notices-panel-title">Notifications</h2>
    <span v-if="freshIds.length" class="notices-panel-new">{{freshIds.length}} nouvelle{{freshIds.length>1?'s':''}}</span>
   </div>
   <p v-if="!notices.length" class="notices-empty">
    <svg aria-hidden="true" viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg>
    Aucune notification pour le moment.
   </p>
   <ul v-else class="notice-list">
    <li v-for="n in notices" :key="n.id" class="notice-item" :class="[kindOf(n.message),{fresh:freshIds.includes(n.id)}]">
     <span class="notice-icon" aria-hidden="true">
      <svg v-if="kindOf(n.message)==='confirmed'" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
      <svg v-else-if="kindOf(n.message)==='moved'" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M8 2v4M16 2v4"/><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M3 10h18M10 16h6M14 14l2 2-2 2"/></svg>
      <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18M6 6l12 12"/></svg>
     </span>
     <span class="notice-body">
      <span class="notice-message">{{n.message}}<span v-if="freshIds.includes(n.id)" class="notice-dot"><span class="sr-only"> (nouvelle)</span></span></span>
      <small>{{dateLabel(n.createdAt)}} à {{timeLabel(n.createdAt)}}</small>
     </span>
    </li>
   </ul>
  </div>
 </div>
</template>

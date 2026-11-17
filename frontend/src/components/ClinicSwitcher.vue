<script setup lang="ts">
import {ref,computed,nextTick,onMounted,onBeforeUnmount} from 'vue';
import {useClinic,type Clinic} from '../stores/clinic';
import {safeColor} from '../tenant';
const clinic=useClinic();
const emit=defineEmits<{switched:[slug:string]}>();
const open=ref(false),root=ref<HTMLElement|null>(null),toggle=ref<HTMLButtonElement|null>(null);
const choices=computed(()=>clinic.mine.length>1);
const roleLabel=(c:Clinic)=>c.role==='ADMIN'?'Admin':null;
const initial=(c:Clinic)=>c.name.replace(/^(Cabinet|Maison de santé) (des |du |de la |de )?/i,'').charAt(0).toUpperCase();
function show(){open.value=true;void nextTick(()=>root.value?.querySelector<HTMLElement>('[aria-current=true]')?.focus())}
function close(focus=true){open.value=false;if(focus)toggle.value?.focus()}
function pick(slug:string){if(slug!==clinic.current?.slug&&clinic.switchTo(slug))emit('switched',slug);close()}
// Flèches haut et bas entre les cabinets, Échap ferme et rend le focus au bouton.
function keys(e:KeyboardEvent){
 if(e.key==='Escape'){e.preventDefault();close();return}
 if(e.key!=='ArrowDown'&&e.key!=='ArrowUp')return;
 e.preventDefault();
 const items=[...(root.value?.querySelectorAll<HTMLElement>('.clinic-option')??[])],i=items.indexOf(document.activeElement as HTMLElement);
 items[(i+(e.key==='ArrowDown'?1:-1)+items.length)%items.length]?.focus();
}
function outside(e:MouseEvent){if(open.value&&!root.value?.contains(e.target as Node))close(false)}
onMounted(()=>document.addEventListener('click',outside));
onBeforeUnmount(()=>document.removeEventListener('click',outside));
</script>
<template>
 <div v-if="clinic.current" ref="root" class="clinic-switcher" @keydown="keys">
  <button v-if="choices" ref="toggle" type="button" class="clinic-current" aria-haspopup="true" :aria-expanded="open" aria-controls="clinic-menu" @click="open?close(false):show()">
   <span class="clinic-badge" aria-hidden="true">{{initial(clinic.current)}}</span>
   <span class="clinic-text"><span class="sr-only">Cabinet : </span><strong>{{clinic.current.name}}</strong><small>{{clinic.current.city}}<template v-if="clinic.isAdmin"> · Admin</template></small></span>
   <span class="clinic-chevron" aria-hidden="true">▾</span>
  </button>
  <div v-else class="clinic-current static">
   <span class="clinic-badge" aria-hidden="true">{{initial(clinic.current)}}</span>
   <span class="clinic-text"><strong>{{clinic.current.name}}</strong><small>{{clinic.current.city}}<template v-if="clinic.isAdmin"> · Admin</template></small></span>
  </div>
  <div v-if="open" id="clinic-menu" class="clinic-menu">
   <p class="clinic-menu-title">Changer de cabinet</p>
   <ul>
    <li v-for="c in clinic.mine" :key="c.slug">
     <button type="button" class="clinic-option" :aria-current="c.slug===clinic.current.slug" :style="{'--option':safeColor(c.color)}" @click="pick(c.slug)">
      <span class="clinic-badge" aria-hidden="true">{{initial(c)}}</span>
      <span class="clinic-text"><strong>{{c.name}}</strong><small>{{c.city}}</small></span>
      <span v-if="roleLabel(c)" class="clinic-role">{{roleLabel(c)}}</span>
      <span v-if="c.slug===clinic.current.slug" class="clinic-check" aria-hidden="true">✓</span>
     </button>
    </li>
   </ul>
  </div>
 </div>
</template>

<script setup lang="ts">
import {ref,watch} from 'vue';
import {api,ApiError} from '../api';
import {useClinic} from '../stores/clinic';
import {useSession} from '../stores/session';
import {safeColor,slugify} from '../tenant';
import {specialties,dateLabel,timeLabel} from '../types';
interface Overview {professionals:number;upcoming:number;members:number;admins:number}
interface Member {id:number;name:string;email:string;role:'ADMIN'|'MEMBER'}
interface Booking {id:number;startsAt:string;professionalName:string;specialty:string;patientName:string;status:string}
interface ClinicStats {slug:string;name:string;city:string;address:string|null;color:string;professionals:number;upcoming:number;members:number;admins:string[]}
const emit=defineEmits<{open:[slug:string]}>();
const clinic=useClinic(),session=useSession();
const overview=ref<Overview|null>(null),members=ref<Member[]>([]),bookings=ref<Booking[]>([]),all=ref<ClinicStats[]>([]);
const loading=ref(false),error=ref('');
async function load(){
 loading.value=true;error.value='';
 try{
  [overview.value,members.value,bookings.value]=await Promise.all([api<Overview>('/admin/overview'),api<Member[]>('/admin/members'),api<Booking[]>('/admin/appointments')]);
  all.value=session.user?.role==='MASTER'?await api<ClinicStats[]>('/master/clinics'):[];
 }catch(e){error.value=e instanceof Error?e.message:'Chargement impossible.'}
 finally{loading.value=false}
}
watch(()=>clinic.current?.slug,()=>void load(),{immediate:true});

// Création d'un cabinet (master seulement). L'identifiant suit le nom tant qu'on ne l'a pas modifié à la main.
const blank=()=>({name:'',city:'',address:'',slug:'',color:'#0873b9',adminEmail:''});
const accounts=ref<{email:string;name:string}[]>([]);
const creating=ref(false),saving=ref(false),form=ref(blank()),slugTouched=ref(false),formError=ref(''),fieldErrors=ref<Record<string,string>>({}),created=ref('');
watch(()=>form.value.name,name=>{if(!slugTouched.value)form.value.slug=slugify(name)});
function openForm(){api<{email:string;name:string}[]>('/master/accounts').then(a=>accounts.value=a).catch(()=>{accounts.value=[]});creating.value=true;created.value='';form.value=blank();slugTouched.value=false;formError.value='';fieldErrors.value={}}
async function createClinic(){
 if(saving.value)return;saving.value=true;formError.value='';fieldErrors.value={};
 try{
  const body={...form.value,adminEmail:form.value.adminEmail||null};
  const c=await api<ClinicStats>('/master/clinics',{method:'POST',body:JSON.stringify(body)});
  all.value=[...all.value,c].sort((a,b)=>a.slug.localeCompare(b.slug));
  creating.value=false;created.value=`Le cabinet « ${c.name} » a été créé.`;
  await clinic.loadMine().catch(()=>{});
 }catch(e){formError.value=e instanceof Error?e.message:'Création impossible.';if(e instanceof ApiError)fieldErrors.value=e.fields}
 finally{saving.value=false}
}
</script>
<template>
 <div class="admin">
  <div class="section-title"><div><p class="eyebrow dark">{{session.user?.role==='MASTER'?'ADMINISTRATION GLOBALE':'ADMINISTRATION DU CABINET'}}</p><h1 tabindex="-1">{{clinic.current?.name}}</h1></div><button class="secondary" :aria-disabled="loading" @click="!loading&&load()">Actualiser</button></div>
  <div role="alert"><p v-if="error" class="alert error">{{error}}</p></div>
  <section v-if="all.length" class="admin-block" aria-labelledby="all-clinics">
   <div class="admin-block-head"><h2 id="all-clinics">Tous les cabinets</h2><button v-if="!creating" class="secondary" @click="openForm">Nouveau cabinet</button></div>
   <div role="status"><p v-if="created" class="alert success">{{created}}</p></div>
   <form v-if="creating" class="admin-form" novalidate @submit.prevent="createClinic">
    <h3>Nouveau cabinet</h3>
    <div class="admin-form-grid">
     <div class="field"><label for="clinic-name">Nom</label><input id="clinic-name" v-model="form.name" maxlength="120" placeholder="Centre médical du Port" :aria-invalid="!!fieldErrors.name"><p v-if="fieldErrors.name" class="error-text">{{fieldErrors.name}}</p></div>
     <div class="field"><label for="clinic-city">Ville</label><input id="clinic-city" v-model="form.city" maxlength="80" placeholder="Marseille" :aria-invalid="!!fieldErrors.city"><p v-if="fieldErrors.city" class="error-text">{{fieldErrors.city}}</p></div>
     <div class="field"><label for="clinic-slug">Identifiant dans l’adresse</label><input id="clinic-slug" v-model="form.slug" maxlength="40" aria-describedby="clinic-slug-hint" :aria-invalid="!!fieldErrors.slug" @input="slugTouched=true"><p id="clinic-slug-hint" class="hint">localhost:8088/c/{{form.slug||'…'}}/</p><p v-if="fieldErrors.slug" class="error-text">{{fieldErrors.slug}}</p></div>
     <div class="field"><label for="clinic-color">Couleur</label><input id="clinic-color" v-model="form.color" type="color" :aria-invalid="!!fieldErrors.color"><p v-if="fieldErrors.color" class="error-text">{{fieldErrors.color}}</p></div>
     <div class="field wide"><label for="clinic-address">Adresse postale</label><input id="clinic-address" v-model="form.address" maxlength="200" autocomplete="off" placeholder="12 quai du Port, 13002" :aria-invalid="!!fieldErrors.address"><p v-if="fieldErrors.address" class="error-text">{{fieldErrors.address}}</p></div>
     <div class="field wide"><label for="clinic-admin">Admin du cabinet <span class="hint">(facultatif)</span></label><select id="clinic-admin" v-model="form.adminEmail" :aria-invalid="!!fieldErrors.adminEmail"><option value="">Aucun admin pour l’instant</option><option v-for="a in accounts" :key="a.email" :value="a.email">{{a.name}} — {{a.email}}</option></select><p v-if="fieldErrors.adminEmail" class="error-text">{{fieldErrors.adminEmail}}</p></div>
    </div>
    <p role="alert" class="error-text"><template v-if="formError">{{formError}}</template></p>
    <div class="admin-form-actions"><button type="submit" class="primary" :aria-disabled="saving">{{saving?'Création…':'Créer le cabinet'}}</button><button type="button" class="text-button" @click="creating=false">Annuler</button></div>
   </form>
   <div class="admin-clinics">
    <article v-for="c in all" :key="c.slug" class="admin-clinic" :class="{current:c.slug===clinic.current?.slug}" :style="{'--option':safeColor(c.color)}">
     <h3>{{c.name}}</h3>
     <p class="admin-clinic-city">{{c.address?`${c.address}, ${c.city}`:c.city}}</p>
     <dl class="admin-clinic-stats"><div><dd>{{c.professionals}}</dd><dt>praticiens</dt></div><div><dd>{{c.upcoming}}</dd><dt>RDV à venir</dt></div><div><dd>{{c.members}}</dd><dt>membres</dt></div></dl>
     <p class="admin-clinic-admins">{{c.admins.length?c.admins.join(' · '):'Aucun admin'}}</p>
     <button v-if="c.slug!==clinic.current?.slug" class="text-button" @click="emit('open',c.slug)">Ouvrir ce cabinet <span aria-hidden="true">→</span></button>
     <p v-else class="admin-clinic-here">Cabinet affiché</p>
    </article>
   </div>
  </section>
  <dl v-if="overview" class="admin-stats">
   <div><dt>Praticiens</dt><dd>{{overview.professionals}}</dd></div>
   <div><dt>Rendez-vous à venir</dt><dd>{{overview.upcoming}}</dd></div>
   <div><dt>Membres</dt><dd>{{overview.members}}</dd></div>
   <div><dt>Admins</dt><dd>{{overview.admins}}</dd></div>
  </dl>
  <section class="admin-block" aria-labelledby="admin-bookings">
   <h2 id="admin-bookings">Prochains rendez-vous</h2>
   <p v-if="!bookings.length" class="empty-small">Aucun rendez-vous à venir.</p>
   <div v-else class="admin-table"><table>
    <thead><tr><th scope="col">Date</th><th scope="col">Praticien</th><th scope="col">Patient</th><th scope="col">Statut</th></tr></thead>
    <tbody><tr v-for="b in bookings" :key="b.id"><td>{{dateLabel(b.startsAt)}} à {{timeLabel(b.startsAt)}}</td><td>{{b.professionalName}} <small>{{specialties[b.specialty]??b.specialty}}</small></td><td>{{b.patientName}}</td><td><span class="status" :class="{cancelled:b.status==='CANCELLED'}">{{b.status==='CONFIRMED'?'Confirmé':'Annulé'}}</span></td></tr></tbody>
   </table></div>
  </section>
  <section class="admin-block" aria-labelledby="admin-members">
   <h2 id="admin-members">Membres du cabinet</h2>
   <div class="admin-table"><table>
    <thead><tr><th scope="col">Nom</th><th scope="col">E-mail</th><th scope="col">Rôle</th></tr></thead>
    <tbody><tr v-for="m in members" :key="m.id"><td>{{m.name}}</td><td>{{m.email}}</td><td><span v-if="m.role==='ADMIN'" class="clinic-role">Admin</span><template v-else>Patient</template></td></tr></tbody>
   </table></div>
  </section>
 </div>
</template>

<script setup lang="ts">
import {ref,watch} from 'vue';
import {api} from '../api';
import {useClinic} from '../stores/clinic';
import {useSession} from '../stores/session';
import {safeColor} from '../tenant';
import {specialties,dateLabel,timeLabel} from '../types';
interface Overview {professionals:number;upcoming:number;members:number;admins:number}
interface Member {id:number;name:string;email:string;role:'ADMIN'|'MEMBER'}
interface Booking {id:number;startsAt:string;professionalName:string;specialty:string;patientName:string;status:string}
interface ClinicStats {slug:string;name:string;city:string;color:string;professionals:number;upcoming:number;members:number;admins:string[]}
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
</script>
<template>
 <div class="admin">
  <div class="section-title"><div><p class="eyebrow dark">{{session.user?.role==='MASTER'?'ADMINISTRATION GLOBALE':'ADMINISTRATION DU CABINET'}}</p><h1 tabindex="-1">{{clinic.current?.name}}</h1></div><button class="secondary" :aria-disabled="loading" @click="!loading&&load()">Actualiser</button></div>
  <div role="alert"><p v-if="error" class="alert error">{{error}}</p></div>
  <section v-if="all.length" class="admin-block" aria-labelledby="all-clinics">
   <h2 id="all-clinics">Tous les cabinets</h2>
   <div class="admin-clinics">
    <article v-for="c in all" :key="c.slug" class="admin-clinic" :class="{current:c.slug===clinic.current?.slug}" :style="{'--option':safeColor(c.color)}">
     <h3><span class="clinic-dot" aria-hidden="true"></span>{{c.name}}</h3>
     <p class="subtle-small">{{c.city}} · {{c.admins.length?c.admins.join(', '):'aucun admin'}}</p>
     <dl><div><dt>Praticiens</dt><dd>{{c.professionals}}</dd></div><div><dt>RDV à venir</dt><dd>{{c.upcoming}}</dd></div><div><dt>Membres</dt><dd>{{c.members}}</dd></div></dl>
     <button v-if="c.slug!==clinic.current?.slug" class="text-button" @click="emit('open',c.slug)">Ouvrir ce cabinet <span aria-hidden="true">→</span></button>
     <p v-else class="subtle-small">Cabinet affiché</p>
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

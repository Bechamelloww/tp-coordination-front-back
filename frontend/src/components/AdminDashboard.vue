<script setup lang="ts">
import {nextTick,ref,watch} from 'vue';
import {api,ApiError} from '../api';
import {useClinic} from '../stores/clinic';
import {useSession} from '../stores/session';
import {safeColor,slugify} from '../tenant';
import {specialties,dateLabel,timeLabel,type AdminTab} from '../types';
import ActivityHistory from './ActivityHistory.vue';
import ClinicStatisticsDashboard from './ClinicStatisticsDashboard.vue';
import NetworkStatisticsDashboard from './NetworkStatisticsDashboard.vue';
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
function openForm(){editing.value=null;confirming.value=null;api<{email:string;name:string}[]>('/master/accounts').then(a=>accounts.value=a).catch(()=>{accounts.value=[]});creating.value=true;created.value='';form.value=blank();slugTouched.value=false;formError.value='';fieldErrors.value={}}
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

// Modification d'un cabinet (master) : tout sauf l'identifiant, qui reste dans les adresses /c/<slug>/ déjà partagées.
const editing=ref<ClinicStats|null>(null),editForm=ref({name:'',city:'',address:'',color:''}),editBusy=ref(false),editError=ref(''),editErrors=ref<Record<string,string>>({});
function openEdit(c:ClinicStats){
 creating.value=false;confirming.value=null;created.value='';editError.value='';editErrors.value={};
 editing.value=c;editForm.value={name:c.name,city:c.city,address:c.address??'',color:c.color};
 void nextTick(()=>document.getElementById('edit-name')?.focus());
}
async function saveEdit(){
 const target=editing.value;if(!target||editBusy.value)return;editBusy.value=true;editError.value='';editErrors.value={};
 try{
  const c=await api<ClinicStats>(`/master/clinics/${encodeURIComponent(target.slug)}`,{method:'PUT',body:JSON.stringify(editForm.value)});
  all.value=all.value.map(x=>x.slug===c.slug?c:x);
  editing.value=null;created.value=`Le cabinet « ${c.name} » a été modifié.`;
  if(clinic.current?.slug===c.slug){clinic.current={...clinic.current,name:c.name,city:c.city,color:c.color};clinic.repaint(false)}
  await clinic.loadMine().catch(()=>{});
 }catch(e){editError.value=e instanceof Error?e.message:'Modification impossible.';if(e instanceof ApiError)editErrors.value=e.fields}
 finally{editBusy.value=false}
}

// Suppression (master) : le back refuse tant qu'il reste des rendez-vous à venir ; l'historique est conservé.
const confirming=ref<string|null>(null),deleting=ref(false),deleteError=ref('');
function askDelete(c:ClinicStats){
 editing.value=null;creating.value=false;created.value='';deleteError.value='';confirming.value=c.slug;
 void nextTick(()=>document.getElementById(`cancel-delete-${c.slug}`)?.focus());
}
async function confirmDelete(c:ClinicStats){
 if(deleting.value)return;deleting.value=true;deleteError.value='';
 try{
  await api<void>(`/master/clinics/${encodeURIComponent(c.slug)}`,{method:'DELETE'});
  all.value=all.value.filter(x=>x.slug!==c.slug);confirming.value=null;
  created.value=`Le cabinet « ${c.name} » a été supprimé.`;
  await clinic.loadMine().catch(()=>{});
  if(clinic.current?.slug===c.slug&&all.value.length)emit('open',all.value[0].slug);
 }catch(e){deleteError.value=e instanceof Error?e.message:'Suppression impossible.'}
 finally{deleting.value=false}
}

// Ajout d'un membre au cabinet affiché. Le master choisit dans la liste des comptes ; un admin saisit l'e-mail
// (il ne doit pas voir les comptes des autres cabinets).
const isMaster=()=>session.user?.role==='MASTER';
const adding=ref(false),addingBusy=ref(false),newMember=ref({email:'',role:'MEMBER'}),addError=ref(''),addErrors=ref<Record<string,string>>({}),added=ref('');
const candidates=ref<{email:string;name:string}[]>([]);
function openAdd(){
 adding.value=true;added.value='';addError.value='';addErrors.value={};newMember.value={email:'',role:'MEMBER'};
 if(isMaster())api<{email:string;name:string}[]>('/master/accounts').then(a=>{const known=new Set(members.value.map(m=>m.email));candidates.value=a.filter(x=>!known.has(x.email))}).catch(()=>{candidates.value=[]});
}
async function addMember(){
 if(addingBusy.value)return;addingBusy.value=true;addError.value='';addErrors.value={};
 try{
  const m=await api<Member>('/admin/members',{method:'POST',body:JSON.stringify(newMember.value)});
  members.value=[...members.value,m].sort((a,b)=>a.role===b.role?a.name.localeCompare(b.name):a.role==='ADMIN'?-1:1);
  if(overview.value)overview.value={...overview.value,members:overview.value.members+1,admins:overview.value.admins+(m.role==='ADMIN'?1:0)};
  adding.value=false;added.value=`${m.name} a été ajouté${m.role==='ADMIN'?' comme admin':''}.`;
 }catch(e){addError.value=e instanceof Error?e.message:'Ajout impossible.';if(e instanceof ApiError)addErrors.value=e.fields}
 finally{addingBusy.value=false}
}
watch(()=>clinic.current?.slug,()=>{adding.value=false;added.value=''});

const tabs:{id:AdminTab;label:string}[]=[{id:'manage',label:'Gestion'},{id:'stats',label:'Statistiques'},{id:'history',label:'Historique'}];
const tab=defineModel<AdminTab>('tab',{default:'manage'});
const opened=ref(new Set<AdminTab>([tab.value]));
watch(tab,next=>{opened.value.add(next)});
const statsPanel=ref<{reload:()=>void}|null>(null),historyPanel=ref<InstanceType<typeof ActivityHistory>|null>(null);
function selectTab(next:AdminTab,focus=false){
 tab.value=next;
 if(focus)void nextTick(()=>document.getElementById(`admin-tab-${next}`)?.focus());
}
function onTabKey(e:KeyboardEvent){
 const index=tabs.findIndex(t=>t.id===tab.value);
 const moves:Record<string,number>={ArrowRight:(index+1)%tabs.length,ArrowLeft:(index-1+tabs.length)%tabs.length,Home:0,End:tabs.length-1};
 if(!(e.key in moves))return;
 e.preventDefault();
 selectTab(tabs[moves[e.key]].id,true);
}
function refresh(){
 if(tab.value==='history')historyPanel.value?.reload();
 else if(tab.value==='stats')statsPanel.value?.reload();
 else if(!loading.value)void load();
}
</script>
<template>
 <div class="admin">
  <div class="section-title"><div><p class="eyebrow dark">{{session.user?.role==='MASTER'?'ADMINISTRATION GLOBALE':'ADMINISTRATION DU CABINET'}}</p><h1 tabindex="-1">{{clinic.current?.name}}</h1></div><button class="secondary" :aria-disabled="tab==='manage'&&loading" @click="refresh">Actualiser</button></div>
  <div class="admin-tabs" role="tablist" aria-label="Sections de l’administration" @keydown="onTabKey">
   <button v-for="t in tabs" :id="`admin-tab-${t.id}`" :key="t.id" type="button" role="tab" :aria-selected="tab===t.id" :aria-controls="`admin-panel-${t.id}`" :tabindex="tab===t.id?0:-1" @click="selectTab(t.id)">{{t.label}}</button>
  </div>
  <div v-show="tab==='manage'" id="admin-panel-manage" role="tabpanel" aria-labelledby="admin-tab-manage">
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
   <form v-if="editing" class="admin-form" novalidate @submit.prevent="saveEdit">
    <h3>Modifier « {{editing.name}} »</h3>
    <div class="admin-form-grid">
     <div class="field"><label for="edit-name">Nom</label><input id="edit-name" v-model="editForm.name" maxlength="120" :aria-invalid="!!editErrors.name"><p v-if="editErrors.name" class="error-text">{{editErrors.name}}</p></div>
     <div class="field"><label for="edit-city">Ville</label><input id="edit-city" v-model="editForm.city" maxlength="80" :aria-invalid="!!editErrors.city"><p v-if="editErrors.city" class="error-text">{{editErrors.city}}</p></div>
     <div class="field"><label for="edit-slug">Identifiant dans l’adresse</label><input id="edit-slug" :value="editing.slug" readonly aria-describedby="edit-slug-hint"><p id="edit-slug-hint" class="hint">Ne change pas : les liens déjà partagés restent valables.</p></div>
     <div class="field"><label for="edit-color">Couleur</label><input id="edit-color" v-model="editForm.color" type="color" :aria-invalid="!!editErrors.color"><p v-if="editErrors.color" class="error-text">{{editErrors.color}}</p></div>
     <div class="field wide"><label for="edit-address">Adresse postale</label><input id="edit-address" v-model="editForm.address" maxlength="200" autocomplete="off" :aria-invalid="!!editErrors.address"><p v-if="editErrors.address" class="error-text">{{editErrors.address}}</p></div>
    </div>
    <p role="alert" class="error-text"><template v-if="editError">{{editError}}</template></p>
    <div class="admin-form-actions"><button type="submit" class="primary" :aria-disabled="editBusy">{{editBusy?'Enregistrement…':'Enregistrer'}}</button><button type="button" class="text-button" @click="editing=null">Annuler</button></div>
   </form>
   <div class="admin-clinics">
    <article v-for="c in all" :key="c.slug" class="admin-clinic" :class="{current:c.slug===clinic.current?.slug}" :style="{'--option':safeColor(c.color)}">
     <h3>{{c.name}}</h3>
     <p class="admin-clinic-city">{{c.address?`${c.address}, ${c.city}`:c.city}}</p>
     <dl class="admin-clinic-stats"><div><dd>{{c.professionals}}</dd><dt>praticiens</dt></div><div><dd>{{c.upcoming}}</dd><dt>RDV à venir</dt></div><div><dd>{{c.members}}</dd><dt>membres</dt></div></dl>
     <p class="admin-clinic-admins">{{c.admins.length?c.admins.join(' · '):'Aucun admin'}}</p>
     <button v-if="c.slug!==clinic.current?.slug" class="text-button" @click="emit('open',c.slug)">Ouvrir ce cabinet <span aria-hidden="true">→</span></button>
     <p v-else class="admin-clinic-here">Cabinet affiché</p>
     <div v-if="confirming===c.slug" class="admin-clinic-confirm" role="group" :aria-labelledby="`confirm-${c.slug}`">
      <p :id="`confirm-${c.slug}`">Supprimer ce cabinet ? Il disparaîtra pour tout le monde. Les rendez-vous passés et l’historique sont conservés.</p>
      <p role="alert" class="error-text"><template v-if="deleteError">{{deleteError}}</template></p>
      <div class="admin-clinic-actions"><button type="button" class="danger-button" :aria-disabled="deleting" @click="confirmDelete(c)">{{deleting?'Suppression…':'Supprimer'}}</button><button :id="`cancel-delete-${c.slug}`" type="button" class="text-button" @click="confirming=null">Annuler</button></div>
     </div>
     <div v-else class="admin-clinic-actions"><button type="button" class="text-button" :aria-label="`Modifier ${c.name}`" @click="openEdit(c)">Modifier</button><button type="button" class="text-button danger-link" :aria-label="`Supprimer ${c.name}`" @click="askDelete(c)">Supprimer</button></div>
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
   <div class="admin-block-head"><h2 id="admin-members">Membres du cabinet</h2><button v-if="!adding" class="secondary" @click="openAdd">Ajouter un membre</button></div>
   <div role="status"><p v-if="added" class="alert success">{{added}}</p></div>
   <form v-if="adding" class="admin-form" novalidate @submit.prevent="addMember">
    <h3>Ajouter un membre</h3>
    <div class="admin-form-grid">
     <div v-if="isMaster()" class="field"><label for="member-email">Compte</label><select id="member-email" v-model="newMember.email" :aria-invalid="!!addErrors.email"><option value="" disabled>Choisir un compte</option><option v-for="a in candidates" :key="a.email" :value="a.email">{{a.name}} — {{a.email}}</option></select><p v-if="addErrors.email" class="error-text">{{addErrors.email}}</p></div>
     <div v-else class="field"><label for="member-email">E-mail du compte</label><input id="member-email" v-model="newMember.email" type="email" autocomplete="off" placeholder="nom@exemple.fr" aria-describedby="member-email-hint" :aria-invalid="!!addErrors.email"><p id="member-email-hint" class="hint">La personne doit déjà avoir un compte MediSlot.</p><p v-if="addErrors.email" class="error-text">{{addErrors.email}}</p></div>
     <div class="field"><label for="member-role">Rôle</label><select id="member-role" v-model="newMember.role"><option value="MEMBER">Patient</option><option value="ADMIN">Admin du cabinet</option></select></div>
    </div>
    <p role="alert" class="error-text"><template v-if="addError">{{addError}}</template></p>
    <div class="admin-form-actions"><button type="submit" class="primary" :aria-disabled="addingBusy">{{addingBusy?'Ajout…':'Ajouter au cabinet'}}</button><button type="button" class="text-button" @click="adding=false">Annuler</button></div>
   </form>
   <div class="admin-table"><table>
    <thead><tr><th scope="col">Nom</th><th scope="col">E-mail</th><th scope="col">Rôle</th></tr></thead>
    <tbody><tr v-for="m in members" :key="m.id"><td>{{m.name}}</td><td>{{m.email}}</td><td><span v-if="m.role==='ADMIN'" class="clinic-role">Admin</span><template v-else>Patient</template></td></tr></tbody>
   </table></div>
  </section>
  </div>
  <div v-show="tab==='stats'" id="admin-panel-stats" role="tabpanel" aria-labelledby="admin-tab-stats">
   <template v-if="opened.has('stats')">
    <NetworkStatisticsDashboard v-if="isMaster()" ref="statsPanel"/>
    <ClinicStatisticsDashboard v-else ref="statsPanel"/>
   </template>
  </div>
  <div v-show="tab==='history'" id="admin-panel-history" role="tabpanel" aria-labelledby="admin-tab-history">
   <ActivityHistory v-if="opened.has('history')" ref="historyPanel" :global="isMaster()" :clinics="all"/>
  </div>
 </div>
</template>

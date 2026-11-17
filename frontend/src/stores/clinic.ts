import {defineStore} from 'pinia';import {ref,computed} from 'vue';import {api} from '../api';
import {clinicFromPath,clinicFromHost,clinicPath,safeColor,setCurrentClinic,DEFAULT_CLINIC} from '../tenant';
export interface Clinic {slug:string;name:string;city:string;color:string;role?:'ADMIN'|'MEMBER'|null}
export const useClinic=defineStore('clinic',()=>{
 const current=ref<Clinic|null>(null),mine=ref<Clinic[]>([]);
 const isAdmin=computed(()=>mine.value.some(c=>c.slug===current.value?.slug&&c.role==='ADMIN'));
 function paint(c:Clinic){document.documentElement.style.setProperty('--clinic',safeColor(c.color))}
 /** Au chargement : cabinet de l'URL (chemin, puis nom d'hôte), sinon cabinet par défaut. */
 async function init(){
  const slug=clinicFromPath(location.pathname)??clinicFromHost(location.hostname)??DEFAULT_CLINIC;
  setCurrentClinic(slug);
  current.value=await api<Clinic>('/clinic');paint(current.value);
  history.replaceState(null,'',clinicPath(slug));
 }
 async function loadMine(){mine.value=await api<Clinic[]>('/me/clinics')}
 /** Visiteur non connecté : tous les cabinets, pour qu'il puisse quitter celui de l'adresse. */
 async function loadPublic(){mine.value=await api<Clinic[]>('/clinics')}
 /** Bascule vers un cabinet dont l'utilisateur est membre ; rend false sinon. */
 function switchTo(slug:string){
  const target=mine.value.find(c=>c.slug===slug);
  if(!target)return false;
  setCurrentClinic(slug);current.value=target;paint(target);
  history.pushState(null,'',clinicPath(slug));
  return true;
 }
 function reset(){mine.value=[]}
 return {current,mine,isAdmin,init,loadMine,loadPublic,switchTo,reset};
});

import {ref,onMounted,onBeforeUnmount} from 'vue';import {probe} from './probe';
export type NetState='offline'|'checking'|'online'|'unreachable';
/** Quatre états. L'événement « online » déclenche une vérification, pas un « c'est bon ». */
export function useNetworkStatus(){
 const state=ref<NetState>(typeof navigator==='undefined'||navigator.onLine?'checking':'offline');
 async function check(){
  if(!navigator.onLine){state.value='offline';return}
  state.value='checking';
  const ok=await probe();
  if(navigator.onLine)state.value=ok?'online':'unreachable';
 }
 const off=()=>{state.value='offline'};
 onMounted(()=>{window.addEventListener('online',check);window.addEventListener('offline',off);void check();});
 onBeforeUnmount(()=>{window.removeEventListener('online',check);window.removeEventListener('offline',off);});
 return {state,check};
}

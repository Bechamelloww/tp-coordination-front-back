import {defineStore} from 'pinia';import {ref} from 'vue';import {api,refreshCsrf,ApiError} from '../api';import type {Identity} from '../types';
export interface CodeChallenge {destination:string}
export const useSession=defineStore('session',()=>{
 const user=ref<Identity|null>(null);
 async function load(){try{user.value=await api<Identity>('/me')}catch(e){if(e instanceof ApiError&&e.status===401)user.value=null;else throw e}}
 async function login(email:string,password:string):Promise<CodeChallenge|null>{
  await refreshCsrf();
  const step=await api<{etape?:string;destination?:string}|undefined>('/login',{method:'POST',body:new URLSearchParams({username:email,password})});
  if(step?.etape==='code')return {destination:step.destination??email};
  await refreshCsrf();await load();return null;
 }
 async function verifyCode(code:string,remember:boolean){await api('/login/code',{method:'POST',body:JSON.stringify({code,remember})});await refreshCsrf();await load();}
 async function logout(){await api('/logout',{method:'POST'});user.value=null;await refreshCsrf();}
 return {user,load,login,verifyCode,logout};
});

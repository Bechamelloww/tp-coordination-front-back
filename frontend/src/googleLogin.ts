import {api,ApiError} from './api';
import type {Browser} from './googleCalendar';

export const LOGIN_RETURN_PARAMETER='connexion';
export const LOGIN_RETURN_MESSAGES={
 google:'Vous êtes connecté avec votre compte Google.',
 refuse:'La connexion avec Google a été annulée, ou votre adresse e-mail Google n’est pas vérifiée.',
 'compte-existant':'Un compte MediSlot existe déjà avec cette adresse. Connectez-vous avec votre e-mail et votre mot de passe.',
 erreur:'La connexion avec Google a échoué. Réessayez, ou connectez-vous avec votre e-mail et votre mot de passe.',
} as const;
export type LoginOutcome=keyof typeof LOGIN_RETURN_MESSAGES;
export const LOGIN_START_FAILED='La connexion avec Google n’a pas pu démarrer. Réessayez plus tard.';

export async function googleLoginEnabled():Promise<boolean>{
 try{return (await api<{enabled:boolean}>('/auth/google/enabled')).enabled}catch{return false}
}

/** Demande au back l'adresse de consentement Google (POST protégé par CSRF), puis y envoie le navigateur. */
export async function startGoogleLogin(browser:Browser):Promise<void>{
 let url:URL;
 try{
  url=new URL((await api<{url:string}>('/auth/google/start',{method:'POST'})).url);
 }catch(e){
  if(e instanceof ApiError||e instanceof TypeError)throw e;
  throw new Error(LOGIN_START_FAILED);
 }
 if(url.protocol!=='https:')throw new Error(LOGIN_START_FAILED);
 browser.navigateTo(url.href);
}

export interface LoginReturn {outcome:LoginOutcome;message:string}

/** Lit puis retire ?connexion=… de l'adresse, pour qu'un rechargement ne réaffiche pas le message. */
export function readLoginReturn(browser:Browser):LoginReturn|null{
 const pageUrl=new URL(browser.currentUrl());
 if(!pageUrl.searchParams.has(LOGIN_RETURN_PARAMETER))return null;
 const value=pageUrl.searchParams.get(LOGIN_RETURN_PARAMETER)??'';
 pageUrl.searchParams.delete(LOGIN_RETURN_PARAMETER);
 browser.replaceCurrentUrl(pageUrl.pathname+pageUrl.search+pageUrl.hash);
 const outcome:LoginOutcome=Object.hasOwn(LOGIN_RETURN_MESSAGES,value)?value as LoginOutcome:'erreur';
 return {outcome,message:LOGIN_RETURN_MESSAGES[outcome]};
}

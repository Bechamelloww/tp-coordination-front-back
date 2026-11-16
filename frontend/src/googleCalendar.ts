import {api,ApiError} from './api';
import type {GoogleStatus} from './types';

export const RETURN_PARAMETER='google';
export const RETURN_MESSAGES={
 connecte:'Votre agenda Google est relié : vos rendez-vous MediSlot y seront ajoutés, déplacés et supprimés automatiquement.',
 refuse:'Vous n’avez pas autorisé l’accès à Google Agenda : vos rendez-vous ne sont pas synchronisés.',
 erreur:'La liaison avec Google Agenda a échoué. Réessayez depuis « Mes rendez-vous ».',
} as const;
export type ReturnOutcome=keyof typeof RETURN_MESSAGES;
export const SESSION_EXPIRED_MESSAGE='Votre session a expiré. Reconnectez-vous pour gérer Google Agenda.';
export const OFFLINE_MESSAGE='Pas de connexion : Google Agenda ne peut pas être modifié pour le moment.';
export const FALLBACK_MESSAGE='Google Agenda est momentanément indisponible. Réessayez plus tard.';
export const INVALID_CONSENT_URL_MESSAGE='Le lien vers Google n’a pas pu être préparé. Réessayez plus tard.';

export interface Browser {
 currentUrl():string;
 navigateTo(url:string):void;
 replaceCurrentUrl(url:string):void;
}

export interface ReturnResult {outcome:ReturnOutcome;message:string}

export function fetchGoogleStatus():Promise<GoogleStatus>{
 return api<GoogleStatus>('/google/status');
}

export async function connectGoogle(browser:Browser):Promise<void>{
 const {url:consentUrl}=await api<{url:string}>('/google/authorize',{method:'POST'});
 let parsedConsentUrl:URL;
 try{
  parsedConsentUrl=new URL(consentUrl);
 }catch{
  throw new Error(INVALID_CONSENT_URL_MESSAGE);
 }
 if(parsedConsentUrl.protocol!=='https:')throw new Error(INVALID_CONSENT_URL_MESSAGE);
 browser.navigateTo(parsedConsentUrl.href);
}

export function disconnectGoogle():Promise<void>{
 return api<void>('/google/link',{method:'DELETE'});
}

export function googleErrorMessage(error:unknown):string{
 if(error instanceof ApiError)return error.status===401?SESSION_EXPIRED_MESSAGE:error.message||FALLBACK_MESSAGE;
 if(error instanceof TypeError)return OFFLINE_MESSAGE;
 if(error instanceof Error&&error.message===INVALID_CONSENT_URL_MESSAGE)return INVALID_CONSENT_URL_MESSAGE;
 return FALLBACK_MESSAGE;
}

function isReturnOutcome(value:string):value is ReturnOutcome{
 return Object.hasOwn(RETURN_MESSAGES,value);
}

export function readGoogleReturn(browser:Browser):ReturnResult|null{
 const pageUrl=new URL(browser.currentUrl());
 if(!pageUrl.searchParams.has(RETURN_PARAMETER))return null;
 const returnedValue=pageUrl.searchParams.get(RETURN_PARAMETER)??'';
 pageUrl.searchParams.delete(RETURN_PARAMETER);
 browser.replaceCurrentUrl(pageUrl.pathname+pageUrl.search+pageUrl.hash);
 const outcome:ReturnOutcome=isReturnOutcome(returnedValue)?returnedValue:'erreur';
 return {outcome,message:RETURN_MESSAGES[outcome]};
}

export const realBrowser:Browser={
 currentUrl:()=>window.location.href,
 navigateTo:url=>window.location.assign(url),
 replaceCurrentUrl:url=>window.history.replaceState(window.history.state,'',url),
};

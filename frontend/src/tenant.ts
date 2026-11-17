// Cabinet courant : lu dans l'URL, envoyé au serveur dans l'en-tête X-Clinic.
export const SLUG=/^[a-z0-9]+(-[a-z0-9]+)*$/;
export const DEFAULT_CLINIC='cabinet-a';
const BASE_HOST='medislot.test';
const DEFAULT_COLOR='#0873b9';

/** /c/cabinet-b/... donne cabinet-b, tout autre chemin donne null. */
export function clinicFromPath(path:string):string|null{
 const m=/^\/c\/([^/]+)/.exec(path);
 return m&&SLUG.test(m[1])?m[1]:null;
}
/** cabinet-a.medislot.test donne cabinet-a. On teste la fin du nom : cabinet-a.medislot.test.pirate.example donne null. */
export function clinicFromHost(host:string):string|null{
 const name=host.split(':')[0].toLowerCase(),suffix='.'+BASE_HOST;
 if(!name.endsWith(suffix))return null;
 const slug=name.slice(0,-suffix.length);
 return SLUG.test(slug)?slug:null;
}
/** Couleur reçue du serveur, n'est utilisée dans le style que si elle a la forme #rrggbb. */
export function safeColor(color:unknown):string{
 return typeof color==='string'&&/^#[0-9a-f]{6}$/i.test(color)?color.toLowerCase():DEFAULT_COLOR;
}
export const clinicPath=(slug:string)=>`/c/${slug}/`;

let current:string|null=null;
export function setCurrentClinic(slug:string|null){current=slug&&SLUG.test(slug)?slug:null}
export const currentClinic=()=>current;
/** EventSource ne sait pas poser d'en-tête : le cabinet passe dans l'adresse. */
export function withClinic(path:string){return current?`${path}${path.includes('?')?'&':'?'}clinic=${current}`:path}
/** Identifiant proposé à partir du nom : « Centre médical du Port » donne centre-medical-du-port. */
export function slugify(name:string):string{
 return name.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,'-').slice(0,40).replace(/^-+|-+$/g,'');
}

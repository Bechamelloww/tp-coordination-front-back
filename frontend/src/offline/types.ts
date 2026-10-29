export type ReasonCode='FIRST_VISIT'|'FOLLOW_UP'|'PRESCRIPTION'|'RESULTS';
export type ForWhom='SELF'|'RELATIVE';
// editing : en saisie · pending : en attente d'envoi · rejected : refusé par le serveur
export type DraftStatus='editing'|'pending'|'rejected';
/** Brouillon de demande de rendez-vous : une donnée de santé, gardée au minimum. Ni jeton, ni mot de passe. */
export interface BookingDraft {
 userId:number;
 professionalId:number;professionalName:string;
 slotId:number;startsAt:string;
 reasonCode:ReasonCode|null;forWhom:ForWhom|null;relativeName:string;phone:string;
 status:DraftStatus;
 idemKey:string|null; // créée une fois par demande, au premier « Valider »
 problem:string|null; // code du refus serveur (SLOT_TAKEN, INVALID…)
}
/** Copie plate rangée dans IndexedDB, sous la clé booking:<userId>. */
export interface StoredDraft extends BookingDraft {key:string;v:number;savedAt:number}
export interface BookingRequestBody {slotId:number;reasonCode:ReasonCode;forWhom:ForWhom;relativeName:string|null;phone:string|null}
/** Entrée de la file d'envoi : le corps est figé au « Valider », la clé reste la même à chaque essai. */
export interface OutboxItem {idemKey:string;userId:number;body:BookingRequestBody;queuedAt:number}
export interface FieldError {field:string;message:string}

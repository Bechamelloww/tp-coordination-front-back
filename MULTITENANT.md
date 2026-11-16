# MediSlot multi-cabinet : base partagée, tenant_id et RLS

Deux cabinets fictifs, une seule base : `cabinet-a` (Cabinet des Tilleuls, Paris) et `cabinet-b` (Maison de santé du Canal, Lyon).
Aucune donnée réelle : uniquement les deux cabinets et les comptes fictifs de démonstration.

## §1 Modèle choisi : base partagée, colonne `clinic_id`, RLS

### Critères

| Critère | Base par cabinet | Schéma par cabinet | **Base partagée + `clinic_id` + RLS (retenu)** |
|---|---|---|---|
| Isolation | Maximale (frontière physique) | Forte | Logique : tient à la discipline du code, d'où la RLS en seconde barrière |
| Coût d'un nouveau cabinet | Une base à créer, sauvegarder, superviser | Un schéma à migrer | Une ligne dans `clinic` |
| Migrations | À rejouer N fois | À rejouer N fois | Une seule fois (Flyway) |
| Pool de connexions | Un par base | Partagé, `search_path` à gérer | Un seul, le cabinet est posé par transaction |
| Compte rattaché à plusieurs cabinets | Doublon de compte | Doublon de compte | Natif : `app_user` partagé, table `membership` |
| Requêtes inter-cabinets (statistiques globales) | Très difficiles | Difficiles | Simples, mais dangereuses (c'est le défi de l'étape 5) |
| Taille actuelle de MediSlot | Disproportionné | Disproportionné | Adapté : 2 cabinets, quelques milliers de lignes |

Le risque principal du modèle retenu est l'oubli d'un filtre : une requête sans `clinic_id` lit tous les cabinets. D'où deux barrières
indépendantes : le filtre applicatif (Hibernate `@TenantId`, étape 2) et la RLS PostgreSQL (étape 3).

### Données propres à un cabinet (portent `clinic_id`)

| Table | Pourquoi propre |
|---|---|
| `professional` | Un praticien exerce dans un cabinet |
| `slot` | Créneau d'un praticien |
| `appointment` | Rendez-vous sur un créneau |
| `audit_event` | Historique d'un rendez-vous |
| `notification` | Message adressé au patient au titre d'un cabinet |
| `booking_key` | Clé d'idempotence d'une réservation, liée à un créneau |

### Données partagées (sans `clinic_id`)

| Table | Pourquoi partagée |
|---|---|
| `app_user` | Un compte (e-mail unique) peut appartenir à plusieurs cabinets |
| `clinic` | Le référentiel des cabinets lui-même (lu seulement par l'application) |
| `membership` | Lien compte ↔ cabinet : sert à vérifier l'appartenance |
| `ab_experiment`, `ab_participant` | Expérience A/B sur l'interface, au niveau du compte |
| `google_calendar_link` | Lien Google d'un compte, non d'un cabinet |

### Contraintes posées par la migration `V7__clinics.sql`

- **Unicités** : `clinic.slug` unique (et limité à `[a-z0-9-]`, donc sûr dans une URL et un en-tête) ; `UNIQUE (clinic_id, id)` sur
  `professional`, `slot` et `appointment`, nécessaire pour être la cible d'une clé étrangère composite.
- **Clés étrangères composites** : `slot (clinic_id, professional_id)` → `professional (clinic_id, id)`,
  `appointment (clinic_id, slot_id)` → `slot (clinic_id, id)`, `audit_event (clinic_id, appointment_id)` → `appointment (clinic_id, id)`,
  et les deux clés de `booking_key`. Conséquence : **la base refuse** un rendez-vous du cabinet A sur un créneau du cabinet B,
  même si le code se trompe (prouvé par `ClinicSchemaTest`).
- **Rôle `medislot_app`** : créé sans connexion, ni superutilisateur ni `BYPASSRLS`. C'est le rôle sous lequel l'application devra
  tourner pour que la RLS (V8, étape 3) s'applique. Le superutilisateur `medislot` du `compose.yaml` ignore la RLS.
- **Reprise de l'existant** : toutes les lignes d'avant le multi-cabinet sont rattachées à `cabinet-a`, y compris les praticiens de Lyon et
  de Bordeaux déjà en base ; `cabinet-b` reçoit trois praticiens fictifs à l'amorçage (`DemoData`).

### Limites connues à ce stade

- `appointment.patient_id` n'est pas contraint par `membership` : rien n'interdit encore, en base, un rendez-vous d'un compte non membre.
  Le contrôle d'appartenance est celui du filtre (étape 2).
- Tant que le filtre de l'étape 2 n'est pas en place, l'API renvoie encore les praticiens des deux cabinets.

## §2 Cabinet courant, filtre et contexte

### Du navigateur à la base

1. **URL** : `/c/cabinet-b/` (ou le nom d'hôte `cabinet-b.medislot.test`). `tenant.ts` en extrait le slug et le valide
   (`[a-z0-9-]`) ; l'adresse `cabinet-a.medislot.test.pirate.example` est refusée parce qu'on teste la fin du nom d'hôte.
2. **Requête** : `api.ts` ajoute `X-Clinic: <slug>` à chaque appel. Les flux SSE (`EventSource` ne sait pas poser d'en-tête)
   passent le cabinet dans l'adresse : `?clinic=<slug>`.
3. **`TenantFilter`** (dans la chaîne Spring Security, après l'authentification) :
   - slug absent ou mal formé : **400** ;
   - cabinet inconnu, ou utilisateur connecté qui n'en est pas membre : **404** (même réponse dans les deux cas, pour ne pas
     révéler l'existence d'un cabinet) ;
   - visiteur anonyme : accepté (annuaire public) ;
   - sinon le cabinet est posé dans `TenantContext`, et **retiré dans un `finally`**, y compris si la suite de la chaîne lève une exception.
   Il n'est pas un `@Component` : Spring Boot l'enregistrerait aussi dans la chaîne des servlets, hors de Spring Security.
4. **Hibernate** : les cinq entités propres portent `@TenantId` sur `clinicId`. `ClinicTenantResolver` lit `TenantContext` ;
   Hibernate ajoute `clinic_id = ?` à chaque lecture et le renseigne à chaque insertion. Sans cabinet, l'identifiant `0`
   ne correspond à aucune ligne : la requête échoue fermée, elle ne devient pas globale.

Appels sans cabinet (le filtre ne s'applique pas) : `/api/csrf`, `/api/login`, `/api/logout`, `/api/me`, `/api/me/clinics`, `/api/ab/**`,
`/api/google/**` (retour OAuth de Google, sans en-tête). Ils ne touchent que des tables partagées.

### Ce qui ne passe pas par Hibernate

- `BookingKeys` (JDBC) : filtré à la main sur `clinic_id`.
- Écouteur Google Agenda (`@Async`) : un autre thread ne reçoit pas le `ThreadLocal`. Le cabinet voyage dans l'événement
  `AppointmentChanged` et l'écouteur le pose lui-même.
- Flux SSE public : chaque abonné est rattaché à son cabinet, un événement n'est envoyé qu'aux abonnés du même cabinet.
- `DemoData` : une transaction par cabinet, ouverte **après** avoir posé le cabinet (la session Hibernate garde le cabinet présent à son ouverture).
- Inscription : le nouveau compte est rattaché au cabinet courant (`membership`).

## §3 RLS et cache

*À compléter à l'étape 3.*

## §4 Tests et preuves

| Test | Ce qu'il prouve | Résultat |
|---|---|---|
| `TenantFilterTest` (11 cas) | 400 sans en-tête ou slug mal formé ; 404 cabinet inconnu ou non membre ; anonyme et membre acceptés ; paramètre `clinic` pour le SSE ; contexte retiré après la requête et après une exception ; appels sans cabinet non filtrés | Vert |
| `tenant.test.ts`, `clinic.test.ts` (front) | Slug lu dans le chemin et le nom d'hôte (dont le piège `.pirate.example`) ; couleur filtrée ; en-tête `X-Clinic` ; bascule refusée vers un cabinet dont on n'est pas membre | Vert |
| `ClinicSchemaTest` (7 cas) | Deux cabinets existent ; membres de démo corrects ; chaque cabinet a ses praticiens et créneaux ; clés composites refusent un mélange de cabinets ; slug unique ; rôle applicatif soumis à la RLS | Vert |

## §5 Défi : fuite d'un nouvel endpoint

*À compléter à l'étape 5.*

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

*À compléter à l'étape 2.*

## §3 RLS et cache

*À compléter à l'étape 3.*

## §4 Tests et preuves

| Test | Ce qu'il prouve | Résultat |
|---|---|---|
| `ClinicSchemaTest` (7 cas) | Deux cabinets existent ; membres de démo corrects ; chaque cabinet a ses praticiens et créneaux ; clés composites refusent un mélange de cabinets ; slug unique ; rôle applicatif soumis à la RLS | Vert |

## §5 Défi : fuite d'un nouvel endpoint

*À compléter à l'étape 5.*

# Rapport de l'existant — MediSlot avant le test A/B

*Date : 29/10/2026 · Contexte : TP « Construire et vérifier un test A/B » (Jour 2, Coordination front & back)*

## 1. Synthèse

| Constat | Détail |
|---|---|
| Application analysée | `MediSlot_application` : application de référence (Spring Boot 4.1.1 / Java 25, Vue 3 + TypeScript + Pinia, PostgreSQL 18). |
| État | Fonctionnelle : les 3 conteneurs tournent, `/actuator/health` = `UP`, la recherche renvoie les 6 praticiens fictifs. |
| A/B testing | **Aucune brique présente** : pas d'affectation, pas de cookie visiteur, pas d'exposition, pas de métriques. `GET /api/ab/assignment` répond 401 (route inexistante, tombée dans la règle « authentifié »). |
| Écart avec le TP | Le TP s'appuie sur **`Kit_TP_AB_Depart.zip`** (API Java + **MongoDB**, port **8092**, `AbService.java`, `app.js`, `SlotController`). Ce kit **n'est pas dans le dossier** : la stack, la base, les ports et les noms de fichiers diffèrent de l'application présente. |

**Conséquence :** soit on récupère le kit et on travaille dessus (ce que demande le TP), soit on transpose le TP dans cette application, ce qui implique de créer l'ensemble de la brique A/B (voir § 6).

## 2. Architecture actuelle

```
Navigateur (Vue 3) ──HTTP + cookie de session──▶ Nginx :8088 ──/api──▶ Spring Boot :8080 ──▶ PostgreSQL :5432
        ▲                                                                   │
        └──────────────────────── SSE (/api/events) ◀───────────────────────┘
```

| Conteneur (compose `medislot-formation`) | Image | Port hôte |
|---|---|---|
| `db` | postgres:18, volume `pgdata` | 127.0.0.1:55432 |
| `backend` | build `./backend` (jar Spring) | 127.0.0.1:18080 |
| `frontend` | build `./frontend` (Nginx + build Vite) | 127.0.0.1:8088 |

Monolithe en couches (ADR 004) : `api` (contrôleurs + DTO) → `application` (`BookingService`, transactions) → `repository` (Spring Data JPA, verrous) → `domain` (entités JPA à champs publics). `security` gère session + CSRF, `realtime` diffuse les SSE après commit.

## 3. Back-end

### 3.1 Routes exposées

| Méthode | Route | Accès | Rôle |
|---|---|---|---|
| GET | `/api/csrf` | public | Jeton CSRF |
| POST | `/api/register`, `/api/login`, `/api/logout` | public / session | Compte et session (form login Spring) |
| GET | `/api/me` | authentifié | Identité courante |
| GET | `/api/professionals?q&city&specialty&page&size` | public | Recherche paginée (tri name puis id, max 50) |
| GET | `/api/professionals/{id}` et `/{id}/slots` | public | Fiche et créneaux libres sur 14 jours |
| GET / POST | `/api/appointments` | authentifié | Lister / **réserver** (`201` + `Location`) |
| GET | `/api/appointments/{id}` et `/{id}/history` | propriétaire | Détail et historique d'audit |
| POST | `/api/appointments/{id}/cancel` | propriétaire | Annulation (idempotente) |
| PATCH | `/api/appointments/{id}` | propriétaire | Déplacement atomique |
| GET | `/api/notifications` | authentifié | 30 dernières notifications |
| GET (SSE) | `/api/events` / `/api/notifications/events` | public / authentifié | `slots-changed` / `notifications-changed`, heartbeat 20 s |

Contrat décrit dans `docs/openapi.yaml` (ADR 005 : DTO distincts des entités).

### 3.2 Réservation (le flux qu'un test A/B mesurerait)

`BookingService.book` dans une seule transaction :
1. patient lu depuis la session (jamais depuis le corps de la requête) ;
2. verrou pessimiste sur le créneau (`SlotRepository.lockById`, `PESSIMISTIC_WRITE`) ;
3. refus `409` si créneau passé ou déjà `CONFIRMED` ;
4. insertion `appointment`, puis `audit_event` (`CREATED`) et `notification` ;
5. publication `BookingChanged`, diffusée en SSE **après commit**.

Filet de sécurité en base : index unique partiel `one_active_appointment_per_slot` (`WHERE status='CONFIRMED'`). Une violation est traduite en `409` par `Errors`.

### 3.3 Données (Flyway `V1__schema.sql`)

`app_user`, `professional`, `slot` (unique professionnel + début), `appointment` (CONFIRMED / CANCELLED), `audit_event`, `notification`. Pas de table ni de collection liée à une expérience. Données de démo (`DemoData`, `DEMO_SEED=true`) : 2 patients, 6 praticiens, 6 créneaux par jour sur 10 jours.

### 3.4 Sécurité

Session serveur + cookie HttpOnly, SameSite=Lax, `Secure` désactivé en local (ADR 001). CSRF actif sur toutes les écritures. Réponses d'erreur au format `ProblemDetail`. Le contrôle de propriété renvoie `404` plutôt que `403` pour ne pas révéler l'existence d'un rendez-vous.

## 4. Front-end

| Fichier | Rôle |
|---|---|
| `src/App.vue` | Écran unique : recherche, résultats, « Mes rendez-vous », modales (connexion, confirmation, fiche, historique, annulation), focus piégé dans les modales. |
| `src/components/ProfessionalCard.vue` | Carte praticien et **boutons de créneaux** (3 jours × 4 créneaux max). |
| `src/api.ts` | `fetch` centralisé : CSRF automatique, `ApiError` avec statut, gestion du `204`. |
| `src/stores/session.ts` | Store Pinia : `load`, `login`, `logout`. |
| `src/composables/useLive.ts` | Abonnement SSE + indicateur « en direct ». |

**Point clé pour le test A/B :** il n'y a **pas de bouton « Réserver »** dans cette version. Le bouton de créneau affiche **l'heure** (`09:00`), avec un `aria-label` « Réserver avec Dr X le … à … ». Le clic ouvre une modale de confirmation, puis « Confirmer le rendez-vous » envoie `POST /api/appointments`. Le parcours compte donc deux clics, et la réservation exige une connexion.

## 5. Qualité, tests et livraison

| Niveau | Contenu |
|---|---|
| Unitaire back | `BookingServiceTest` (Mockito) : créneau passé refusé avant écriture. |
| Intégration back | `BookingIntegrationTest` (Testcontainers PostgreSQL) : 2 réservations concurrentes donnent `201` + `409` ; annulation par un tiers = `404`, historique conservé. |
| Unitaire front | `api.test.ts` (Vitest). |
| Bout en bout | Playwright : réservation + annulation, inscription, conformité au contrat OpenAPI (ajv). |
| CI | `.github/workflows/ci.yml` : jobs back, front et e2e ; actions épinglées par commit. Non exécutée sur un dépôt distant (README). |

Le dossier **n'est pas un dépôt git**.

## 6. Écarts entre l'existant et le TP A/B

| Exigence du TP | Existant | À faire si on transpose le TP ici |
|---|---|---|
| Libellé A « Réserver » / B « Choisir ce créneau » | Bouton = heure du créneau | Décider où le libellé s'applique (bouton de créneau ou bouton de confirmation) sans modifier le parcours. |
| Affectation stable (SHA-256 sur `clé:visiteur`, premier octet, 0-127 → A) | Absente | Service d'affectation + endpoint `GET …/assignment` en `Cache-Control: no-store`. |
| Cookie visiteur opaque (`ab_visitor`) | Seul le cookie de session existe | Cookie posé une seule fois, UUID validé, HttpOnly. Les routes A/B doivent être **publiques** dans `SecurityConfig`. |
| Exposition unique et atomique | Absente | Table `ab_assignment` (ou équivalent) avec `exposed` / `exposed_at` et un `UPDATE … WHERE exposed = false` (équivalent SQL du `MongoTemplate` du kit). |
| Exposition déclarée après rendu d'un bouton disponible | Absente | Dans Vue : `nextTick` (ou `watch` en `flush: 'post'`) puis `POST /api/ab/exposure`, seulement s'il y a au moins un créneau. |
| Pas de scintillement (`ready`) | Absent | Squelette tant que la variante n'est pas connue ; A de secours en cas d'erreur, sans attribution (`X-AB-Measure`). |
| Conversion = réservation `201`, comptée une fois par visiteur, fenêtre 24 h | Réservation transactionnelle robuste, mais pas d'attribution | Écrire l'attribution dans la transaction de `book` ; dédoublonnage par visiteur ; aucune conversion sur un `409`. |
| `GET /api/ab/metrics` (assigned, exposed, converted, rate = null si 0) | Absent | Endpoint d'agrégation. |
| Interrupteur `AB_ENABLED` | Absent | Variable d'environnement dans `compose.yaml` + `enabled` dans la réponse d'affectation. |
| Réservation sans connexion (créneaux de test ajoutés depuis l'UI) | Connexion obligatoire ; pas de bouton « Ajouter un créneau de test » | Le kit simplifie volontairement ces points ; ici il faudrait passer par le compte de démo. |

## 7. Points d'attention

- **Unité de randomisation :** la liste de créneaux est visible avant connexion, donc randomiser par navigateur (cookie) comme dans le cours. Le même patient sur deux appareils comptera deux fois.
- **Données de santé :** ne mettre ni spécialité ni praticien dans les événements A/B (chapitre 5 du cours). Le cookie de mesure doit rester dans le cadre de l'exemption CNIL.
- **Cache :** Nginx ne met rien en cache aujourd'hui (`proxy_buffering off`). Garder `no-store` sur l'affectation.
- **Données existantes :** ne pas supprimer le volume `pgdata` (rappel du README et du TP).
- **Version du cours :** le cours fourni (Java 21 / Spring Boot 3) ne suit pas la même pagination que celle citée dans le TP (p. 10, 17-20), et ne traite pas MongoDB. Le principe SHA-256 se trouve p. 27-29, l'exposition p. 34-37.

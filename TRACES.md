# TRACES.md — Diagnostiquer MediSlot par les traces

TP OpenTelemetry, module Coordination Front & Back. Relevés du 16/11/2026 sur la pile Docker `medislot-otel`
(commit `0f947a6`, `service.version=0f947a6`). Jaeger : http://localhost:16686.

> **Contexte.** Le TP prévoit un kit de départ (`Kit_TP_OpenTelemetry_depart.zip`) avec des TODO et trois problèmes
> cachés : passerelle SMS simulée, libellés d'historique, rendez-vous de démonstration. Ce kit n'était pas disponible.
> L'instrumentation a donc été ajoutée **directement au MediSlot du module**, en suivant le cours (chapitres 3 à 6).
> Les tickets sont diagnostiqués sur ce code-là : T2 y existe vraiment ; T1 et T3 dépendent d'ajouts du kit
> absents ici, et la trace le montre (voir § 2 et § 4).

## 1. Instrumentation en place

| Maillon | Ce qui est fait | Fichier | Preuve |
|---|---|---|---|
| Back (Spring) | Agent Java **2.33.1**, ajouté par `ADD --chmod=644 --checksum=sha256:…` dans `/app`, lisible par l'utilisateur 10001 | `backend/Dockerfile` | Journal : `opentelemetry-javaagent - version: 2.33.1`, puis `Started MedislotApplication` |
| Back (config) | `JAVA_TOOL_OPTIONS=-javaagent:…`, `OTEL_SERVICE_NAME=medislot-api`, `OTEL_EXPORTER_OTLP_ENDPOINT=http://collector:4318`, `OTEL_TRACES_SAMPLER=parentbased_always_on`, métriques et journaux coupés, ressources `service.version=${GIT_SHA}` et `deployment.environment.name=formation` | `compose.yaml` | Jaeger propose `medislot-api` ; ressource `service.version=0f947a6` |
| Back (corrélation) | Filtre `TraceIdHeaderFilter` : en-tête `X-Trace-Id` sur chaque réponse ; le front l'affiche dans les erreurs 5xx (« réf. … ») | `backend/src/main/java/fr/medislot/config/TraceIdHeaderFilter.java`, `frontend/src/api.ts` | Point 3 du script : `X-Trace-Id renvoyé` |
| Collecteur | Récepteur OTLP (HTTP 4318, gRPC 4317), `memory_limiter`, **`attributes/sante`** (supprime `url.query`, `http.url`, `enduser.id`, `user.email`, `user.name`), `batch`, exportateur **`otlp_grpc/jaeger`** + `debug` dans le pipeline `traces` | `observabilite/collector.yaml` | Une trace `GET /api/professionals` avec ses spans SQL arrive dans Jaeger |
| Jaeger | `jaegertracing/jaeger:2.23.0`, publié sur `127.0.0.1:16686` seulement (pas d'authentification) | `compose.yaml` | Interface accessible en local uniquement |
| Navigateur | `demarrerTraces()` : `TracerProvider` (`service.name=medislot-web`), `ParentBasedSampler(TraceIdRatioBased(1))`, `BatchSpanProcessor` → `OTLPTraceExporter` sur `location.origin + '/otlp/v1/traces'`, `StackContextManager`, `W3CTraceContextPropagator`, `FetchInstrumentation({ignoreUrls:[/\/otlp\//]})`. Option `processeur` pour les tests. | `frontend/src/tracing.ts`, appelé dans `main.ts` avant `createApp` | `traceparent` sur `/api/professionals?…`, `POST /otlp/v1/traces` toutes les 5 s environ |
| Navigateur (garde-fou) | `ProcesseurSansSaisie.onStart` réécrit `url.full` sans `?…` : la saisie d'une recherche ne quitte pas le navigateur | `frontend/src/tracing.ts` | Test « ne garde pas la saisie… » ; dans Jaeger `url.full = http://localhost:8088/api/professionals` |
| Nginx | `location /otlp/ { proxy_pass http://collector:4318/; client_max_body_size 1m; }` : même origine, sans CORS, préfixe retiré | `frontend/nginx.conf` | Point 1 du script : `POST /otlp/v1/traces -> 200` |

**Trace de bout en bout (navigateur → back)** : `6bd38164aa03c543582dafbe80710a67`.
Span `GET` de `medislot-web` (223 ms, `url.full=http://localhost:8088/api/professionals`, sans la saisie)
→ enfant `GET /api/professionals` de `medislot-api` (33,7 ms) → `ProfessionalRepository.search` → `SELECT medislot.professional`.

**Relation exigée par `e2e/trace.spec.ts`** : le span du navigateur a pour identifiant le `parent-id` de l'en-tête
`traceparent`, et le span serveur `GET /api/professionals` du back est son **enfant direct** (`CHILD_OF`).
Les deux appartiennent à la même trace, et aucun attribut ne contient le texte saisi.

## 2. T1 — « Confirmer un rendez-vous prend plus d'une seconde »

- **Recherche Jaeger** : service `medislot-api`, opération `POST /api/appointments`, Min Duration `1s` → **aucune trace**.
  Sans filtre de durée : 8 réservations de Camille entre 240 et 375 ms côté client (curl), entre 25 et 156 ms côté
  serveur (la plus longue est la première, JVM encore froide).
- **Trace** : `be906e49d4b9d8f40ab9ac9a33b4751a` (`POST /api/appointments`, 201, **76,6 ms**)

  ```
  POST /api/appointments                      76.6 ms
    medislot.reservation                      44.3 ms  medislot.slot.id=113 medislot.appointment.id=8
      UserRepository.findByEmail               9.2 ms
      SlotRepository.lockById                  6.0 ms
      AppointmentRepository.existsBySlot…      9.2 ms
      AppointmentRepository.saveAndFlush       5.8 ms
      AuditRepository.save / NotificationRepository.save  7.6 + 4.3 ms
    Transaction.commit                         3.9 ms
    SlotRepository.findById / ProfessionalRepository.findById  5.4 + 5.6 ms  (construction du DTO)
  ```
- **Coupable** : aucun dans ce code. Aucune barre ne dépasse 10 ms et il n'y a aucun span client vers un service
  externe. Dans le kit, la lenteur venait de la **passerelle SMS simulée**, appelée pendant la réservation.
  Ce service n'existe pas ici.
- **Comment la trace l'aurait désigné** : un span client `POST` (`java-http-client`, `server.address=sms-passerelle`)
  long d'environ 1 s, enfant de `medislot.reservation`, donc à l'intérieur de la transaction, et non côté navigateur.
- **Piste (back)** : un appel à un service tiers ne doit pas se faire dans la transaction de réservation. Il faut le
  sortir après le commit (`@TransactionalEventListener(phase=AFTER_COMMIT)` + `@Async`, ou une file), avec un délai
  d'attente court. **Côté front** : `busy` bloque déjà un second envoi. On peut en plus envoyer une `Idempotency-Key`
  à chaque réservation en ligne : le back la gère déjà, mais seule la file hors ligne (`offline/flush.ts`) l'envoie
  aujourd'hui.

## 3. T2 — « Mes rendez-vous sollicite la base bien plus que prévu »

- **Recherche Jaeger** : service `medislot-api`, opération `GET /api/appointments`, puis comparaison de deux traces
  du même compte (Camille), avant et après 8 réservations.
- **Traces et preuve** :

  | Trace | Rendez-vous | Spans | `SELECT` | Durée serveur | Somme SQL |
  |---|---|---|---|---|---|
  | `0b29033514b39ba6f8da97e846a290d4` | 0 | 8 | 2 | 23,3 ms | 2,7 ms |
  | `754d92c7032168d15face49fbcc34cbe` | 8 | **72** | **18** | 85,5 ms | 17,1 ms |

  Spans qui se répètent dans `754d92c7…` : **8 ×** `SlotRepository.findById` → `SELECT medislot.slot`,
  **8 ×** `ProfessionalRepository.findById` → `SELECT medislot.professional`, **16 ×** `Transaction.commit`.
  Il y a donc 2 + 2N requêtes et 2N transactions pour N rendez-vous : un **N+1**.
- **Coupable** : maillon **Spring** (pas la base, qui répond en environ 1 ms par requête) :
  `AppointmentController.list` → `AppointmentController.dto(Appointment)`, appelé une fois par rendez-vous, qui fait
  `slots.findById` puis `professionals.findById` (confirmé dans `backend/src/main/java/fr/medislot/api/AppointmentController.java`, l. 36-37).
- **Pourquoi ça ne se voit presque pas à la durée** : chaque requête est courte (≈ 1 ms) et la base est locale. 85 ms
  pour 8 rendez-vous passe inaperçu dans un filtre `Min Duration`. Le coût grandit avec le nombre de rendez-vous et de
  patients simultanés (connexions, aller-retours réseau vers une base distante). On le voit en **comptant les spans**,
  pas en mesurant la plus longue barre.
- **Piste (back)** : charger créneaux et praticiens en une requête : une projection JPQL
  `select new AppointmentDto(...) from Appointment a join Slot s … join Professional p … where a.patientId = :id`,
  ou `findAllById` sur les deux listes d'identifiants. Objectif vérifiable dans Jaeger : un nombre de spans constant
  quel que soit N.

## 4. T3 — « L'historique de certains rendez-vous affiche "Le service est momentanément indisponible" »

- **Recherche Jaeger** : service `medislot-api`, opération `GET /api/appointments/{id}/history`, Tags `error=true`
  → **aucune trace en erreur**. Les historiques ouverts répondent 200.
- **Trace** : `65d1594b7b9216cc753410c5cd910c1c` (200, 12 spans : `AppointmentRepository.findById`,
  `UserRepository.findByEmail`, `AuditRepository.findByAppointmentIdOrderByCreatedAtAsc`, une seule requête
  `SELECT medislot.audit_event`).
- **Coupable** : aucun dans ce code. Le kit ajoutait des **libellés d'historique** qui échouaient sur certains
  rendez-vous de démonstration. Cette fonction n'existe pas ici (l'historique renvoie `action` et `createdAt`).
- **Comment la chaîne de diagnostic fonctionne maintenant** : une réponse 5xx porte `X-Trace-Id`, et le front
  affiche « Le service est momentanément indisponible. (réf. <32 caractères>) ». Il suffit de coller la référence
  dans **Lookup by Trace ID** : le span serveur est en `error=true` et son événement `exception` donne
  `exception.type`, `exception.message` et la pile d'appels jusqu'à la ligne fautive.
- **Piste** : si le ticket revient, il faut d'abord demander la référence affichée. Le front n'est pas en cause :
  il affiche le message générique des 5xx, ce qui est voulu.

## 5. Le span métier `medislot.reservation`

- `@WithSpan("medislot.reservation")` sur `BookingService.book(String email, Long slotId, UUID key, String abToken)`.
  Les autres surcharges y délèguent, et l'agent instrumente aussi les appels internes.
- `@SpanAttribute("medislot.slot.id")` sur `slotId`, puis `Span.current().setAttribute("medislot.appointment.id", a.id)`
  une fois le rendez-vous enregistré (et dans le cas d'un renvoi idempotent).
- **Pourquoi ces deux attributs** : ce sont deux identifiants techniques numériques. Ils suffisent pour retrouver le
  créneau et le rendez-vous en base, et ne révèlent rien du patient à qui n'a pas accès à la base.
- **Et l'e-mail ?** Il serait « utile » pour retrouver les traces d'un patient. Mais c'est une donnée personnelle,
  et associée à un rendez-vous chez un praticien, elle révèle une information de santé (RGPD, art. 4.15 et 9).
  Une trace est copiée, stockée, partagée et ne se rappelle pas. Jaeger n'a pas d'authentification et n'est pas un
  hébergeur HDS. `email` n'est donc pas annoté : il ne quitte pas le back.
- **Vérifié** : aucun attribut ajouté ne contient de nom, d'e-mail, de motif ni de saisie. Le texte recherché est
  absent des traces (script, point 3, et test Playwright). `db.statement` affiche `email=?`.

## 6. Sorties des tests (non retouchées)

### `npm test` (frontend)

```
 RUN  v4.1.11 C:/Users/ribau/Documents/Travail/Alternance/Ecole/Mastère 2/Coordination front & back/Jour 2/MediSlot_application/frontend

 Test Files  5 passed (5)
      Tests  27 passed (27)
   Start at  09:54:31
   Duration  7.89s (transform 1.01s, setup 0ms, import 4.58s, tests 1.09s, environment 3.78s)
```

Dont `src/tracing.test.ts` (3 tests) : traceparent W3C échantillonné ; le span du navigateur est le parent annoncé
au back ; `url.full` ne garde pas la saisie.

### `bash tests/verifier_traces.sh`

```
1. Nginx relaie /otlp/ vers le collecteur (route du navigateur)
  OK     POST /otlp/v1/traces -> 200
2. Le back trace la requête et l'envoie jusqu'à Jaeger (agent, collecteur, exportateur)
  OK     trace 74595b5f93c413c5214868f27a0b674b : 5 spans medislot-api, dont GET /api/professionals et 2 spans SQL
3. Propagation et nettoyage : le back reprend traceparent, la saisie ne reste pas dans la trace
  OK     span serveur enfant de 2122c547630d595c, X-Trace-Id renvoyé, aucune trace de « verifTraces0dfbe7 » dans les attributs

Bilan : 3 OK, 0 ÉCHEC
```

### `npx playwright test e2e/trace.spec.ts`

```
Running 1 test using 1 worker

  ok 1 e2e\trace.spec.ts:17:1 › une recherche produit une trace front puis back (7.3s)

  1 passed (11.8s)
```

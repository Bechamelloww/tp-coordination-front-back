# Test A/B `confirm_label_v2` : libellé du bouton du formulaire de réservation

## Fiche d'expérience

| Élément | Choix |
|---|---|
| Changement (visible, mineur) | Bouton du formulaire de réservation (TP « parcours hors ligne »). **A** : « Valider ». **B** : « Réserver ce créneau ». Même style, même place, même action (la demande entre dans la file d'envoi). |
| Historique | `confirm_label_v1` testait le bouton de l'ancienne fenêtre de confirmation (« Confirmer le rendez-vous »), puis un mode hors ligne réservé à B. Le TP rend le hors ligne commun à tous et impose « Valider » : nouvelle version, nouvelle clé. Les données v1 sont conservées en base. |
| Hypothèse | « Réserver ce créneau » dit plus clairement ce que fait le clic, donc plus de patients exposés obtiennent une réservation confirmée. |
| Population | Les **10 premiers patients connectés** qui demandent leur affectation. Les suivants voient A et ne sont pas comptés. |
| Unité | Le patient connecté (`app_user.id`). Le bouton testé n'apparaît qu'après connexion : pas besoin de cookie de suivi, et un patient garde sa version sur tous ses appareils. |
| Répartition | Par paires : le premier de chaque paire est tiré par SHA-256(`confirm_label_v2:<id>`), premier octet < 128 → A ; le second reçoit l'autre version. Résultat : 5 A et 5 B. |
| Jeton de variante | La variante circule dans un **JWT HS256 signé par le serveur** (voir plus bas). Le front lit la variante dans le jeton ; exposition et conversion ne comptent que si le serveur valide ce jeton. |
| Exposition | Le formulaire de réservation est affiché avec son bouton. Une seule par patient, la première date est conservée. Hors ligne, elle est gardée et envoyée au retour du réseau (A comme B). |
| Conversion | Réservation enregistrée (`201`) par un patient déjà exposé, y compris une demande validée hors ligne et envoyée plus tard par la file. Comptée une fois par patient, dans la même transaction que la réservation : un `409` ne compte rien, un rejeu de la même clé d'idempotence non plus. |
| Indicateur | `converted / exposed` par version, `null` si aucune exposition. |
| Limite | 10 personnes ne suffisent **pas** à désigner un gagnant (il en faudrait des milliers, cf. cours chap. 2). Ce test vérifie que la chaîne affecter → exposer → convertir fonctionne. |

## Où c'est dans le code

| Partie | Fichier |
|---|---|
| Tables `ab_experiment`, `ab_participant` ; expérience v2 | `V2__ab_testing.sql`, `V4__ab_confirm_label_v2.sql` |
| Affectation, limite de 10, exposition, conversion, métriques | `backend/src/main/java/fr/medislot/experiment/AbService.java` |
| Émission et vérification du jeton | `backend/src/main/java/fr/medislot/experiment/AbTokens.java` |
| Routes `/api/ab/*` | `backend/src/main/java/fr/medislot/experiment/AbController.java` |
| Conversion déclenchée par la réservation | `BookingService.book(…)` publie `AppointmentBooked`, vérifié par `AbService.booked` |
| Variante, libellé, repli A, exposition | `frontend/src/stores/experiment.ts`, `frontend/src/components/BookingForm.vue` |
| Jeton joint à l'envoi de la file | `frontend/src/offline/flush.ts` (en-tête `X-AB-Token`) |
| Tests | `AbTokensTest.java`, `AbExperimentIntegrationTest.java`, `frontend/src/stores/experiment.test.ts` |

## Routes

| Route | Accès | Rôle |
|---|---|---|
| `GET /api/ab/assignment` | connecté | `{experiment, enabled, participating, token}`, `Cache-Control: no-store` ; `token` = `null` hors expérience |
| `POST /api/ab/exposure` | connecté + CSRF + `X-AB-Token` | `204` ; `400` si le jeton est absent, falsifié, expiré ou d'un autre patient |
| `GET /api/ab/metrics` | public | Agrégats par version, aucun identifiant patient |

`POST /api/appointments` accepte l'en-tête `X-AB-Token`, joint par la file d'envoi si la variante a été reçue. Sans jeton valide, la réservation fonctionne mais n'est pas attribuée.

## Le jeton de variante

Format JWT HS256 : `en-tête.charge.signature`, chaque partie en base64url.

```
{"alg":"HS256","typ":"JWT"} . {"sub":"12","experiment":"confirm_label_v2","variant":"B","iat":…,"exp":…} . HMAC-SHA256(secret)
```

1. `GET /api/ab/assignment` : le serveur inscrit le patient (ou retrouve son inscription) et signe un jeton avec le secret `AB_TOKEN_SECRET`.
2. Le front décode la partie centrale et lit `variant` pour choisir le libellé. Il ne vérifie pas la signature (il n'a pas le secret).
3. Le front renvoie le jeton dans l'en-tête `X-AB-Token` pour l'exposition et la réservation. Le jeton n'est jamais rangé dans IndexedDB : la file le lit au moment d'envoyer.
4. Le serveur vérifie, avant de compter : la signature (comparaison à temps constant), l'en-tête exact `HS256` (`alg: none` refusé), l'expiration (24 h), l'expérience, et que `sub` est bien le patient de la session. La mise à jour en base exige aussi que la variante du jeton soit celle enregistrée.

Modifier la variante dans le jeton (par exemple A → B) casse la signature : le serveur refuse. Le jeton n'identifie ni ne connecte personne : la session reste le seul moyen d'authentification.

## Réglages (`compose.yaml`, service `backend`)

- `AB_ENABLED: "false"` : tout le monde voit A, plus aucune exposition ni conversion n'est enregistrée ; les données restent consultables.
- `AB_MAX_PARTICIPANTS` : nombre de participants (10 par défaut).
- `AB_TOKEN_SECRET` : secret de signature des jetons, 32 octets minimum. La valeur par défaut est réservée au laboratoire local.

Appliquer avec `docker compose up -d --build backend`, puis recharger la page.

## Vérifier à la main

1. Se connecter avec `patient@medislot.test`, cliquer sur un créneau : le bouton du formulaire affiche la version du patient.
2. `http://localhost:8088/api/ab/metrics` : `exposed` = 1 dans sa version.
3. Rouvrir le formulaire plusieurs fois : `exposed` ne bouge pas. Valider : `converted` = 1. Réserver un 2e créneau : toujours 1.
4. Créer des comptes depuis l'interface : au 11e, `participating` = `false` et `participants` reste à 10.
5. F12 → Réseau → réponse de `ab/assignment` : la partie centrale de `token`, décodée, contient la variante. Rejouer `ab/exposure` avec un jeton modifié : réponse `400`.

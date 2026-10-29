# QUALITE.md — MediSlot sous SonarQube

TP SonarQube du 29 octobre 2026. Serveur local SonarQube Community Build 26.10.0.133078 (Docker, `127.0.0.1:9000`, base H2).
Projets : `medislot-backend` et `medislot-frontend`. Le jeton d'analyse est resté dans `SONAR_TOKEN` et n'apparaît nulle part dans le dépôt.

Code analysé : ce dépôt (MediSlot de la formation). L'archive `MediSlot_a_defauts.zip` n'était pas disponible sur le poste,
donc les pages ajoutées (Recherche avancée, Mon profil, Messagerie, Admin / Debug, Export CSV) n'existent pas ici.

Environnement :
- Java 25 absent du poste (JDK 17 seulement) : Maven tourne dans `eclipse-temurin:25-jdk`, socket Docker monté pour Testcontainers.
- Front : tests et couverture dans `node:22` (le `&` du chemin « front & back » casse `npx` sous Windows).
- Scanner front : `sonarsource/sonar-scanner-cli` en conteneur, serveur joint par `host.docker.internal:9000`.

## 1 · Les mesures (étapes 2 et 3) — première analyse, Overall Code

| Projet | Lignes | Sécu. | Fiab. | Maint. | Couverture | Dupl. | Porte |
|---|---|---|---|---|---|---|---|
| back (1.0.0, 16 h 33) | 509 | **E** (1 Blocker) | C (1) | A (0) | 64,4 % (lignes 69,5 %, 187 lignes à couvrir) | 0,0 % | Passed, aucune condition évaluée |
| front (1.0, 16 h 38) | 591 | A (0) | **D** (6) | A (4) | 39,0 % (lignes 28,7 %, 327 lignes à couvrir) | 0,0 % | Passed, aucune condition évaluée |

Constats :
- Back : `Tests run: 11, Failures: 0`, `Sensor JaCoCo XML Report Importer`, `ANALYSIS SUCCESSFUL`. Aucun test rouge.
- Front : 24 tests Vitest verts. `Analysing [/usr/src/frontend/coverage/lcov.info]` sans « Could not resolve ».
  Avertissement `Failed to parse ... App.vue`, `BookingForm.vue`, `ProfessionalCard.vue`, `NetworkBanner.vue` :
  `vitest.config.ts` n'a pas le plugin Vue, donc les `.vue` sont absents du lcov. SonarQube les compte quand même, non couverts :
  c'est ce qui fait tomber la couverture (Vitest annonce 39,3 % de lignes sur les seuls `.ts`, SonarQube 28,7 % avec les `.vue`).
- Les deux portes sont vertes alors qu'il y a 8 problèmes ouverts : à la première analyse tout le code est « ancien »,
  la porte « Sonar way » ne juge que le nouveau code, qui est vide.

## 2 · Les problèmes (étapes 4 et 5)

8 problèmes ouverts au départ (2 back, 6 front). Les cinq retenus :

| # | Problème (fichier:ligne) | Règle | Décision et raison | Preuve |
|---|---|---|---|---|
| 1 | `backend/.../config/DemoData.java:15` — mot de passe `MediSlot-demo-2026!` écrit en dur pour les comptes de démo | `java:S6437` · Sécurité · **Blocker** | **Corriger.** Le mot de passe est dans le dépôt et dans l'image Docker, et `DEMO_SEED` vaut `true` par défaut : ces comptes existeraient en production avec un mot de passe public. Il passe en configuration (`DEMO_PASSWORD`), et la démo devient désactivée par défaut. | à remplir |
| 2 | `backend/.../experiment/AbService.java:46` — `int rank=jdbc.queryForObject("select count(*) ...",Integer.class,EXP)` | `java:S2259` · Fiabilité · Medium | **Faux positif.** `select count(*)` renvoie toujours exactement une ligne, jamais `null` (0 si la table est vide) : le déballage `Integer` → `int` ne peut pas lever de `NullPointerException`. La règle ne connaît que la signature `@Nullable` de `queryForObject`. | statut False Positive + commentaire |
| 3 | `frontend/src/App.vue:126` — bouton « Se connecter » sans attribut `type` dans le formulaire de connexion | `typescript:S9011` · Fiabilité · Medium | **Corriger.** Le comportement voulu est bien l'envoi du formulaire, mais il repose sur la valeur par défaut `submit`. On l'écrit (`type="submit"`) : un relecteur voit l'intention, et un futur bouton ajouté dans le formulaire ne sera pas confondu. | à remplir |
| 4 | `frontend/src/App.vue:130, 131, 132, 133` — `id="modal-title"` répété 4 fois après la ligne 119 | `Web:S7930` · Fiabilité High + Maintenabilité High (×4) | **Faux positif.** Les cinq `<h2 id="modal-title">` sont dans des branches `v-if` / `v-else-if` exclusives sur `modalKind` : un seul est rendu à la fois. L'id est requis par `aria-labelledby="modal-title"` de la fenêtre. La règle lit le gabarit Vue comme du HTML statique. | statut False Positive + commentaire (×4) |
| 5 | `frontend/src/stores/experiment.ts:66` — `flushExposure()` appelé dans l'écouteur `online` sans `await`, `.catch` ni `void` | `typescript:S9383` · Fiabilité · Medium | **Accepter.** La promesse ne peut pas être rejetée : tout le travail de `flushExposure` est dans un `try/catch/finally` qui avale l'erreur. Aucun risque de rejet non géré aujourd'hui. À marquer `void flushExposure()` au prochain passage dans ce fichier pour rendre l'intention explicite. | statut Accepted + commentaire |
| + | `frontend/src/App.vue:128` — la fenêtre de connexion affiche `patient@medislot.test` et le mot de passe de démo dans un `<details>` « Compte de démonstration » | non vu par SonarQube (aucune règle) | **Corriger.** Même secret que le n° 1, côté front : corriger le back sans retirer cet affichage ne ferme pas la fuite. Aucune règle ne lit le texte d'un gabarit comme un identifiant. Le même mot de passe apparaît aussi dans `README.md` et `frontend/e2e/booking.spec.ts`. | à remplir |

Problèmes non retenus : aucun autre (les 8 sont couverts par les lignes 1 à 5, la ligne 4 en regroupant 4).

## 3 · La porte (étape 6)

À remplir.

# Convocation PDF — MediSlot

TP « Télécharger sa convocation en PDF » (Coordination front & back, jour 3).
Le kit de départ n'était pas disponible : endpoint, rendu, modèle, appel Vue et tests ont été écrits de zéro dans ce dépôt, avec les noms de fichiers du TP.

| Côté | Fichiers |
|---|---|
| Back | `backend/src/main/java/fr/medislot/convocation/` (`ConvocationController`, `ConvocationService`, `PdfRenderer`, `ConvocationView`, `ConvocationFormats`), `backend/src/main/resources/templates/convocation.html`, `backend/src/main/resources/fonts/` (DejaVu Sans + licence) |
| Front | `frontend/src/convocation.ts`, `frontend/src/components/ConvocationButton.vue`, intégration dans `frontend/src/App.vue` (« Mes rendez-vous ») |
| Tests | `backend/src/test/java/fr/medislot/convocation/` (16 tests), `frontend/src/convocation.test.ts` (15 tests) |
| Contrat | `docs/openapi.yaml`, route `/appointments/{id}/convocation.pdf` |

## §1 · Le contrat

**Requête**

```http
GET /api/appointments/{id}/convocation.pdf            → afficher (inline)
GET /api/appointments/{id}/convocation.pdf?download=true → enregistrer (attachment)
Cookie: JSESSIONID=…              (session ; GET : pas de jeton CSRF)
Accept: application/pdf, application/problem+json
```

**Succès : 200**

| En-tête | Valeur | Pourquoi |
|---|---|---|
| `Content-Type` | `application/pdf` | le front vérifie ce type avant de lire le corps |
| `Content-Disposition` | `inline; filename="convocation-42.pdf"` ou `attachment; filename="convocation-42.pdf"` | afficher ou enregistrer ; le nom ne contient que le numéro |
| `Cache-Control` | `no-store` | document personnel : aucune copie dans un cache (navigateur, proxy) |
| `X-Content-Type-Options` | `nosniff` | le navigateur s'en tient au type déclaré |

**Erreurs** (corps `application/problem+json`, jamais montré au patient : le front affiche sa propre phrase)

| Code | Cas | Phrase affichée (`MESSAGES` dans `convocation.ts`) |
|---|---|---|
| 401 | session absente ou expirée | « Votre session a expiré. Reconnectez-vous pour obtenir votre convocation. » |
| 404 | numéro inconnu **ou** rendez-vous d'un autre patient | « Ce rendez-vous est introuvable sur votre compte. » |
| 409 | rendez-vous annulé | « Ce rendez-vous a été annulé : il n'a plus de convocation. » |
| 500 | rendu du PDF impossible | « La convocation n'a pas pu être préparée. Réessayez dans quelques minutes. » (+ « réf. <trace> » si `X-Trace-Id`) |
| — | réseau coupé | « Pas de connexion : la convocation ne peut pas être téléchargée pour le moment. » |
| 200 non PDF | page HTML renvoyée à la place | « Le serveur n'a pas renvoyé de PDF. Rechargez la page puis réessayez. » |

Pourquoi 404 et pas 403 pour un autre patient : un 403 confirmerait que le numéro existe. Le service refuse **avant** de fabriquer : aucun PDF n'est produit pour un rendez-vous qui n'est pas le sien (vérifié par `ConvocationServiceTest`, `verifyNoInteractions(renderer)`).

## §2 · Où le PDF est fabriqué, et pourquoi

Sur le serveur (Spring : Thymeleaf → OpenHTMLtoPDF), à la demande, sans stockage.
1. Les droits se vérifient là où sont les données : le serveur ne fabrique que la convocation du patient connecté, et un PDF falsifié côté navigateur n'est pas possible.
2. Un seul rendu, identique pour tous : police embarquée, fuseau Europe/Paris, mise en page A4 balisée (PDF/UA), quel que soit le navigateur ou le téléphone.
3. Le front reste léger (pas de bibliothèque PDF dans le bundle) et le même endpoint servira demain un e-mail ou une autre application.

## §3 · Ce qui est imprimé : modèle, police, fuseau

- **Modèle** `templates/convocation.html` : uniquement des `th:text` (`${c.day}`, `${c.time}`, `${c.address + ', ' + c.city}`…). Toutes les valeurs arrivent déjà mises en forme dans `ConvocationView` : aucun calcul dans le modèle.
- **Police** : `PdfRenderer` embarque DejaVu Sans Regular (400) et Bold (700) via `useFont(…)`, en sous-ensemble. Le mode accessible (`usePdfUaAccessibility(true)`) refuse un texte sans police embarquée ; avec elle, « Hélène Durand » s'imprime avec ses accents (vérifié par extraction PDFBox dans `ConvocationPdfTest`).
- **Bug du fuseau, en une phrase** : le créneau est un `Instant` UTC, et un `DateTimeFormatter` sans `withZone(Europe/Paris)` imprime l'heure UTC, soit « 8 h 30 » en hiver et « 7 h 30 » en été pour un rendez-vous à 9 h 30 à Paris (voire la veille près de minuit) ; correction : `.withZone(ConvocationFormats.PARIS)` sur les formateurs du jour et de l'heure.
- Tests « heure de Paris » : 08:30Z en janvier → « 9 h 30 », 07:30Z en juillet → « 9 h 30 », 23:30Z le 11 juillet → « lundi 12 juillet 2027 ».
- PDF d'exemple : `backend/target/convocation-exemple.pdf` (produit par le test) : « Hélène Durand », « mardi 12 janvier 2027 », « 9 h 30 », une page.

Côté front (rôle inversé de l'étape 3) :
- `openConvocation` ouvre l'onglet **pendant le clic, avant tout `await`** (sinon le bloqueur de fenêtres le refuse), y affiche l'URL `blob:`, puis la révoque après 60 s (`browser.later`) ; en cas d'erreur, l'onglet est refermé : jamais d'onglet vide. Si la fenêtre est bloquée, le fichier est enregistré à la place.
- `saveConvocation` demande `download=true` et enregistre sous le nom lu dans `Content-Disposition`.
- `ConvocationButton.vue` : « Préparation… » + `aria-busy` pendant l'attente (boutons `aria-disabled`, double clic ignoré), phrase d'erreur dans un `role="alert"`. Sur 404/409, la liste « Mes rendez-vous » est rechargée. Un rendez-vous annulé affiche « Plus de convocation : rendez-vous annulé. » à la place des boutons.
- Le test « refuse une réponse 200 qui n'est pas un PDF » imite le repli SPA de Nginx (`try_files … /index.html`) ou de Vite quand `/api` n'est pas relayé : la page d'accueil revient en 200 `text/html`, et sans ce contrôle on l'enregistrerait sous `convocation-42.pdf`.

## §4 · Preuves sur l'application qui tourne

Application complète : `docker compose -p medislot-otel up -d --build backend frontend` (projet Compose déjà en place pour le TP OpenTelemetry), puis http://localhost:8088.
Session obtenue comme le front : `GET /api/csrf` → `POST /api/login` (formulaire `username`/`password`, jeton CSRF en en-tête), cookie gardé dans un fichier `-c/-b`. Le mot de passe vient de `DEMO_PASSWORD` dans `.env` et n'est jamais affiché. Rendez-vous réservé pour la preuve : n° 12, créneau `2026-11-17T08:00:00Z`.

**1. Le propriétaire : 200, les quatre en-têtes du contrat**

```text
$ curl -sS -D - -o convocation-12.pdf -b patient.jar http://localhost:8088/api/appointments/12/convocation.pdf
HTTP/1.1 200
Content-Type: application/pdf
Content-Length: 24999
X-Trace-Id: e0db1d7bc118d7991a26aa2c4cf7a419
Content-Disposition: inline; filename="convocation-12.pdf"
Cache-Control: no-store
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
X-Frame-Options: DENY

$ head -c 8 convocation-12.pdf
%PDF-1.6

$ curl -sS -D - -o /dev/null -b patient.jar ".../appointments/12/convocation.pdf?download=true"
HTTP/1.1 200
Content-Disposition: attachment; filename="convocation-12.pdf"
```

Le PDF reçu imprime « Camille Dupont », « mardi 17 novembre 2026 », **« 9 h 00 »** (08:00Z = 9 h 00 à Paris en hiver), « Dr Sarah Martin », « Médecine générale », « 18 avenue de la République, Paris », « RDV-000012 ». La liste « Mes rendez-vous » affiche le même créneau : `timeLabel` (Intl, `timeZone: 'Europe/Paris'`) donne « 09:00 ».

**2. Le second patient, même numéro : 404 (et pas 403)**

```text
$ curl -sS -D - -b autre.jar http://localhost:8088/api/appointments/12/convocation.pdf
HTTP/1.1 404
Content-Type: application/problem+json
X-Trace-Id: 2cd9b19e77e140c9e5992a4ce4c2e1a8
Cache-Control: no-cache, no-store, max-age=0, must-revalidate

{"detail":"Rendez-vous introuvable.","instance":"/api/appointments/12/convocation.pdf","status":404,"title":"Not Found"}
```

**3. Après annulation (`POST /api/appointments/12/cancel` → `CANCELLED`) : 409**

```text
$ curl -sS -D - -b patient.jar http://localhost:8088/api/appointments/12/convocation.pdf
HTTP/1.1 409
Content-Type: application/problem+json
X-Trace-Id: 9e3c92aa3249d5b17236bf96ee3d1604

{"detail":"Ce rendez-vous est annulé : il n’a plus de convocation.","instance":"/api/appointments/12/convocation.pdf","status":409,"title":"Conflict"}
```

**4. Sans session : 401**

```text
$ curl -sS -D - http://localhost:8088/api/appointments/12/convocation.pdf
HTTP/1.1 401
Content-Type: application/problem+json;charset=ISO-8859-1

{"title":"Connexion requise","status":401}
```

Codes relevés : **200, 404, 409** (et 401). Aucun PDF n'est servi en dehors du cas 200 : les erreurs sont du `problem+json`, que le front transforme en phrase.

**Parcours dans le navigateur**

Session `patient@medislot.test`, « Mes rendez-vous », rendez-vous n° 13 (créneau `2026-11-19T08:00:00Z`, affiché « jeudi 19 novembre à 09:00 »).

- Chaque rendez-vous confirmé montre « Ma convocation (PDF) » et « Enregistrer » ; chaque rendez-vous annulé montre « Plus de convocation : rendez-vous annulé. » (arbre d'accessibilité : les boutons portent en texte masqué « du rendez-vous du jeudi 19 novembre à 09:00 avec Dr Amel Benali »).
- Onglet Réseau, clic sur « Ma convocation (PDF) » puis « Enregistrer » :

```text
GET http://localhost:8088/api/appointments/13/convocation.pdf               → 200
GET http://localhost:8088/api/appointments/13/convocation.pdf?download=true → 200
```

- Fichier enregistré : `convocation-13.pdf`, le nom lu dans `Content-Disposition` (relevé en instrumentant `HTMLAnchorElement.click` : `download = "convocation-13.pdf"`, `href = blob:http://localhost:8088/…`).
- Le volet navigateur utilisé pour la démonstration bloque les fenêtres (`window.open` renvoie `null`) : `openConvocation` a alors pris le chemin de secours prévu et enregistré le PDF au lieu de laisser un onglet vide. Dans un navigateur ordinaire, l'onglet s'ouvre pendant le clic puis reçoit l'URL `blob:` (chemin couvert par le test « ouvre l'onglet avant la réponse, y affiche le PDF puis libère l'URL »).
- Même heure partout : la liste affiche 09:00 pour `08:00:00Z`, et le PDF du rendez-vous n° 12 (même heure UTC, preuve 1) imprime « 9 h 00 ».

## §5 · Défi : le récapitulatif annuel (contrat asynchrone, sans code)

Cent rendez-vous, plusieurs secondes de rendu : on ne bloque pas une requête HTTP (délai de Nginx, fil de Tomcat occupé, double clic qui relance tout). On sépare **commander**, **suivre**, **télécharger**.

| Étape | Requête | Réponses |
|---|---|---|
| Commander | `POST /api/recaps` · corps `{"year":2026}` · en-têtes `X-XSRF-TOKEN` (CSRF) et `Idempotency-Key: <uuid>` | **202 Accepted** + `Location: /api/recaps/{jobId}` + `Retry-After: 2` + corps `{"id","status":"PENDING"}` ; même clé rejouée → même tâche (202) ; 400 année invalide ; 401 ; 403 CSRF manquant ; 429 + `Retry-After` si une tâche est déjà en cours pour ce patient |
| Suivre | `GET /api/recaps/{jobId}` | **200** `{"status":"PENDING"\|"RUNNING"\|"READY"\|"FAILED","progress":40}` + `Retry-After` tant que non prêt ; quand prêt : **303 See Other** (ou 200 avec `"download":"/api/recaps/{jobId}/file"`) ; **404** tâche inconnue ou d'un autre patient ; **410 Gone** fichier expiré |
| Télécharger | `GET /api/recaps/{jobId}/file[?download=true]` | **200** `application/pdf`, `Content-Disposition: attachment; filename="recapitulatif-2026.pdf"` (année seule), `Cache-Control: no-store`, `X-Content-Type-Options: nosniff` ; **409** pas encore prêt ; **404** autre patient ; **410** expiré |

**Qui protège quoi**
- **CSRF** : seul `POST` (qui crée une tâche et consomme du calcul) exige le jeton ; les `GET` ne modifient rien.
- **Propriétaire** : la tâche enregistre `patientId` ; chaque `GET` filtre sur le patient connecté → 404 sinon (comme la convocation). L'identifiant de tâche est un UUID aléatoire, mais ce n'est pas lui qui protège : c'est la session.
- **Durée de vie** : fichier conservé 24 h maximum (puis 410), supprimé par une tâche planifiée ; stocké hors de la racine web, jamais dans une URL publique ; rien dans les journaux ni les traces que l'id technique.

**Côté front**
- Sondage : respecter `Retry-After` (2 s), puis espacer (2, 4, 8 s, plafond 10 s) ; suspendre quand l'onglet est caché (`visibilitychange`) ; un seul sondage par tâche (bouton désactivé, `aria-busy`, message « Préparation de votre récapitulatif… » dans une région `role="status"`).
- Abandon : après ~2 minutes ou sur `FAILED` / 404 / 410 → phrase claire et bouton « Relancer » (nouvelle commande, nouvelle clé) ; hors ligne → on arrête et on reprend au retour du réseau ; en quittant la page on stoppe le sondage (`AbortController`), la tâche continue côté serveur et reste récupérable par son `Location` pendant 24 h.

## §6 · Vérifier et rendre

**Tests** (JDK 25 absent du poste : le back est testé dans l'image Maven du Dockerfile ; sous Windows, le `&` du chemin casse `npx`, d'où `node node_modules/...`) :

```sh
# back : 27 tests, dont 16 Convocation* (8 service, 4 PDF, 4 contrôleur) ; socket Docker monté pour les tests Testcontainers
docker run --rm -v "$(pwd -W)/backend:/src" -v medislot-m2:/root/.m2 -v //var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /src maven:3.9-eclipse-temurin-25 mvn -B test
# front : 48 tests, dont 15 dans convocation.test.ts (Contrat 3, Récupération du PDF 8, Affichage et mémoire 4)
cd frontend && node node_modules/vitest/vitest.mjs run && node node_modules/vue-tsc/bin/vue-tsc.js --noEmit
```

```text
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in fr.medislot.convocation.ConvocationServiceTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in fr.medislot.convocation.ConvocationPdfTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in fr.medislot.convocation.ConvocationControllerTest
Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Test Files  7 passed (7)
     Tests  48 passed (48)
vue-tsc --noEmit : aucune erreur
```

**Auto-vérification**

- [x] Le nom de fichier ne contient ni nom, ni spécialité, ni date : `convocation-<id>.pdf` (`leNomDeFichierNeContientQueLeNumero`, en-têtes du §4).
- [x] Un autre patient reçoit 404, pas 403, et aucun PDF n'est fabriqué pour lui (`unRendezVousDUnAutrePatientRepond404SansFabriquerDePdf` + §4).
- [x] L'heure imprimée est juste en hiver comme en été (`heureDeParisEnHiver`, `heureDeParisEnEte`).
- [x] Le PDF contient « Hélène Durand » au test, avec sa police embarquée (`leTexteImprimeEstJusteAccentsCompris`, `lesPolicesSontEmbarquees`).
- [x] Une erreur affiche une phrase, jamais du JSON ni un onglet vide (`MESSAGES`, onglet refermé sur erreur, refus d'un 200 non PDF).
- [x] Chaque affirmation du §4 a sa commande ou sa sortie collée.

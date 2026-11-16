# Synchronisation Google Agenda

Le patient connecte son compte Google une seule fois depuis « Mes rendez-vous ». MediSlot crée alors un agenda secondaire « MediSlot » dans son Google Agenda. Ensuite, chaque rendez-vous y est répliqué automatiquement :

| Action dans MediSlot | Effet dans Google Agenda |
|---|---|
| Réserver | L'événement est créé |
| Déplacer | L'horaire de l'événement est mis à jour |
| Annuler | L'événement est supprimé |
| Déconnecter Google Agenda | L'agenda « MediSlot » est supprimé et l'accès est révoqué |

## Ce qui est envoyé à Google

- **Titre** : « Rendez-vous MediSlot ». La spécialité n'apparaît jamais, ni dans le titre ni dans les notifications.
- **Lieu** : adresse et ville du cabinet.
- **Description** : nom du praticien et référence `RDV-xxxxxx`.
- **Horaire** : début et fin du créneau, en heure de Paris.
- **Visibilité** : privée.

Rien d'autre n'est transmis : ni motif, ni téléphone, ni adresse e-mail.

## Sécurité

- **Portée minimale `calendar.app.created`** : MediSlot ne voit et ne modifie que l'agenda qu'il a créé. Le reste de l'agenda du patient lui est inaccessible.
- **Code d'autorisation avec PKCE (S256) et un `state` aléatoire** :
  - Les deux sont liés à la session du patient.
  - Le `state` est à usage unique et comparé en temps constant.
- **Démarrage de la liaison par `POST /api/google/authorize`**, protégé par le jeton CSRF : aucun site tiers ne peut lancer une liaison.
- **Jeton de rafraîchissement chiffré en AES-256-GCM** :
  - La clé vient de `GOOGLE_TOKEN_KEY`.
  - Le nonce est aléatoire.
  - L'identifiant du patient sert de donnée associée : un jeton chiffré recopié sur un autre compte est refusé.
- **Jeton d'accès jamais stocké** : il est redemandé à chaque synchronisation.
- **Aucun jeton n'est envoyé au navigateur ni écrit dans les journaux.** Les erreurs ne journalisent que le numéro du rendez-vous et le code HTTP.
- **Accès retiré par le patient côté Google** : Google répond `invalid_grant`, la liaison passe à « À reconnecter » et la carte propose de se reconnecter.
- **Synchronisation après le commit, en asynchrone** : une panne Google ne bloque ni ne ralentit une réservation.

## Configuration du projet Google Cloud

Ces étapes se font une seule fois, sur https://console.cloud.google.com, dans ton projet existant.

### 1. Activer l'API

« API et services » → « Bibliothèque » → rechercher **Google Calendar API** → **Activer**.

### 2. Configurer l'écran de consentement

« API et services » → « Écran de consentement OAuth » (ou « Google Auth Platform »).

1. **Type d'utilisateur** : Externe.
2. **Informations sur l'application** :
   - Nom : MediSlot.
   - E-mail d'assistance et e-mail du développeur : le tien.
3. **Accès aux données** (« Champs d'application ») : ajouter `https://www.googleapis.com/auth/calendar.app.created`.
4. **Audience** :
   - Laisser l'application **en test**.
   - Ajouter ton adresse Gmail dans **Utilisateurs test**. Tant que l'application est en test, seuls ces comptes peuvent se connecter.

### 3. Créer l'identifiant OAuth

« API et services » → « Identifiants » (ou « Clients ») → **Créer des identifiants** → **ID client OAuth**.

1. **Type d'application** : Application Web.
2. **Nom** : MediSlot local.
3. **URI de redirection autorisés** : `http://localhost:8088/api/google/callback`.
4. Récupérer l'**ID client** et le **code secret du client**.

### 4. Renseigner `.env`

Générer la clé de chiffrement des jetons :

```bash
openssl rand -base64 32
```

Puis compléter `.env` (non versionné) :

```
GOOGLE_CLIENT_ID=xxxxxxxx.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=xxxxxxxx
GOOGLE_TOKEN_KEY=<résultat de openssl>
```

Relancer ensuite :

```bash
docker compose up --build
```

Si `GOOGLE_CLIENT_ID` est vide, la fonction est désactivée et la carte n'apparaît pas. Si `GOOGLE_CLIENT_ID` est défini mais que `GOOGLE_TOKEN_KEY` est absente ou ne fait pas 32 octets, le backend refuse de démarrer.

Changer `GOOGLE_TOKEN_KEY` rend illisibles les jetons déjà stockés : chaque patient devra reconnecter Google Agenda.

## Routes

| Méthode | Route | Rôle |
|---|---|---|
| GET | `/api/google/status` | État : `enabled`, puis `NON_CONNECTE`, `CONNECTE` ou `A_RECONNECTER` |
| POST | `/api/google/authorize` | Renvoie l'adresse de consentement Google |
| GET | `/api/google/callback` | Retour de Google, puis redirection vers `/?google=connecte`, `/?google=refuse` ou `/?google=erreur` |
| DELETE | `/api/google/link` | Supprime l'agenda MediSlot, révoque le jeton et efface la liaison |

## Vérifier

1. Se connecter comme patient sur http://localhost:8088, ouvrir « Mes rendez-vous », puis cliquer sur « Connecter Google Agenda ».
2. Sur l'écran Google, vérifier que l'accès demandé se limite aux agendas créés par l'application.
3. Réserver, déplacer, puis annuler un rendez-vous, et observer l'agenda « MediSlot » dans Google Agenda.
4. Vérifier que le jeton n'est pas lisible en base :

```bash
docker compose exec db psql -U medislot -c "select user_id, status, encode(refresh_token, 'hex') from google_calendar_link"
```

5. Cliquer sur « Déconnecter » : MediSlot doit disparaître de https://myaccount.google.com/permissions.

## Limites

- **Pas de reprise automatique** : un événement qui échoue (Google indisponible) n'est pas rejoué. Le prochain changement du rendez-vous, ou une reconnexion, le resynchronise.
- **Verrou propre au processus** : le verrou qui ordonne les synchronisations d'un même rendez-vous ne protège qu'une seule instance du backend.
- **Pas de synchronisation inverse** : une modification faite dans Google Agenda n'est pas reportée dans MediSlot.

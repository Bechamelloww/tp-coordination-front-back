# Synchronisation Google Agenda

Statut : implémenté.

## Décision

Le patient autorise MediSlot une seule fois par OAuth2, avec un code d'autorisation, PKCE et un `state` lié à la session. Le backend conserve le jeton de rafraîchissement, chiffré en AES-256-GCM. Après chaque réservation, déplacement ou annulation, il réplique le rendez-vous dans un agenda secondaire « MediSlot ».

- **Portée `calendar.app.created`, la plus étroite** : elle suffit à créer un agenda et à gérer ses propres événements. Les autres agendas du patient restent hors de portée.
- **Flux écrit avec `RestClient`, sans `spring-boot-starter-oauth2-client`** :
  - le `formLogin` par session reste inchangé ;
  - le stockage des jetons reste sous notre contrôle (chiffrement, effacement).
- **Identifiant d'événement déterministe `medislotrdv{id}`** : il rend la création idempotente, sans table de correspondance.
- **Synchronisation après le commit, en asynchrone** (`@TransactionalEventListener` + `@Async`) : la réservation ne dépend jamais de la disponibilité de Google.
- **Données minimisées** : le titre est neutre, et l'événement ne contient ni spécialité, ni motif, ni coordonnées.

## Conséquences et alternatives

- **Le serveur détient un secret par patient.** Une fuite de la base seule ne suffit pas à l'exploiter, mais la clé `GOOGLE_TOKEN_KEY` devient un secret critique. La faire tourner impose une reconnexion des patients.
- **Pas de file d'attente** : un échec n'est pas rejoué. Une outbox avec reprise serait l'étape suivante, à envisager avant plusieurs instances.
- **Alternative écartée : le simple lien « Ajouter à Google Agenda »**. Il ne demande aucun secret, mais il n'est pas automatique et ne suit ni les déplacements ni les annulations.
- **Alternative écartée : un fichier ICS**. Il ne demande aucun compte tiers, mais il n'offre aucune mise à jour.
- **Données de santé** : un rendez-vous médical confié à un tiers relève des données de santé. En exploitation réelle, ce choix demande une analyse d'impact et l'information explicite du patient.

## Vérification

- Exécuter `GoogleCalendarIntegrationTest` (faux serveur Google, PostgreSQL réel) et les tests unitaires du paquet `fr.medislot.calendar`.
- Suivre ensuite le parcours manuel décrit dans `GOOGLE_AGENDA.md`.

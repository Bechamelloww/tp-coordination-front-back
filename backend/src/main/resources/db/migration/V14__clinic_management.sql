-- Le master modifie et supprime des cabinets depuis l'application (PUT / DELETE /api/master/clinics/{slug}).
-- Suppression = archivage : deleted_at est posé, le cabinet disparaît partout, mais ses rendez-vous passés et son
-- historique restent en base (clés étrangères intactes). L'identifiant reste pris : les anciens liens ne mènent à aucun autre cabinet.
ALTER TABLE clinic ADD COLUMN deleted_at TIMESTAMPTZ;
-- Le rôle applicatif ne peut changer que ces colonnes ; slug et id restent figés, et DELETE reste interdit.
GRANT UPDATE (name, city, address, color, deleted_at) ON clinic TO medislot_app;

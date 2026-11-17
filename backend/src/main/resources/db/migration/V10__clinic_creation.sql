-- Le master crée des cabinets depuis l'application (POST /api/master/clinics) :
-- le rôle applicatif peut insérer dans clinic, mais toujours pas modifier ni supprimer un cabinet.
GRANT INSERT ON clinic TO medislot_app;
GRANT USAGE, SELECT ON SEQUENCE clinic_id_seq TO medislot_app;

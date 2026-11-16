-- Multi-cabinet : clinic_id sur les tables propres (professional, slot, appointment, audit_event, notification, booking_key).
-- app_user, ab_* et google_calendar_link restent partagées.

CREATE TABLE clinic (
 id BIGSERIAL PRIMARY KEY,
 slug VARCHAR(40) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
 name VARCHAR(120) NOT NULL,
 city VARCHAR(80) NOT NULL,
 color VARCHAR(7) NOT NULL CHECK (color ~ '^#[0-9a-f]{6}$')
);
INSERT INTO clinic(slug, name, city, color) VALUES
 ('cabinet-a', 'Cabinet des Tilleuls', 'Paris', '#1d4ed8'),
 ('cabinet-b', 'Maison de santé du Canal', 'Lyon', '#0f766e');

CREATE TABLE membership (
 user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 clinic_id BIGINT NOT NULL REFERENCES clinic(id),
 PRIMARY KEY (user_id, clinic_id)
);
-- comptes existants : cabinet-a
INSERT INTO membership(user_id, clinic_id) SELECT u.id, c.id FROM app_user u, clinic c WHERE c.slug = 'cabinet-a';

-- (clinic_id, id) unique : cible des clés étrangères composites
ALTER TABLE professional ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
UPDATE professional SET clinic_id = (SELECT id FROM clinic WHERE slug = 'cabinet-a');
ALTER TABLE professional ALTER COLUMN clinic_id SET NOT NULL, ADD CONSTRAINT professional_clinic_id_uq UNIQUE (clinic_id, id);

ALTER TABLE slot ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
UPDATE slot s SET clinic_id = p.clinic_id FROM professional p WHERE p.id = s.professional_id;
ALTER TABLE slot ALTER COLUMN clinic_id SET NOT NULL, ADD CONSTRAINT slot_clinic_id_uq UNIQUE (clinic_id, id),
 ADD CONSTRAINT slot_professional_same_clinic FOREIGN KEY (clinic_id, professional_id) REFERENCES professional(clinic_id, id);

ALTER TABLE appointment ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
UPDATE appointment a SET clinic_id = s.clinic_id FROM slot s WHERE s.id = a.slot_id;
ALTER TABLE appointment ALTER COLUMN clinic_id SET NOT NULL, ADD CONSTRAINT appointment_clinic_id_uq UNIQUE (clinic_id, id),
 ADD CONSTRAINT appointment_slot_same_clinic FOREIGN KEY (clinic_id, slot_id) REFERENCES slot(clinic_id, id);

ALTER TABLE audit_event ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
UPDATE audit_event e SET clinic_id = a.clinic_id FROM appointment a WHERE a.id = e.appointment_id;
ALTER TABLE audit_event ALTER COLUMN clinic_id SET NOT NULL,
 ADD CONSTRAINT audit_appointment_same_clinic FOREIGN KEY (clinic_id, appointment_id) REFERENCES appointment(clinic_id, id);

ALTER TABLE booking_key ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
UPDATE booking_key k SET clinic_id = s.clinic_id FROM slot s WHERE s.id = k.slot_id;
ALTER TABLE booking_key ALTER COLUMN clinic_id SET NOT NULL,
 ADD CONSTRAINT booking_key_slot_same_clinic FOREIGN KEY (clinic_id, slot_id) REFERENCES slot(clinic_id, id),
 ADD CONSTRAINT booking_key_appointment_same_clinic FOREIGN KEY (clinic_id, appointment_id) REFERENCES appointment(clinic_id, id);

-- pas de lien avec un créneau : les existantes passent en cabinet-a
ALTER TABLE notification ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
UPDATE notification SET clinic_id = (SELECT id FROM clinic WHERE slug = 'cabinet-a');
ALTER TABLE notification ALTER COLUMN clinic_id SET NOT NULL;

-- Rôle de l'application, soumis à la RLS (V8). Sans connexion : le mot de passe est posé hors migration.
DO $$ BEGIN
 IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'medislot_app') THEN CREATE ROLE medislot_app NOLOGIN NOSUPERUSER NOBYPASSRLS; END IF;
END $$;
GRANT USAGE ON SCHEMA public TO medislot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO medislot_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO medislot_app;
REVOKE ALL ON flyway_schema_history FROM medislot_app;
-- cabinets gérés par migration, lecture seule pour l'application
REVOKE INSERT, UPDATE, DELETE ON clinic FROM medislot_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO medislot_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO medislot_app;

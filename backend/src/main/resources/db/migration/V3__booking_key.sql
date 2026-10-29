-- Clé d'idempotence : un patient, une clé, un créneau, un rendez-vous.
-- Deux envois de la même demande rendent le même rendez-vous. Clés purgées après 7 jours.
CREATE TABLE booking_key (
 patient_id BIGINT NOT NULL REFERENCES app_user(id),
 idem_key UUID NOT NULL,
 slot_id BIGINT NOT NULL REFERENCES slot(id),
 appointment_id BIGINT NOT NULL REFERENCES appointment(id),
 created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY (patient_id, idem_key)
);
CREATE INDEX booking_key_created_idx ON booking_key(created_at);

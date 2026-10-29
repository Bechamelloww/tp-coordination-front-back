CREATE TABLE ab_experiment (experiment_key VARCHAR(60) PRIMARY KEY, description VARCHAR(240) NOT NULL);
INSERT INTO ab_experiment VALUES ('confirm_label_v1', 'Libellé du bouton de confirmation : A « Confirmer le rendez-vous », B « Réserver ce créneau ».');
CREATE TABLE ab_participant (
 experiment VARCHAR(60) NOT NULL REFERENCES ab_experiment(experiment_key),
 user_id BIGINT NOT NULL REFERENCES app_user(id),
 enrollment_rank INTEGER NOT NULL CHECK(enrollment_rank >= 0),
 variant CHAR(1) NOT NULL CHECK(variant IN ('A','B')),
 assigned_at TIMESTAMPTZ NOT NULL,
 exposed_at TIMESTAMPTZ,
 converted_at TIMESTAMPTZ,
 appointment_id BIGINT REFERENCES appointment(id),
 PRIMARY KEY(experiment, user_id),
 UNIQUE(experiment, enrollment_rank),
 CHECK(converted_at IS NULL OR exposed_at IS NOT NULL)
);

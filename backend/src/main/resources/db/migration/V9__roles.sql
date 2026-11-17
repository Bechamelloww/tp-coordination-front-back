-- Rôles : MASTER (compte global, tous les cabinets) sur app_user ; ADMIN ou MEMBER par cabinet sur membership.
ALTER TABLE app_user ADD CONSTRAINT app_user_role_check CHECK (role IN ('PATIENT', 'MASTER'));

ALTER TABLE membership ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('MEMBER', 'ADMIN'));
CREATE INDEX membership_clinic_role_idx ON membership (clinic_id, role);

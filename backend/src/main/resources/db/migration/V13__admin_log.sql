-- Journal des actions d'administration, cabinet par cabinet (création du cabinet, ajout d'un membre…).
-- Lu par les admins du cabinet (GET /api/admin/logs) et par le master (GET /api/master/logs), jamais en public.
CREATE TABLE admin_log (
 id BIGSERIAL PRIMARY KEY,
 clinic_id BIGINT NOT NULL REFERENCES clinic(id),
 -- e-mail copié au moment de l'action, sans clé étrangère : le journal ne bouge pas si le compte disparaît
 actor_email VARCHAR(254) NOT NULL,
 action VARCHAR(40) NOT NULL,
 detail VARCHAR(300) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX admin_log_clinic_recent_idx ON admin_log (clinic_id, id DESC);

-- Ajout seul : une ligne du journal ne se modifie ni ne s'efface.
REVOKE UPDATE, DELETE, TRUNCATE ON admin_log FROM medislot_app;
CREATE FUNCTION admin_log_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'admin_log est en ajout seul'; END $$;
CREATE TRIGGER admin_log_append_only BEFORE UPDATE OR DELETE ON admin_log
 FOR EACH ROW EXECUTE FUNCTION admin_log_append_only();

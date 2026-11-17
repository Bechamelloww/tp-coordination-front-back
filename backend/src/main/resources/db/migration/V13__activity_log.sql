CREATE TABLE activity_log (
 id BIGSERIAL PRIMARY KEY,
 clinic_id BIGINT REFERENCES clinic(id),
 actor_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
 action VARCHAR(40) NOT NULL,
 detail VARCHAR(300),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX activity_log_recent_idx ON activity_log (id DESC);
CREATE INDEX activity_log_clinic_idx ON activity_log (clinic_id, id DESC);
CREATE INDEX activity_log_created_idx ON activity_log (created_at);

INSERT INTO activity_log (clinic_id, actor_id, action, detail, created_at)
SELECT e.clinic_id, e.actor_id, 'APPOINTMENT_' || e.action, p.name, e.created_at
FROM audit_event e
JOIN appointment a ON a.id = e.appointment_id
JOIN slot s ON s.id = a.slot_id
JOIN professional p ON p.id = s.professional_id
ORDER BY e.created_at, e.id;

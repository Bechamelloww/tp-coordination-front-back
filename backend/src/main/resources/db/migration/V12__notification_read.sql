ALTER TABLE notification ADD COLUMN read_at TIMESTAMPTZ;
UPDATE notification SET read_at = created_at;
CREATE INDEX notification_patient_unread_idx ON notification (patient_id) WHERE read_at IS NULL;

-- Adresse postale du cabinet, saisie à la création. Facultative en base : les cabinets existants n'en ont pas.
ALTER TABLE clinic ADD COLUMN address VARCHAR(200);

-- Position du cabinet (WGS 84, comme OpenStreetMap). Facultative : un praticien sans position n'a pas de carte.
ALTER TABLE professional ADD COLUMN latitude DOUBLE PRECISION, ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE professional ADD CONSTRAINT professional_position_valid CHECK (
  (latitude IS NULL AND longitude IS NULL)
  OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180));

-- Praticiens de démonstration déjà créés (bases existantes). Géocodage Nominatim (OpenStreetMap) du 16/11/2026 ;
-- positions approchées à la rue pour les adresses que Nominatim ne résout pas au numéro. Données fictives.
UPDATE professional SET latitude=48.866400, longitude=2.370000 WHERE address='18 avenue de la République' AND city='Paris';
UPDATE professional SET latitude=48.851300, longitude=2.330500 WHERE address='42 rue de Rennes' AND city='Paris';
UPDATE professional SET latitude=45.756800, longitude=4.831900 WHERE address='6 rue de la Charité' AND city='Lyon';
UPDATE professional SET latitude=45.763688, longitude=4.844596 WHERE address='21 cours Lafayette' AND city='Lyon';
UPDATE professional SET latitude=48.849564, longitude=2.354708 WHERE address='12 boulevard Saint-Germain' AND city='Paris';
UPDATE professional SET latitude=44.845525, longitude=-0.577436 WHERE address='8 cours de Verdun' AND city='Bordeaux';

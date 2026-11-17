#!/usr/bin/env bash
# Comptes de démo, tous avec le même mot de passe (MEDISLOT_PASSWORD, sinon motdepasse-demo).
set -euo pipefail
cd "$(dirname "$0")"

export MEDISLOT_PASSWORD=${MEDISLOT_PASSWORD:-motdepasse-demo}

./add-user.sh directeur@medislot.test "Directeur MediSlot" master
./add-user.sh chef.paris@medislot.test "Sophie Laurent" admin cabinet-a
./add-user.sh chef.lyon@medislot.test "Karim Benali" admin cabinet-b
./add-user.sh julie@medislot.test "Julie Moreau" patient cabinet-a
./add-user.sh thomas@medislot.test "Thomas Petit" patient cabinet-b
./add-user.sh lea@medislot.test "Léa Garnier" patient cabinet-a cabinet-b

echo
./add-user.sh --list

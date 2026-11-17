#!/usr/bin/env bash


# export MEDISLOT_PASSWORD='motdepasse-demo' && \
# scripts/add-user.sh directeur@medislot.test "Directeur MediSlot" master && \
# scripts/add-user.sh chef.paris@medislot.test "Sophie Laurent" admin cabinet-a && \
# scripts/add-user.sh chef.lyon@medislot.test "Karim Benali" admin cabinet-b && \
# scripts/add-user.sh julie@medislot.test "Julie Moreau" patient cabinet-a && \
# scripts/add-user.sh thomas@medislot.test "Thomas Petit" patient cabinet-b && \
# scripts/add-user.sh lea@medislot.test "Léa Garnier" patient cabinet-a cabinet-b && \
# unset MEDISLOT_PASSWORD && \
# scripts/add-user.sh --list


set -euo pipefail
cd "$(dirname "$0")/.."

PSQL=${PSQL:-docker compose exec -T db psql -U medislot -d medislot}
psql_() { $PSQL -v ON_ERROR_STOP=1 -q "$@"; }

usage() {
  cat >&2 <<'TXT'
scripts/add-user.sh <email> "<nom>" patient|admin cabinet-a [cabinet-b ...]
scripts/add-user.sh <email> "<nom>" master
scripts/add-user.sh --list
Mot de passe : MEDISLOT_PASSWORD ou saisie au clavier. Hors docker : PSQL="psql -h localhost -p 55432 -U medislot medislot"
TXT
  exit 1
}

if [[ ${1:-} == "--list" ]]; then
  psql_ -c "select u.email, u.name, u.role as compte, coalesce(string_agg(c.slug || ':' || m.role, ', ' order by c.slug), '-') as cabinets
    from app_user u left join membership m on m.user_id = u.id left join clinic c on c.id = m.clinic_id
    group by u.id order by u.role = 'MASTER' desc, u.email"
  exit 0
fi

[[ $# -ge 3 ]] || usage
email=$(tr '[:upper:]' '[:lower:]' <<< "$1"); name=$2; role=$3; shift 3
clinics=("$@")

[[ $email =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ ]] || { echo "E-mail invalide : $email" >&2; exit 1; }
case $role in
  patient) user_role=PATIENT; member_role=MEMBER ;;
  admin)   user_role=PATIENT; member_role=ADMIN ;;
  master)  user_role=MASTER;  member_role=MEMBER ;;
  *) echo "Rôle inconnu : $role (patient, admin ou master)" >&2; exit 1 ;;
esac
if [[ $role != master && ${#clinics[@]} -eq 0 ]]; then echo "Indiquez au moins un cabinet pour un $role." >&2; exit 1; fi

known=$(psql_ -tA -c "select slug from clinic")
for slug in ${clinics[@]+"${clinics[@]}"}; do
  grep -qx -- "$slug" <<< "$known" || { echo "Cabinet inconnu : $slug (connus : $(echo $known))" >&2; exit 1; }
done

password=${MEDISLOT_PASSWORD:-}
if [[ -z $password ]]; then
  read -rsp "Mot de passe pour $email : " password; echo
  read -rsp "Confirmation : " again; echo
  [[ $password == "$again" ]] || { echo "Les deux saisies diffèrent." >&2; exit 1; }
fi
bytes=$(printf %s "$password" | wc -c | tr -d ' ')
(( ${#password} >= 12 && bytes <= 72 )) || { echo "Mot de passe : 12 caractères au moins, 72 octets au plus." >&2; exit 1; }

psql_ -v email="$email" -v name="$name" -v pw="$password" -v user_role="$user_role" -v member_role="$member_role" \
  -v slugs="$(IFS=,; echo "${clinics[*]:-}")" <<'SQL'
set client_min_messages = warning;
create extension if not exists pgcrypto;
begin;
insert into app_user(email, password, name, role) values (:'email', crypt(:'pw', gen_salt('bf', 10)), :'name', :'user_role')
  on conflict (email) do update set password = excluded.password, name = excluded.name, role = excluded.role;
insert into membership(user_id, clinic_id, role)
  select u.id, c.id, :'member_role' from app_user u, clinic c
  where u.email = :'email' and c.slug = any(string_to_array(nullif(:'slugs', ''), ','))
  on conflict (user_id, clinic_id) do update set role = excluded.role;
commit;
SQL

echo "OK : $email ($role${clinics[*]:+, ${clinics[*]}})"

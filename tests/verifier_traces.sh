#!/usr/bin/env bash
# Vérifie que la trace passe : Nginx -> collecteur, back -> collecteur -> Jaeger, propagation et nettoyage.
# Usage (Git Bash sous Windows, à la racine du projet, après docker compose up -d --build) :
#   bash tests/verifier_traces.sh
# Variables : APP_URL (défaut http://localhost:8088), JAEGER_URL (défaut http://localhost:16686).
set -u
APP_URL="${APP_URL:-http://localhost:8088}"
JAEGER_URL="${JAEGER_URL:-http://localhost:16686}"
OK=0; ECHEC=0
ok()    { echo "  OK     $1"; OK=$((OK+1)); }
echec() { echo "  ÉCHEC  $1"; ECHEC=$((ECHEC+1)); }
hex()   { od -An -N"$1" -tx1 /dev/urandom | tr -d ' \n'; }

# Trace forgée : on choisit l'identifiant de trace et le span parent, comme le ferait le navigateur.
TRACE_ID="$(hex 16)"; PARENT_ID="$(hex 8)"
SAISIE="verifTraces$(hex 3)"          # texte de recherche qui ne doit apparaître dans aucun attribut
JSON="$(mktemp)"; ENTETES="$(mktemp)"; trap 'rm -f "$JSON" "$ENTETES"' EXIT

echo "1. Nginx relaie /otlp/ vers le collecteur (route du navigateur)"
code="$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  --data '{"resourceSpans":[]}' "$APP_URL/otlp/v1/traces")"
if [ "$code" = "200" ]; then ok "POST /otlp/v1/traces -> 200"; else echec "POST /otlp/v1/traces -> $code (attendu 200 : location /otlp/ dans frontend/nginx.conf ?)"; fi

echo "2. Le back trace la requête et l'envoie jusqu'à Jaeger (agent, collecteur, exportateur)"
curl -s -o /dev/null -D "$ENTETES" -H "traceparent: 00-$TRACE_ID-$PARENT_ID-01" \
  "$APP_URL/api/professionals?q=$SAISIE&page=0&size=6"
recu="$(grep -i '^x-trace-id:' "$ENTETES" | tr -d '\r' | awk '{print $2}')"
trouve=""
for _ in $(seq 1 20); do
  if curl -sf "$JAEGER_URL/api/traces/$TRACE_ID" -o "$JSON" 2>/dev/null; then trouve=1; break; fi
  sleep 1
done
# Lecture du JSON de Jaeger avec Node (déjà requis pour le front).
analyse() { node -e '
const t=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).data[0],p=process.argv[2],saisie=process.argv[3];
const svc=s=>t.processes[s.processID].serviceName;
const api=t.spans.filter(s=>svc(s)==="medislot-api");
const serveur=api.find(s=>/^GET \/api\/professionals/.test(s.operationName));
const sql=api.filter(s=>s.tags.some(g=>g.key==="db.system"||g.key==="db.system.name"));
const enfant=serveur&&serveur.references.some(r=>r.spanID===p);
const fuite=t.spans.some(s=>s.tags.some(g=>String(g.value).includes(saisie)));
console.log([api.length,serveur?1:0,sql.length,enfant?1:0,fuite?1:0].join(" "));
' "$JSON" "$PARENT_ID" "$SAISIE"; }
if [ -n "$trouve" ]; then
  read -r nb serveur sql enfant fuite < <(analyse)
  if [ "$serveur" = "1" ] && [ "$sql" -gt 0 ]; then
    ok "trace $TRACE_ID : $nb spans medislot-api, dont GET /api/professionals et $sql spans SQL"
  else echec "trace trouvée, mais sans span serveur GET /api/professionals ou sans span SQL"; fi
else
  echec "trace $TRACE_ID absente de Jaeger après 20 s (agent chargé ? OTEL_SERVICE_NAME ? exporters du pipeline ?)"
fi

echo "3. Propagation et nettoyage : le back reprend traceparent, la saisie ne reste pas dans la trace"
if [ -n "$trouve" ] && [ "$enfant" = "1" ] && [ "$fuite" = "0" ] && [ "$recu" = "$TRACE_ID" ]; then
  ok "span serveur enfant de $PARENT_ID, X-Trace-Id renvoyé, aucune trace de « $SAISIE » dans les attributs"
else
  [ -z "$trouve" ] && echec "pas de trace à examiner" || {
    [ "${enfant:-0}" = "1" ] || echo "         le span serveur n'a pas $PARENT_ID pour parent (OTEL_TRACES_SAMPLER=parentbased_always_on ?)"
    [ "${fuite:-1}" = "0" ] || echo "         la saisie apparaît dans un attribut (processeur attributes/sante dans le pipeline ?)"
    [ "$recu" = "$TRACE_ID" ] || echo "         en-tête X-Trace-Id : « $recu » au lieu de $TRACE_ID"
    echec "propagation ou nettoyage incomplet"; }
fi

echo
echo "Bilan : $OK OK, $ECHEC ÉCHEC"
[ "$ECHEC" -eq 0 ]

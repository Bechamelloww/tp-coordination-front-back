# La config est copiée dans l'image au lieu d'être montée : un montage échoue si le chemin du projet contient ":".
FROM otel/opentelemetry-collector-contrib:0.165.0
COPY collector.yaml /etc/otelcol-contrib/config.yaml

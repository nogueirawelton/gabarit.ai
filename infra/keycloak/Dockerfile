# syntax=docker/dockerfile:1

# Keycloak com o tema e o realm do gabarit.ai.
# prod: build otimizado (`kc.sh build` com as opções de build fixadas aqui) e `start --optimized`.
# dev (docker-compose.override.yml): `start-dev`, com o tema montado do host e sem cache.
ARG KEYCLOAK_IMAGE=quay.io/keycloak/keycloak:26.8.0

FROM ${KEYCLOAK_IMAGE} AS builder
# opções de build: mudar qualquer uma exige rebuild da imagem
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true
COPY themes/gabarit /opt/keycloak/themes/gabarit
RUN /opt/keycloak/bin/kc.sh build

FROM ${KEYCLOAK_IMAGE}
COPY --from=builder /opt/keycloak/ /opt/keycloak/
# importado no boot só se o realm ainda não existir (não sobrescreve mudanças feitas no admin)
COPY realm/gabarit-realm.json /opt/keycloak/data/import/gabarit-realm.json
# a imagem oficial já roda como usuário não-root (uid 1000)
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]

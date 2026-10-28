# syntax=docker/dockerfile:1

ARG NODE_IMAGE=node:24-alpine
ARG NGINX_IMAGE=nginxinc/nginx-unprivileged:1.28-alpine

# ==========================================
# DEPS: node_modules a partir do lockfile (camada só muda quando o lock muda)
# ==========================================
FROM ${NODE_IMAGE} AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

# ==========================================
# DEV: servidor do Vite com hot reload; código entra via bind mount
# ==========================================
FROM deps AS dev
EXPOSE 5173
CMD ["npm", "run", "dev"]

# ==========================================
# BUILD: typecheck + bundle (sem URL de ambiente: ela vem do config.js em runtime)
# ==========================================
FROM deps AS build
COPY . .
RUN npm run build

# ==========================================
# PROD: só os arquivos estáticos num Nginx sem root (porta 8080).
# No boot, docker-entrypoint.d/40-gabarit-config.sh gera o config.js a partir de KEYCLOAK_URL e API_URL.
# ==========================================
FROM ${NGINX_IMAGE} AS prod
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
COPY config.js.template /etc/gabarit/config.js.template
COPY --chmod=755 40-gabarit-config.sh /docker-entrypoint.d/40-gabarit-config.sh
# só o config.js é gravável pelo processo (usuário nginx); o resto do site fica de root
USER root
RUN chown nginx /usr/share/nginx/html/config.js
USER nginx
EXPOSE 8080

# syntax=docker/dockerfile:1.7

# ---------- Stage 1: build the Angular app ----------
FROM node:22-alpine AS builder

WORKDIR /app

# Install dependencies first so the layer is cached when only source changes.
COPY package.json package-lock.json ./
RUN npm ci

# Copy the rest of the project and build the production bundle.
COPY . .
RUN npm run build

# ---------- Stage 2: serve via nginx ----------
FROM nginx:1.27-alpine

# Replace the default site config with our SPA + proxy rules.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Angular's `application` builder emits to dist/<project>/browser.
COPY --from=builder /app/dist/finglass/browser /usr/share/nginx/html

# wget is in nginx:alpine via busybox — used by the compose healthcheck.
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --quiet --spider http://127.0.0.1/healthz || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

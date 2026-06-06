# syntax=docker/dockerfile:1.7

FROM node:20-bookworm-slim AS web-build
WORKDIR /src
COPY web/package.json web/package-lock.json* ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install --no-audit --no-fund; fi
COPY web/ ./
RUN npm run build

FROM rust:1.83-bookworm AS server-build
WORKDIR /src
COPY server/Cargo.toml server/Cargo.lock* ./
RUN mkdir -p src \
 && echo 'fn main() {}' > src/main.rs \
 && cargo build --release \
 && rm -rf src
COPY server/ ./
RUN touch src/main.rs && cargo build --release

FROM debian:bookworm-slim AS runtime
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl \
 && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /data /srv

ENV FL_DATA_DIR=/data \
    FL_STATIC_DIR=/srv/dist \
    FL_PORT=3000 \
    FL_BIND=0.0.0.0 \
    FL_LOG=info \
    FL_INSECURE_RELAY_TLS=1

COPY --from=web-build /src/dist /srv/dist
COPY --from=server-build /src/target/release/fiatlife-web /usr/local/bin/fiatlife-web

VOLUME ["/data"]
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD curl -fsS http://127.0.0.1:3000/api/health || exit 1

ENTRYPOINT ["/usr/local/bin/fiatlife-web"]

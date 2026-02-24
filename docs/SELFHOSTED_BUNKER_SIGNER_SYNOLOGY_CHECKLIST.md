# Self-Hosted Bunker Signer on Synology NAS

This document outlines a practical deployment checklist for running a self-hosted NIP-46 bunker signer on a Synology NAS, plus currently available self-hostable bunker options.

## Recommended baseline architecture

- `nsecbunkerd` as the signer service
- Dedicated NIP-46 relay for RPC/event transport
- Reverse proxy with TLS termination
- Persistent storage on NAS volumes
- Strict signer policy controls (allowed kinds/methods/rate limits)

## Deployment checklist

### 1) Pre-flight

- [ ] DSM is current (prefer DSM 7.2+) and Container Manager is installed.
- [ ] NAS has a static LAN IP.
- [ ] Domain/subdomain is ready (for example `bunker.example.com`).
- [ ] Router/firewall forwards only `443` to your reverse proxy.
- [ ] Persistent folders are created (example: `/volume1/docker/bunker/{config,data,logs}`).
- [ ] Encrypted off-device backups are configured for bunker state/config.

### 2) Security baseline

- [ ] Use a dedicated Docker network for bunker services.
- [ ] Terminate TLS at a reverse proxy (Synology reverse proxy, Caddy, Traefik, or Nginx).
- [ ] Enable WebSocket support end-to-end (`wss://`).
- [ ] Protect admin surfaces with IP allowlists and/or auth.
- [ ] Use a strong passphrase for encrypted key-at-rest storage.
- [ ] Enable MFA for DSM admin accounts.
- [ ] Prefer SSH key auth and disable password auth for SSH.
- [ ] Add proxy-level rate limits and abuse protections.

### 3) Deploy signer + relay

- [ ] Deploy `nsecbunkerd` with persistent volumes.
- [ ] Deploy a dedicated NIP-46 relay service.
- [ ] Configure environment variables (`ADMIN_NPUBS`, database URL, relay list, base URL, secrets).
- [ ] Verify signer startup and capture its bunker connection URI.
- [ ] Ensure services auto-restart after reboot/failure.

### 4) TLS + routing

- [ ] DNS points to the public endpoint.
- [ ] Reverse proxy routes HTTPS/WebSocket traffic to signer/relay.
- [ ] TLS certs are valid and auto-renew.
- [ ] External `wss://` connection tests pass.

### 5) FiatLife/agent policy hardening

- [ ] Allow only required methods (`get_public_key`, `sign_event`).
- [ ] Restrict to approved event kinds (include `37004` only if required).
- [ ] Constrain relay usage and, when possible, `d`-tag scopes.
- [ ] Set per-client and per-window rate limits.
- [ ] Configure key/client revocation and rotation workflow.
- [ ] Test first with a low-value account.

### 6) Monitoring and operations

- [ ] Centralize logs for signer, relay, and reverse proxy.
- [ ] Add uptime checks and alerting.
- [ ] Document incident response and recovery runbook.
- [ ] Test backup restore at least quarterly.
- [ ] Run periodic key rotation and revocation drills.

### 7) Validation tests (must pass)

- [ ] Client can connect via bunker URI.
- [ ] `get_public_key` works.
- [ ] Allowed `sign_event` requests succeed.
- [ ] Disallowed kinds/methods are denied.
- [ ] Revoked client loses access immediately.
- [ ] Reboot test confirms persistent recovery.

## Currently available self-hostable bunker options

## 1) nsecbunkerd (recommended primary)

- Repo: <https://github.com/kind-0/nsecbunkerd>
- Maturity: strong ecosystem support and active usage.
- Notes: Docker-friendly; includes configuration, security model docs, and admin workflow.

## 2) nak bunker (lightweight CLI bunker)

- Repo: <https://github.com/fiatjaf/nak>
- Command: `nak bunker`
- Notes: excellent for technical operators who prefer minimal dependencies and CLI-first workflows.

## 3) noauthd (Noauth ecosystem server component)

- Repo: <https://github.com/nostrband/noauthd>
- Notes: self-hostable server component for Noauth key manager ecosystem; useful depending on client stack needs.

## 4) Dedicated NIP-46 relay (complementary component)

- Example repo: <https://github.com/Letdown2491/nip46-relay>
- Notes: this is a relay transport component, not the signer itself. Pair with a signer implementation for full bunker setup.

## Selection guidance

- Choose `nsecbunkerd` if you want the most complete general-purpose bunker deployment today.
- Choose `nak bunker` if you want minimal footprint and are comfortable with CLI-centric operations.
- Use a dedicated NIP-46 relay for better isolation, observability, and policy control in production.

## Notes and caveats

- Public relay transport can leak metadata even when payloads are encrypted; dedicated relay paths reduce exposure.
- Do not run without TLS in real deployments.
- Keep signer keys isolated from app hosts and agent hosts when possible.
- For AI-agent write access, enforce strict kind and rate policies before enabling automation.

## Synology Docker Compose starter

This starter is intended for Synology Container Manager using project mode. It gives you:

- `nsecbunkerd`
- Postgres for bunker persistence
- `nip46-relay` as dedicated RPC relay transport

Adjust image tags and environment values before production use.

```yaml
version: "3.9"

services:
  postgres:
    image: postgres:16-alpine
    container_name: bunker-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: nsecbunker
      POSTGRES_USER: nsecbunker
      POSTGRES_PASSWORD: CHANGE_ME_STRONG_PASSWORD
    volumes:
      - /volume1/docker/bunker/postgres:/var/lib/postgresql/data
    networks:
      - bunker_net

  nsecbunkerd:
    image: ghcr.io/kind-0/nsecbunkerd:latest
    container_name: nsecbunkerd
    restart: unless-stopped
    depends_on:
      - postgres
    environment:
      DATABASE_URL: postgresql://nsecbunker:CHANGE_ME_STRONG_PASSWORD@postgres:5432/nsecbunker
      ADMIN_NPUBS: npub1replace_with_admin_npub
      # Add/adjust other variables from upstream .env.example:
      # BASE_URL, RELAYS, LOG_LEVEL, etc.
    volumes:
      - /volume1/docker/bunker/config:/app/config
      - /volume1/docker/bunker/logs:/app/logs
    networks:
      - bunker_net
    # Keep internal only; expose via reverse proxy
    ports:
      - "127.0.0.1:7777:7777"

  nip46-relay:
    image: ghcr.io/letdown2491/nip46-relay:latest
    container_name: nip46-relay
    restart: unless-stopped
    environment:
      RELAY_NAME: FiatLife Bunker Relay
      RELAY_DESCRIPTION: Dedicated NIP-46 relay
      RELAY_URL: wss://nip46.example.com
      RELAY_PORT: ":3334"
      WORKING_DIR: /data
      KEEP_IN_MINUTES: "10"
      ACCEPT_WINDOW_IN_MINUTES: "1"
      RATE_LIMIT_PER_MINUTE: "100"
    volumes:
      - /volume1/docker/bunker/nip46-relay:/data
    networks:
      - bunker_net
    ports:
      - "127.0.0.1:3334:3334"

networks:
  bunker_net:
    name: bunker_net
```

### Suggested Synology reverse proxy mapping

- `bunker.example.com` -> `http://127.0.0.1:7777`
- `nip46.example.com` -> `http://127.0.0.1:3334`

Enable WebSocket support and TLS certificates for both hosts.

### First-run commands

After bringing the stack up, run the following from the project directory to retrieve your bunker URI:

```bash
docker compose up -d
docker compose logs -f nsecbunkerd
docker compose exec nsecbunkerd cat /app/config/connection.txt
```

### Production hardening reminders

- Replace `latest` image tags with pinned versions.
- Move secrets to Synology secrets manager or env files with restricted permissions.
- Restrict inbound access to reverse proxy only (`443`), no direct container port exposure.
- Keep a tested backup/restore path for `/volume1/docker/bunker`.

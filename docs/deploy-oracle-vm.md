# Oracle VM production runbook (Option 3: always-on VM)

## Architecture decision

- **Chosen option:** Oracle VM (always-on), no mandatory custom domain at launch.
- Initial access can use VM public IP; DuckDNS + TLS can be added after first stable deployment.
- Runtime stack: `postgres` + `backend` + `nginx` (frontend static + reverse proxy).

## Pre-deploy gate (mandatory before each release)

Run from repository root:

```bash
npm test
npm run build
cd backend && sh ./mvnw test
```

Deploy is blocked if any command fails.

## Files used for production deploy

- `deploy/production/docker-compose.yml`
- `deploy/production/nginx.conf`
- `backend/Dockerfile`
- `Dockerfile.frontend`
- `.env.example`
- `backend/.env.example`

## Deployment sequence

1. Register in Oracle Cloud and select **Frankfurt** region.
2. Acquire Ampere A1 VM (target: 4 OCPU / 24 GB). If capacity is unavailable, run automated retries.
3. Install Docker + Docker Compose on VM.
4. Open ports `80` and `443` in Oracle Security Lists and host firewall (iptables/ufw).
5. Clone repository on VM.
6. Create `deploy/production/.env` with production values (copy from examples).
7. Start stack:

   ```bash
   cd deploy/production
   docker compose build
   docker compose up -d
   ```

8. Verify:
   - `GET /health` returns `{"status":"UP"}`
   - frontend UI loads
   - at least one GraphQL mutation succeeds via `/graphql`
9. Run smoke-tests and record current image tags as rollback baseline.
10. Configure daily PostgreSQL backup (`pg_dump`) and upload to Oracle Object Storage via cron.
11. Add external monitoring (UptimeRobot) for `/health`.
12. Configure DuckDNS and Let's Encrypt TLS when ready.

## Example production env file

Create `deploy/production/.env`:

```dotenv
POSTGRES_DB=spira
PORT=8080
DATABASE_URL=jdbc:postgresql://postgres:5432/spira
DATABASE_USERNAME=spira
DATABASE_PASSWORD=change-me
CORS_ALLOWED_ORIGINS=https://your-domain.example,https://your-duckdns-subdomain.duckdns.org
VITE_GRAPHQL_ENDPOINT=/graphql
```

## Update flow (new release)

```bash
cd deploy/production
docker compose build --pull
docker compose up -d
docker compose ps
```

Then run smoke-tests and validate `/health`.

## Smoke-test checklist

- `curl -fsS http://<host>/health`
- Open frontend main page and load goals list.
- Execute one read and one mutation against `/graphql`.
- Check backend and nginx logs for 5xx:

  ```bash
  docker compose logs --tail=200 backend nginx
  ```

## Rollback

1. Keep previous image tags in deployment notes.
2. Set compose services to previous known-good tags.
3. Recreate services:

   ```bash
   docker compose up -d
   ```

4. Re-run smoke-tests and confirm `/health`.

## Backups: pg_dump + Oracle Object Storage + cron

1. Create bucket in Oracle Object Storage.
2. Configure CLI/API credentials on VM.
3. Daily backup script pattern:
   - `pg_dump` to timestamped file
   - compress (`gzip`)
   - upload to Object Storage bucket
   - prune old local dumps
4. Register cron (example: daily 02:30):

   ```cron
   30 2 * * * /opt/spira/scripts/backup-postgres.sh >> /var/log/spira-backup.log 2>&1
   ```

## Ampere A1 risk handling

| Situation | Action |
|---|---|
| Ampere A1 unavailable | Keep automated retry script running for 24–48 hours |
| Unavailable for too long | Temporary x86 micro fallback for smoke validation only (very limited RAM) |
| VM acquired and running | Keep it always-on; no sleep/cold-start handling is needed |

### Fallback note (x86)

For emergency x86 fallback, either:

- build a separate `linux/amd64` image, or
- publish multi-arch images (`linux/arm64` + `linux/amd64`)

to avoid deployment blocking while Ampere capacity is unavailable.

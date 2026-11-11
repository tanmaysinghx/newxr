# Deploying Newxr

Newxr is one jar (`newxr.jar`) that serves the web console and the API. It needs **Java 25** and, for production, **PostgreSQL 15+** or **MySQL 8.0+**.

## Pick a way to run it

| | Good for | How |
|---|---|---|
| `java -jar` | Trying it out | `java -jar newxr.jar --httpPort=8080` |
| systemd | A Linux server | `sudo ./install.sh` (see below) |
| Docker Compose | Containers, with PostgreSQL included | `docker compose up -d` |

## The Newxr home

All state lives in one directory: `~/.newxr` by default, `/var/lib/newxr` with `install.sh`, `/data` in Docker.

```
newxr.properties          configuration (written on first start, all options commented)
data/                     embedded H2 database (only when no external database is set)
secrets/master.key        encryption key for stored secrets: back this up
secrets/initialSetupKey   one-time key for the setup wizard (deleted when setup completes)
```

## Database

The setup wizard can switch to PostgreSQL or MySQL before anything is stored. To configure it by hand instead, set in `newxr.properties` or the environment:

```properties
spring.datasource.url=jdbc:postgresql://db.internal:5432/newxr
spring.datasource.username=newxr
spring.datasource.password=change-me
```

Create an empty database owned by that user; Newxr creates its tables on first start and adds new ones on upgrade.

> There is no H2 → PostgreSQL/MySQL data copy yet. Decide on the production database before people start using Newxr. **Administration → Database** shows the current connection and can test another one.

## Linux with systemd

```bash
./build.sh                                   # or copy a released newxr.jar to dist/
sudo ./install.sh --port 8080 --home /var/lib/newxr --user newxr
```

`install.sh` checks for Java 25, creates the `newxr` system user, installs the jar to `/opt/newxr`, writes `/etc/systemd/system/newxr.service` (from `distribution/newxr.service`) and starts it. The unit runs hardened (`ProtectSystem=strict`, writes only to the Newxr home).

```bash
journalctl -u newxr -f                       # logs
sudo cat /var/lib/newxr/secrets/initialSetupKey
sudo systemctl edit newxr                    # e.g. Environment="JAVA_OPTS=-Xmx2g"
```

**Upgrade**: build or download the new jar and re-run `install.sh`; it replaces the jar and restarts the service.

## Docker

```bash
cp .env.example .env    # set POSTGRES_PASSWORD and NEWXR_PUBLIC_URL
docker compose up -d
docker compose logs newxr | grep -A2 "setup key"
```

The image runs as a non-root user, keeps data in the `/data` volume and has a health check on `/actuator/health`.

## Behind a reverse proxy

Terminate TLS at the proxy and forward to Newxr. Set `NEWXR_PUBLIC_URL` (or `newxr.public-url`) to the address users type, e.g. `https://newxr.example.com`. Portal SSO redirect URIs and notification links are built from it.

nginx:

```nginx
server {
    listen 443 ssl;
    server_name newxr.example.com;
    client_max_body_size 25m;
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

Newxr reads `X-Forwarded-*` headers (`server.forward-headers-strategy=native`); only expose it through the proxy so clients can't forge them.

## Backups

Back up together:

1. The database: `pg_dump` / `mysqldump`, or for H2 stop Newxr and copy `<home>/data`.
2. `<home>/secrets/master.key`: without it, stored secrets (Portal SSO client secret, Kafka password) can't be decrypted.
3. `<home>/newxr.properties`.

## Security checklist

- Serve over HTTPS and set `NEWXR_PUBLIC_URL` to the `https://` address.
- Keep `newxr.properties` and `secrets/` readable only by the service user (`install.sh` sets `750`).
- Lockout: 5 failed sign-ins lock an account for 15 minutes (`newxr.security.max-failed-logins`, `newxr.security.lockout-duration`). Sessions expire after 12 hours idle (`server.servlet.session.timeout`).
- Webhook URLs (Pulse integrations, Code CI) contain secrets. Rotate them from the app if they leak.
- Review **Administration → Audit log** for sign-ins and admin changes.

# Newxr

**One self-hosted jar for software, knowledge, service and operations teams.**

Newxr bundles seven apps behind one sign-in, one search and one notification bell:

| App | What it does |
|---|---|
| **Track** | Projects, issues, board, backlog, sprints, timeline, sprint reports |
| **Pages** | Spaces, pages with a Markdown editor, comments, reactions, version history, blog posts |
| **Boards** | Visual card boards with lists, labels, checklists and covers |
| **Code** | Repositories with browser editing, branches, pull requests with reviews and diffs, CI pipelines and deployments |
| **Desk** | Service desk queues with SLAs, internal notes, and a help center for customers |
| **Pulse** | Alerts with escalation, on-call schedules, incidents, and a public status page |
| **Goals** | Goals with metrics, weekly updates and contributing work |

Plus **Administration** for users, groups, products, security, the audit log and settings.

It runs as a single `newxr.jar` on Java 25, with an embedded database for evaluation and PostgreSQL or MySQL for production. Sign-in is built-in passwords, [Portal SSO](docs/portal-sso.md) (OpenID Connect), or both. Every event can optionally be streamed to [Kafka](docs/kafka-events.md).

Licensed under the [Apache License 2.0](LICENSE.txt).

---

## Quick start

```bash
./build.sh                                  # needs Node.js 20+ and JDK 25 → dist/newxr.jar
java -jar dist/newxr.jar --httpPort=8080
```

Open <http://localhost:8080>. The first start prints a **one-time setup key** and saves it to `~/.newxr/secrets/initialSetupKey`; the setup wizard asks for it, then walks you through:

1. **License**: accept Apache-2.0 (recorded with who and when).
2. **Database**: keep embedded H2, or point at PostgreSQL / MySQL (tested before saving).
3. **Administrator and sign-in**: your admin account, then built-in, Portal SSO, or both.
4. **Kafka**: optional event streaming.
5. **Organization**: name, and whether to add **sample content** (a demo project, space, board, service desk, operations team, repository and goals) so every app has something to explore.

### Docker

```bash
cp .env.example .env          # set POSTGRES_PASSWORD
docker compose up -d          # Newxr + PostgreSQL
docker compose logs newxr     # the setup key is printed here
```

Or just the app, on the embedded database: `docker build -t newxr . && docker run -p 8080:8080 -v newxr-data:/data newxr`.

### Linux service

```bash
./build.sh
sudo ./install.sh --port 8080           # systemd unit, user `newxr`, data in /var/lib/newxr
sudo cat /var/lib/newxr/secrets/initialSetupKey
```

Re-run `install.sh` with a new jar to upgrade; data is left alone. See [docs/deployment.md](docs/deployment.md) for reverse proxies, backups and production settings.

---

## Configuration

Everything is optional. Settings resolve in this order (later wins):

1. Built-in defaults
2. `<newxr home>/newxr.properties`, written on first start with every option commented out
3. Environment variables (`NEWXR_PORT`, `NEWXR_PUBLIC_URL`, `SPRING_DATASOURCE_URL`, …)
4. Command-line flags

| Flag | Environment | Default | Meaning |
|---|---|---|---|
| `--httpPort` | `NEWXR_PORT` | `8080` | HTTP port |
| `--httpListenAddress` | | all interfaces | Bind address |
| `--newxrHome` | `NEWXR_HOME` | `~/.newxr` | Data directory (H2 database, secrets, config) |
| `--config` | `NEWXR_CONFIG` | `<home>/newxr.properties` | Configuration file |
| | `NEWXR_PUBLIC_URL` | | Address browsers use; needed for Portal SSO and links |

Secrets (the Portal SSO client secret, the Kafka password) are encrypted at rest with `<home>/secrets/master.key` (AES-256-GCM). Back that file up with the database.

---

## Integrations

- **Portal SSO**: OIDC authorization code with PKCE, accounts created on first sign-in. [docs/portal-sso.md](docs/portal-sso.md)
- **Kafka**: every domain event as JSON on `<prefix>.events`. [docs/kafka-events.md](docs/kafka-events.md)
- **Alert webhooks (Pulse)**: generic JSON or Prometheus Alertmanager to a per-integration URL.
- **CI webhooks (Code)**: report pipeline runs and deployments to a per-repository URL.
- **Track ↔ Code**: put an issue key (`ORB-412`) in a branch, commit or pull request title and it shows on the issue.

## Development

```
newxr-backend/    Spring Boot 4 (Java 25): one package per app, each with entities, a *Service controller, sample data
newxr-frontend/   Angular (standalone components, signals), one lazy-loaded folder per app
design/           Design source (renders/ has the reference frames)
distribution/     systemd unit used by install.sh
docs/             Deployment, Portal SSO, Kafka events, architecture
```

```bash
cd newxr-frontend && npx ng serve          # console on :4200, proxies /api to :8080
cd newxr-backend && ./gradlew bootRun      # server on :8080
./gradlew test                             # backend unit tests
./smoke-test.sh                            # fresh-install end-to-end check of dist/newxr.jar
```

Read [docs/architecture.md](docs/architecture.md) and [AGENTS.md](AGENTS.md) before changing code.

## Status and limits

Newxr 1.0 is feature-complete for the apps above. Not in this version:

- **Git hosting**: Code stores files and commits itself; there is no `git push`/`clone` yet. CI reports into Newxr through a webhook.
- **Email and SMS**: notifications, invitations and escalations are in-app. Invitation and reset links are shown to the administrator to pass on.
- **Database migrations and data copy**: the schema is managed by Hibernate (`ddl-auto=update`), and there is no tool yet to move data from H2 to PostgreSQL/MySQL. Choose your production database at setup.
- **Real-time co-editing** in Pages, **two-step verification**, and **Ask AI**.

# Newxr — build plan & task list

Self-hosted suite for software, knowledge, service and operations teams, shipped as **one jar**.
Design source of truth: [`design/newxr-design`](design/newxr-design) (renders in `renders/`).

| App | Role | Design frame |
|---|---|---|
| Hub | Home, app switcher, search, notifications | `Hub.png` |
| Track | Projects, issues, board, backlog, sprints, timeline, reports | `Track.png` |
| Pages | Spaces, pages, editor, comments | `Pages.png` |
| Boards | Visual card boards | `Boards.png` |
| Code | Git repositories, pull requests, pipelines | `Code.png` |
| Desk | Service desk queues, SLAs, help center | `Desk.png` |
| Pulse | Alerts, on-call, status pages | `Pulse.png` |
| Goals | Goals and project updates | `Goals.png` |
| Admin | Users, groups, products, security, settings | `Admin.png` |

Legend: `[x]` done · `[~]` in progress · `[ ]` to do

---

## Stage 0 — Repository & planning
- [x] Review design renders and Portal SSO (README, setup wizard, config file, SSO client in eggless-observability)
- [x] Task list (this file)
- [x] Root `README.md`, `LICENSE.txt` (Apache 2.0), `.gitignore`, `.dockerignore`, `AGENTS.md`

## Stage 1 — Platform backend (single jar, first-run setup)
- [x] Rename to `com.tanmaysinghx.newxr`, jar `newxr.jar`, launcher flags `--httpPort`, `--config`, `--newxrHome`
- [x] `~/.newxr` home, `newxr.properties` read before Spring, template written on first start, merge-in-place updates
- [x] Embedded H2 by default; PostgreSQL / MySQL via JDBC URL (dialect auto-detected)
- [x] At-rest key (`secrets/master.key`) + AES-256-GCM for stored secrets (SSO client secret, Kafka password)
- [x] One-time setup key printed and saved to `secrets/initialSetupKey`
- [x] Setup API: status, license, database test/save (+ in-process restart), Kafka test, initialize
- [x] Apache-2.0 acceptance required and recorded (who/when) before the administrator is created
- [x] Built-in auth: BCrypt users, JSON login, session cookie, CSRF cookie for the SPA, lockout
- [x] Portal SSO: OIDC authorization code + PKCE (server side), JIT user provisioning by email
- [x] Portal SSO round trip verified against a running portal-sso.jar (confidential client + PKCE, JIT account)
- [x] Sign-in modes: built-in, Portal SSO, or both; break-glass local admin always available
- [x] Kafka (optional): domain events published to `<prefix>.events` when configured; in-process otherwise
- [x] Event bus → in-app notifications

## Stage 2 — Frontend foundation
- [x] Design tokens (colour, type, radius) from Foundations; Inter + DejaVu Sans Mono
- [x] Icon set (from `generator/icons.py`) as an Angular component
- [x] Shell: top nav (app switcher, search, Create, notifications, help, theme, account), sidebar
- [x] Setup wizard (License → Database → Administrator & sign-in → Kafka → Organization → Done)
- [x] Login page (email/password, Continue with SSO), forgot/remember
- [x] Home "For you" (apps, assigned to me, recently viewed, starred), Recent, Starred, Apps, People, Search, Profile
- [x] Frontend bundled into the jar by Gradle

## Stage 3 — Track (Jira)
- [x] Projects (key, name, type), issue types, priorities, statuses, labels
- [x] Issues CRUD, child issues, acceptance checklist, comments, history
- [x] Board (drag between columns), Backlog with sprints (create/start/complete), List with filters
- [x] Issue detail page, Create issue modal
- [x] Timeline (epics), Sprint report (burndown, velocity, status, workload)
- [x] Sample "Orbit Mobile" project (velocity history, active sprint, epics) for evaluation installs
- [x] Portable long-text columns (PostgreSQL `text` / MySQL `longtext` / H2) and typed search queries

## Stage 4 — Pages (Confluence)
- [x] Spaces (directory + overview), page tree, page view, editor (Markdown), publish/draft
- [x] Comments and reactions
- [x] Version history with restore, blog posts, templates, space directory/archive, page search with snippets

## Stage 5 — Boards, Goals, Desk, Pulse, Code
- [x] Boards: boards, lists, cards, labels, drag & drop, card dialog (members, dates, checklist, cover, Track link)
- [x] Goals: goal tree, status, progress, metric vs plan chart, weekly updates, followers, contributing work
- [x] Desk: service projects, queues, requests, SLAs, internal notes, help center for customers
  - `DeskService` (`/api/desk`): queues (all/mine/unassigned/breaching/per category/resolved), stats (open, avg first response,
    breaching soon, CSAT 30 days + delta), request detail, reply/internal note, SLA per priority (P1 30m/4h, P2 1h/8h, P3 4h/24h,
    P4 8h/72h; resolution clock pauses while waiting for the customer), similar resolved requests, customers, HubContributor
  - Help center `/api/desk/help/**`: customers only see their own requests and never internal notes; reply, resolve, CSAT
  - `DeskSampleData` (IT Help with HELP-1093 VPN request, People team, Facilities, Legal); frontend `/desk` (projects, queues,
    request page, customers, SLAs) and `/help` customer portal outside the agent shell
- [x] Pulse: alerts, acknowledge/close, on-call schedule, escalation, public status page
  - Alerts with dedup (count), acknowledge/close/reopen/snooze/assign/notes, bulk actions, timeline; stats (MTTA/MTTR 7 days + delta)
  - Escalation policy per team (primary/secondary/person/team, delays from raise); `AlertEngine` job runs due steps every 30 s;
    notifications are in-app (email/SMS need a provider — see Later)
  - Incidents keyed `{team}-{n}` (OPS-77), updates (public/internal), affected components, resolving closes linked alerts
  - On-call: rotating layers + overrides → final schedule, week view, page/message the on-call person, iCal export
  - Status page: components (manual status or worst open incident impact), 90-day bars and uptime from public incidents,
    public `/status/{slug}` + Atom feed (`/api/public/status/{slug}/feed.atom`)
  - Integrations: webhook `/api/public/pulse/hooks/{token}` (generic JSON or Alertmanager; token stored as SHA-256, shown once;
    CSRF-exempt), Desk "Linked" values open search
- [x] Code: repositories, pull requests, reviews, pipeline runs (`renders/Code.png`); `/api/code/linked/{issueKey}` for Track
  - Files stored per branch with a merge-base hash: browser editing as commits, branches, PR diffs (LCS line diff), line comments,
    reviews, merge with conflict detection, "update branch" and "keep branch version" to resolve
  - CI reports pipelines/deployments to a per-repo webhook `/api/public/code/hooks/{token}` (hashed, CSRF-exempt); Newxr runs no builds
  - Issue keys in branch names, commit messages and PR titles link to Track (Development panel); sample repo `orbit-mobile`, PR #284

## Stage 6 — Hub & Admin
- [x] Global search across apps, notifications panel, starred, recent (built in Stage 2, each app adds a HubContributor)
- [x] Admin: users (invite, role, app access, deactivate, unlock, reset), groups, products, security, audit log
  - Invitations/resets are one-time links (`/invite/{token}`, hashed, 7 days / 24 h) shown to the admin: no SMTP yet
  - Audit log in its own transaction: sign-ins (password, Portal SSO, failures, lockouts) and every admin change
  - Billing from the design is left out (self-hosted, Apache-2.0); no 2FA tile (2FA isn't implemented)
- [x] Admin settings: organization, sign-in (built-in / Portal SSO, with issuer test), database status + connection test, Kafka (+ test)

## Stage 7 — Packaging & docs
- [x] `build.sh` (frontend + backend → `dist/newxr.jar`), `install.sh` (Linux, systemd, Java 25 check, re-run to upgrade)
- [x] `distribution/newxr.service` (hardened; `systemd-analyze verify` clean), `Dockerfile`, `compose.yaml` (+ PostgreSQL), `.env.example`
- [x] Docs: `docs/deployment.md`, `docs/portal-sso.md`, `docs/kafka-events.md`, `docs/architecture.md`
- [x] End-to-end smoke test of a fresh install on H2: `./smoke-test.sh` (28 checks, passes); backend unit tests (diff, on-call, mappings)

## Later (not in v1)
- [ ] Liquibase changelogs (v1 uses Hibernate schema update) and an H2 → PostgreSQL/MySQL data copy
- [ ] "Connect with Portal SSO" manifest flow (no copied secrets) — see portal-sso `docs/APP_CONNECT.md`
- [ ] Real Git hosting in Code, real-time collaboration in Pages, SMTP email, Ask AI (needs an AI provider setting)

---

## Resume notes (2026-11-11)

**Where we stopped:** v1 complete: every stage done. `./build.sh && ./smoke-test.sh` passes on a fresh H2 install.
The Docker image and `install.sh` were not run here (no Docker; needs root/systemd); the unit file verifies.

**Verified working end to end:** setup wizard (key → license → DB → admin & sign-in → Kafka → org), built-in login,
Portal SSO login against a real `portal-sso.jar` (confidential client + PKCE, account created on first sign-in),
Track, Pages, Boards, Goals, Desk (queues, request page, help center), Pulse (alerts, escalation job, incidents, on-call, public status page, webhooks), Code (source, edit/commit, PR diff/review/merge/conflicts,
CI webhook, Track links), Admin (invite → accept → sign in, roles/app access, lockout/unlock, audit), home, search, notifications.

**How to build and try it**
```bash
cd newxr-frontend && npx ng build          # → dist/newxr-frontend/browser
cd ../newxr-backend && ./gradlew bootJar   # copies the frontend in → build/libs/newxr.jar
java -jar build/libs/newxr.jar --httpPort=8080 --newxrHome=/tmp/newxr-try
```
Tick "Add sample content" in the wizard to get the Orbit Mobile demo data.

**Conventions to keep**
- Each app = `backend/<app>/` (entities, repos, a `*Service` that is also the `@RestController` and a `HubContributor`,
  a `*SampleData` implementing `SampleDataContributor`) + `frontend/src/app/<app>/` (`*-api.ts`, pages, `*.routes.ts`
  lazy-loaded in `app.routes.ts`).
- Long text columns: `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)`; in JPQL use `lower(cast(x as String))` on them, and never
  `lower(:param)` alone (Hibernate 7 cannot type it).
- Seed timestamps: set `createdAt`/`updatedAt` *before* `save()` (PrePersist keeps them).
- Jackson 3: missing primitives allowed via `spring.jackson.deserialization.fail-on-null-for-primitives=false`.
- CSP is strict (`script-src 'self'`): keep Angular `inlineCritical: false`.
- Charts: series colours `--series-1/--series-2` (validated light + dark); always legend + tooltip + table view.

**Next:** the "Later" list: Liquibase + H2→PostgreSQL/MySQL copy, Portal SSO manifest flow, Git hosting, SMTP email
(invitations, escalations, status subscriptions), real-time Pages, 2FA, Ask AI.

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
- [ ] Root `README.md` in Portal SSO style, `LICENSE.txt` (Apache 2.0), `.gitignore`, `AGENTS.md`

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
- [ ] Pulse: alerts, acknowledge/close, on-call schedule, escalation, public status page (`renders/Pulse.png`)
- [ ] Code: repositories, pull requests, reviews, pipeline runs (`renders/Code.png`); also serve `/api/code/linked/{issueKey}` used by the Track issue Development panel

## Stage 6 — Hub & Admin
- [x] Global search across apps, notifications panel, starred, recent (built in Stage 2, each app adds a HubContributor)
- [ ] Admin: users (invite, role, deactivate), groups, products, security, audit log
- [ ] Admin settings: organization, sign-in (built-in / Portal SSO), database status, Kafka

## Stage 7 — Packaging & docs
- [ ] `build.sh` (frontend + backend → `newxr.jar`), `install.sh` (Linux, systemd)
- [ ] `distribution/newxr.service`, `Dockerfile`, `compose.yaml`, `.env.example`
- [ ] Docs: deployment, Portal SSO integration, Kafka events, architecture
- [ ] End-to-end smoke test of a fresh install on H2

## Later (not in v1)
- [ ] Liquibase changelogs (v1 uses Hibernate schema update) and an H2 → PostgreSQL/MySQL data copy
- [ ] "Connect with Portal SSO" manifest flow (no copied secrets) — see portal-sso `docs/APP_CONNECT.md`
- [ ] Real Git hosting in Code, real-time collaboration in Pages, SMTP email, Ask AI (needs an AI provider setting)

---

## Resume notes (paused 2026-11-11)

**Where we stopped:** Stage 5, Desk done and verified on a fresh H2 install (API + UI). Next: Pulse, then Code.

**Verified working end to end:** setup wizard (key → license → DB → admin & sign-in → Kafka → org), built-in login,
Portal SSO login against a real `portal-sso.jar` (confidential client + PKCE, account created on first sign-in),
Track, Pages, Boards, Goals, Desk (queues, request page, help center), home, search, notifications.

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

**Still to do after Stage 5:** Stage 6 Admin (users/groups/products/security/audit, sign-in + Kafka + database settings
screens), Stage 7 packaging (`build.sh`, `install.sh`, systemd unit, Dockerfile/compose, README in Portal SSO style,
docs, AGENTS.md), then the "Later" list.

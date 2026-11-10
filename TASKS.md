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
- [~] Root `README.md` in Portal SSO style, `LICENSE.txt` (Apache 2.0), `.gitignore`, `AGENTS.md`

## Stage 1 — Platform backend (single jar, first-run setup)
- [ ] Rename to `com.tanmaysinghx.newxr`, jar `newxr.jar`, launcher flags `--httpPort`, `--config`, `--newxrHome`
- [ ] `~/.newxr` home, `newxr.properties` read before Spring, template written on first start, merge-in-place updates
- [ ] Embedded H2 by default; PostgreSQL / MySQL via JDBC URL (dialect auto-detected)
- [ ] At-rest key (`secrets/master.key`) + AES-256-GCM for stored secrets (SSO client secret, Kafka password)
- [ ] One-time setup key printed and saved to `secrets/initialSetupKey`
- [ ] Setup API: status, license, database test/save (+ in-process restart), Kafka test, initialize
- [ ] Apache-2.0 acceptance required and recorded (who/when) before the administrator is created
- [ ] Built-in auth: BCrypt users, JSON login, session cookie, CSRF cookie for the SPA, lockout
- [ ] Portal SSO: OIDC authorization code + PKCE (server side), JIT user provisioning by email
- [ ] Sign-in modes: built-in, Portal SSO, or both; break-glass local admin always available
- [ ] Kafka (optional): domain events published to `<prefix>.events` when configured; in-process otherwise
- [ ] Event bus → in-app notifications

## Stage 2 — Frontend foundation
- [ ] Design tokens (colour, type, radius) from Foundations; Inter + DejaVu Sans Mono
- [ ] Icon set (from `generator/icons.py`) as an Angular component
- [ ] Shell: top nav (app switcher, search, Create, Ask AI, notifications, help, theme, avatar), sidebar
- [ ] Setup wizard (License → Database → Administrator & sign-in → Kafka → Organization → Done)
- [ ] Login page (email/password, Continue with SSO), forgot/remember
- [ ] Home "For you" (apps, assigned to me, recently viewed, starred)
- [ ] Frontend bundled into the jar by Gradle

## Stage 3 — Track (Jira)
- [ ] Projects (key, name, type), issue types, priorities, statuses, labels
- [ ] Issues CRUD, child issues, acceptance checklist, comments, history
- [ ] Board (drag between columns), Backlog with sprints (create/start/complete), List with filters
- [ ] Issue detail page, Create issue modal
- [ ] Timeline (epics), Sprint report (burndown, velocity, status, workload)

## Stage 4 — Pages (Confluence)
- [ ] Spaces (directory + overview), page tree, page view, editor (Markdown), publish/draft
- [ ] Comments and reactions

## Stage 5 — Boards, Goals, Desk, Pulse, Code
- [ ] Boards: boards, lists, cards, labels, drag & drop
- [ ] Goals: goals, status, progress, updates
- [ ] Desk: service projects, queues, requests, SLAs, internal notes, help center for customers
- [ ] Pulse: alerts, acknowledge/close, on-call schedule, status page
- [ ] Code: repositories, pull requests, reviews, pipeline runs (metadata; no git hosting)

## Stage 6 — Hub & Admin
- [ ] Global search across apps, notifications panel, starred, recent
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
- [ ] Real Git hosting in Code, real-time collaboration in Pages, SMTP email, Ask AI

# Architecture

```
Browser ── Angular console (static files in the jar)
   │  /api/**  JSON, session cookie + CSRF cookie
   ▼
Spring Boot 4 (Java 25, virtual threads)
   ├─ platform/   home directory, newxr.properties, database selection, secret encryption
   ├─ setup/      first-run wizard (setup key, license, database, admin, sign-in, Kafka)
   ├─ security/   built-in login with lockout, Portal SSO (OIDC + PKCE), CSRF, current user
   ├─ hub/        home, search, recent, stars: aggregated from every app's HubContributor
   ├─ events/     DomainEvent → in-app notifications (notify/) and Kafka (optional)
   ├─ track/ pages/ boards/ code/ desk/ pulse/ goals/   one package per app
   └─ admin/      users, groups, products, audit log, settings, invitations
   ▼
H2 (embedded) │ PostgreSQL │ MySQL       (JPA / Hibernate, schema via ddl-auto=update)
```

## Conventions

**One package per app.** Each has its entities and repositories, a `*Service` that is also the `@RestController` (under `/api/<app>`) and implements `HubContributor`, and a `*SampleData` implementing `SampleDataContributor` for the demo content. The frontend mirrors it: `src/app/<app>/` with `<app>-api.ts`, pages, and `<app>.routes.ts` lazy-loaded from `app.routes.ts`.

**Hub contributions.** `assignedTo(user)` feeds "Assigned to me", `waitingOn(user)` feeds "Waiting on you", and `search(q)` feeds global search. Adding an app to search means implementing these three methods.

**Events.** Changes that someone should hear about publish a `DomainEvent` with recipients. The notification listener writes in-app notifications after the transaction commits; the Kafka sink publishes the same event.

**Security.** `/api/public/**` is open (login options, setup, invitations, the public status page, alert and CI webhooks). `/api/admin/**` needs the ADMIN role. Everything else under `/api` needs a session. CSRF uses Spring's SPA cookie mode; the two webhook paths are exempt and authenticate with a secret token in the URL, of which only a SHA-256 hash is stored. The CSP is strict (`script-src 'self'`).

**Secrets at rest.** `SecretBox` encrypts stored secrets (SSO client secret, Kafka password) with AES-256-GCM and `secrets/master.key`. Tokens that only need checking (webhooks, invitations) are stored as hashes.

## How the apps work

- **Track**: issues keyed `<PROJECT>-<n>` with a row-locked sequence per project; board columns are statuses; burndown and velocity are computed from issue resolution times and story points.
- **Code**: a minimal version control in the database. Each branch holds its own copy of every file. A copied file remembers the hash it started from, which is the merge base: a pull request's changes are the files whose hash moved, and a conflict is a file the target changed too. Merging writes one commit to the target. CI is external and reports runs through the webhook; issue keys in branch names, commit messages and PR titles link work to Track.
- **Desk**: SLA targets per priority (first response and resolution). The resolution clock pauses while a request waits for the customer and resumes with the paused time added back.
- **Pulse**: alerts de-duplicate on a key (repeats raise the count). The escalation policy runs steps by delay from when the alert was raised; a scheduled job checks for due steps every 30 seconds and acknowledging stops it. On-call is rotating layers plus overrides; the final schedule is the primary layer with overrides on top. Status page history and uptime are derived from public incidents.
- **Admin**: invitations and password resets are one-time links (hashed, 7 days or 24 hours) because Newxr has no email yet. The audit log is written in its own transaction so failed sign-ins and rolled-back changes are still recorded.

## Data

Hibernate creates and updates the schema (`ddl-auto=update`). Long text uses `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)` so it maps to PostgreSQL `text`, MySQL `longtext` and H2's large text type. Timestamps are UTC `Instant`s; `BaseEntity` sets `createdAt`/`updatedAt` unless they are set beforehand (sample data back-dates records).

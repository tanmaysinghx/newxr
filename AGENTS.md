# Notes for coding agents

Newxr is a self-hosted suite (Track, Pages, Boards, Code, Desk, Pulse, Goals and Admin) shipped as one Spring Boot jar with an Angular console. Read `docs/architecture.md` first; `TASKS.md` is the build plan and status.

## Build and check

```bash
cd newxr-frontend && npx ng build          # must finish with no errors or warnings
cd newxr-backend && ./gradlew test bootJar # unit tests + jar (copies the frontend in)
./build.sh                                 # both → dist/newxr.jar
./smoke-test.sh                            # fresh H2 install, setup, sign-in, every app (28 checks)
```

Try a change end to end on a throwaway home: `java -jar newxr-backend/build/libs/newxr.jar --httpPort=18080 --newxrHome=/tmp/newxr-try`, then run setup with "Add sample content".

## Conventions to keep

- **Per app**: `backend/<app>/` holds entities, repositories, a `*Service` that is the `@RestController` and a `HubContributor`, and `*SampleData` implementing `SampleDataContributor`. `frontend/src/app/<app>/` holds `*-api.ts`, pages and `*.routes.ts`, lazy-loaded in `app.routes.ts`.
- **Errors**: throw `ApiException` with a sentence the user can act on; it becomes `{status, message}`.
- **Long text**: `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)`. In JPQL use `lower(cast(x as String))` on those columns, and never `lower(:param)` alone (Hibernate 7 can't type it). Lower-case the parameter in Java instead.
- **Timestamps**: set `createdAt`/`updatedAt` *before* `save()` when back-dating (sample data); `createdAt` is not updatable.
- **Jackson 3**: missing primitives are allowed via `spring.jackson.deserialization.fail-on-null-for-primitives=false`.
- **CSP** is strict (`script-src 'self'`): no inline scripts; keep Angular `inlineCritical: false`.
- **Frontend**: standalone components, `ChangeDetectionStrategy.OnPush`, signals. Use design tokens (`var(--…)`) and existing global classes (`.page`, `.card`, `.table`, `.lozenge`…). Global class names such as `.check` already exist, so check `styles.scss` before naming a class. Don't bind `[class]` on `<nx-icon>`: it replaces the host class; wrap the icon instead.
- **Charts**: series colours `--series-1/--series-2`; always a legend, tooltip and table view.
- **Secrets**: never return a stored secret from the API. Webhook and invitation tokens are shown once and stored as SHA-256 hashes.
- **Public endpoints** go under `/api/public/**`. Webhooks called by other tools also need a CSRF exemption in `SecurityConfig`.

## Don'ts

- Don't add a feature that pretends to do something it can't. If something isn't supported (email, Git push, data copy), say so in the UI.
- Don't commit `dist/`, build output, `.env`, or a Newxr home directory.

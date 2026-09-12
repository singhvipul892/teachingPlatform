# Backend test suite

Black-box API tests for the Singh Sir backend, built on the Playwright test
runner in API mode (no browser).

## The constraint this is built around

There is **one environment: production**. That is not a gap to work around with
careful test data — the backend genuinely cannot undo what tests would write:

| Thing a test might create | Can it be removed? |
|---|---|
| A user (`POST /api/auth/signup`) | **No.** There is no user-delete endpoint anywhere. `AdminUserController` exposes only POST and GET. |
| A user with any payment order | **No, not even by hand.** `payment_orders.user_id` and `purchases.user_id` are `REFERENCES users(id)` with no `ON DELETE`, so the delete fails on a foreign key. |
| A course | **No.** `deleteCourse` sets `active = false`. The row is permanent and still shows in the admin panel, which lists inactive courses. |
| A password-reset request | Sends a **real SMS** through AWS SNS. Prod runs `SMS_MOCK=false`. |
| A video or PDF | Yes — these really are deleted, including the S3 objects. |

So the suite splits in two, and the split is enforced in code rather than by
discipline.

## The two targets

### `--project=prod` — read-only, always

Runs only specs tagged `@readonly`. Underneath that, `src/guards.ts` wraps the
request context so **any non-GET call throws before the request leaves your
machine**. A mistyped `post()` inside a `@readonly` spec aborts the run instead
of reaching production.

The single exception is `POST /api/auth/login`, which verifies a hash and mints
a JWT without touching a row. Without it there is no token, and the 401-vs-403
authorization matrix — the most valuable thing here — could not run at all.
That allowlist is one path long and has its own test.

```bash
npm run test:prod
```

### `--project=local` — the throwaway stack

Everything, including specs that create courses, videos, users and orders.
Points at `127.0.0.1:18080`, a stack that exists only while you are testing.

The database is a **tmpfs** — not a named volume — so it dies with the
container and can never be mistaken for `teacher_db`, which holds real student
data. Ports are deliberately odd (`55432`, `18080`) so this is never confused
with a real local stack.

The API image is **pulled, not built**. Gradle cannot start on this machine, but
the image CI already built runs fine.

```bash
npm run stack:pull   # fetch the latest CI-built image
npm run stack:up
npm test
npm run stack:down   # -v; nothing survives
```

Destructive specs use the `destructiveTest` fixture, which fails immediately if
the target is not loopback. Three independent things would all have to fail for
a destructive spec to touch production: the project grep, the fixture assertion,
and the request guard.

## One-time production setup

Create **one** permanent QA account by hand, through the normal signup page:

1. Sign up with an address you control, e.g. `qa@your-domain.example`.
2. Leave it as role `USER`. Do not promote it, and never buy a course with it.
3. Put the credentials in `tests/.env` (copy `.env.example`).
4. For CI, add them as repository secrets `QA_USER_USERNAME` / `QA_USER_PASSWORD`.

That one row is the suite's entire production footprint. It is created
deliberately by you, not by a test run, and it is never deleted.

`access-control.spec.ts` asserts the account is still a plain `USER` — if it
ever gets promoted, every 403 assertion would silently stop testing anything.

## Layout

```
tests/
├── docker-compose.test.yml   throwaway postgres + the CI-built API image
├── playwright.config.ts      the local / prod projects
├── specs/
│   ├── smoke.spec.ts             @readonly  post-deploy check
│   ├── access-control.spec.ts    @readonly  the 401/403 matrix
│   ├── auth.spec.ts              @readonly  login only
│   ├── catalog.spec.ts           @readonly  response shapes the app depends on
│   ├── guard.spec.ts             @readonly  proves the safety net works
│   ├── admin-crud.spec.ts        @destructive
│   ├── payment.spec.ts           @destructive
│   └── signup.spec.ts            @destructive
└── src/
    ├── config.ts             loopback-vs-real decision, credentials
    ├── guards.ts             the read-only enforcement
    ├── fixtures.ts           request contexts and tokens
    └── seed-ephemeral.ts     waits for the stack, seeds an admin + a student
```

## Known failing test

`access-control.spec.ts` carries one `test.fail()` — an expected failure for a
real bug: `GET /api/videos/{videoId}/pdfs/{pdfId}/download` returns a presigned
S3 URL with no authorization at all, so any paid PDF can be downloaded without
an account. Playwright reports an **unexpected pass** the moment it is fixed;
delete the `test.fail()` line then.

## Notes

- `fullyParallel` is off and `workers` is 1: destructive specs share one
  database.
- The throwaway stack runs `SPRING_PROFILES_ACTIVE=prod` on purpose, so it
  exercises the same configuration path production does. It also starts from an
  empty schema built by `backend/docker/init`, so `ddl-auto: validate` failing
  there means the entities and that init script have drifted apart.

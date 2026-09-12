# Backlog — Decided but Not Built

Things we agreed are worth doing but consciously deferred. Each entry says what it
is, why it was skipped, and what still needs deciding before it can be picked up.

Nothing here is in progress. When an item is built, move it out of this file.

---

## B1. Expiry reminder outside the app

**Area:** Course validity
**Raised:** 2026-09-10
**Status:** Deferred — not part of the initial validity release

### What it is

When a student's course access is about to expire, tell them through a channel
that reaches them even if they haven't opened the app.

### Why it matters

The validity feature warns the student inside the app only. A student who hasn't
opened the app in a few weeks gets no warning at all — they simply discover one
day that the course is gone. That is the most likely source of support complaints
and the most likely lost renewal.

### Why it's deferred

There is no notification channel in the product today:

- The Android app requests only the `INTERNET` permission — no push/FCM setup.
- The backend sends no email to students at any point, including after purchase.

So this is new infrastructure, not a change to an existing screen. Validity can
ship without it, and the reminder can be added later without reworking anything
that gets built now.

### Still to decide

- Channel — email, push notification, or WhatsApp/SMS.
- Timing — how many reminders and when (e.g. 7 days before, and on the day).
- Whether the same channel should also handle purchase confirmations, which would
  make the setup worth considerably more than expiry reminders alone.

---

## B2. PDF downloads are not gated at all

**Area:** Access control
**Raised:** 2026-09-10
**Status:** Deferred — pre-existing, surfaced again by course validity

### What it is

`GET /api/videos/{videoId}/pdfs/{pdfId}/download` performs no authorization
whatsoever. `PdfDownloadService.getDownloadUrl` takes no user, checks no
purchase, and `SecurityConfig` makes `GET /api/videos/**` public — so anyone who
knows a pdf id gets a presigned S3 URL, logged in or not.

`tests/specs/access-control.spec.ts` already documents this with a deliberately
failing test. Delete its `test.fail()` line when the endpoint is fixed.

### Why it matters now

Course validity blocks an expired student at the video listing, which is where
the app discovers PDFs — so in practice an expired student cannot find new PDFs
to download. That is a UI-level block, not an enforced one. The endpoint itself
stays open to anybody.

### Why it's deferred

Unrelated to validity: it was open before, and validity neither worsens nor
depends on it. Fixing it means threading the authenticated user into the
download path and checking the purchase — the same shape as
`VideoCatalogService.getVideosByCourse` — which is its own change with its own
testing.

---

## B3. Revenue figures drift once renewals exist

**Area:** Admin reporting
**Raised:** 2026-09-10
**Status:** Deferred

### What it is

The admin dashboard computes revenue in the browser as
`course.pricePaise × course.studentCount`, summed over courses
(`web/admin/index.html`).

### Why it matters

It was already approximate — it ignores admin-tagged enrolments, which are
recorded at ₹0, and it uses the course's *current* price rather than what each
student actually paid. Course validity adds a third source of drift: when an
expired student buys again, that is a second payment against the same enrolment
row, and `studentCount` cannot see it. The reported figure will read low.

### The real fix

Revenue should be summed from `payment_orders` (or `purchases.amountPaise`)
server-side, where every paid order is already recorded individually, rather
than inferred from a head count. That is a small backend endpoint plus a change
to the dashboard, and it would make the number correct on all three counts at
once.

---

## B4. Tell a student when their access is removed

**Area:** Enrolment
**Raised:** 2026-09-12
**Status:** Deferred — current silence is now a decision, not an oversight

### What it is

When an admin removes a student from a course, the student is told nothing. The
course simply disappears from their list the next time the app loads.

### Why it matters

Expiry is explained and removal is not, which is backwards — expiry is the one the
student can predict. `/api/user/courses` returns `expired`, `expiryDate` and
`daysRemaining`, and the app shows an expired course greyed with its date, so the
student knows what happened and can renew. Removal has no equivalent: the row is
excluded from the response entirely, the Android app has no concept of a removed
enrolment (`UserCoursesResponse.kt` carries no such field), and the student is left
to guess whether they were removed, whether it expired, or whether something broke.

The likely support message is "my course disappeared", which costs more to answer
than it would have cost to say.

### Why it's deferred

Two reasons, and the first is a decision rather than a constraint:

- **Silence is deliberate for now.** Removals are usually corrections or refunds,
  and a notification is not always wanted. Keeping the current behaviour is the
  chosen default until there is a reason to change it.
- **There is still no channel.** Same blocker as [B1](#b1-expiry-reminder-outside-the-app):
  no push setup, no student email. Anything that reaches a student who is not
  currently in the app is new infrastructure.

These two share a channel entirely. Whoever builds B1 should pick up B4 in the
same pass — the second message costs very little once the first one can be sent.

### Still to decide

- Whether removal is announced at all, or only shown in-app when the student next
  opens it (a removed course listed as removed, rather than vanishing).
- Whether the reason is included. An admin-entered note would be more use than a
  bare "access withdrawn", but it is a field that has to be written every time.
- Whether a restore is announced too. Restoring is an undo, so a student who was
  never told about the removal should probably not be told about the undo either.

### Related

Removal now keeps the enrolment row (`purchases.unenrolled_at`, migration
`002_purchase_unenrolment.sql`), so the data needed to show "removed on <date>" to
a student already exists. Only the surfacing is missing.

---

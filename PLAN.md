# SSC CGL Learning Platform — Master Build Plan
> This file lives in the project root. Claude Code reads and updates it every session.
> Last Updated: —
> Current Focus: Phase 1 — Get Existing App Live

---

## How Claude Code Uses This File

At the start of every session:
```
Read PLAN.md and project_overview. Understand what is built and what is pending.
Then build: [task name]
After finishing, update PLAN.md — mark task as done, add notes if any decision was made.
```

---

## Project Overview

A learning platform for SSC CGL Math students in India.
Teacher shares video lectures + PDFs with students.
Currently done manually via Telegram — we are replacing that with this app.

**Two parts:**
- `backend/` → Java Spring Boot REST API
- `android/` → Android app (Java)

---

## Tech Stack

| Layer | Choice |
|---|---|
| Backend | Java Spring Boot |
| Android | Java (Android SDK) |
| Payments | Razorpay (India-first, UPI + cards) |
| Admin Panel | Simple HTML/JS webpage calling existing backend APIs |

---

## PHASE 1 — Get Existing App Live
> Only priority right now. Do not touch Phase 2 until this is live.

### What Is Already Working
| Feature | Status |
|---|---|
| Teacher uploads video + PDF via Swagger | `[x] Done` |
| Students can download and view PDF in app | `[x] Done` |
| Basic course listing in app | `[x] Done` |

---

### P1.0 — Architecture Decision
> No payments inside Android app (Google Play policy).
> Payment happens on website only. App is for content consumption only.

---

### P1.1 — Razorpay Payment Integration
| Task | Status | Notes |
|---|---|---|
| Add Razorpay dependency to backend | `[x] Done` | razorpay-java 1.4.7 in backend/build.gradle |
| Create `POST /api/payment/create-order` — creates Razorpay order per course | `[x] Done` | |
| Create `courses` table + `purchases` table with course_id | `[x] Done` | docker/init/003_purchases.sql |
| Create `POST /api/payment/verify` — verify signature, record purchase | `[x] Done` | |
| Android — Razorpay SDK | `~~REMOVED~~` | Moved to web — Google Play billing policy |
| Android — payment flow on course screen | `~~REMOVED~~` | Moved to web — Google Play billing policy |

**Keys needed (you provide, never commit to git):**
```
RAZORPAY_KEY_ID=your_key
RAZORPAY_KEY_SECRET=your_secret
```

---

### P1.2 — Access Validation
| Task | Status | Notes |
|---|---|---|
| Backend — GET /api/user/courses returns purchased section names | `[x] Done` | |
| Android — call GET /api/user/courses on startup | `[x] Done` | MainActivity.kt |
| Android — store purchased section names in SessionManager | `[x] Done` | |
| Android — show "Purchase at website" dialog if section not owned | `[x] Done` | HomeScreen.kt |
| Test full flow — unpaid user blocked, paid user gets access | `[ ] To Do` | |

---

### P1.3 — Student Web Page
> Students pay and access content via browser. Mobile responsive — they will open this on their phone.

| Task | Status | Notes |
|---|---|---|
| Course listing page (public, no login required) | `[x] Done` | web/student/index.html |
| Course detail page + Razorpay checkout button | `[x] Done` | |
| After payment, backend unlocks account access | `[x] Done` | POST /api/payment/verify |
| Mobile responsive design | `[x] Done` | |
| Login / signup on web page (same account as app) | `[x] Done` | |

---

### P1.4 — Admin Panel (Teacher Dashboard)
> Teacher manages courses without Swagger.
> Build as a single HTML file + vanilla JS. No framework needed.

| Task | Status | Notes |
|---|---|---|
| Simple teacher login (single admin account) | `[ ] To Do` | |
| List all courses — title, price, published status | `[ ] To Do` | |
| Add new course — title, description, price, upload video + PDF | `[ ] To Do` | |
| Edit course details | `[ ] To Do` | |
| Delete / unpublish course | `[ ] To Do` | |
| View enrolled students per course | `[ ] To Do` | |

---

### P1.5 — Go Live Checklist
| Task | Status | Notes |
|---|---|---|
| Backend deployed and stable | `[ ] To Do` | |
| Android APK tested end-to-end on real device | `[ ] To Do` | |
| Teacher has tested admin panel — can upload a course | `[ ] To Do` | |
| One real student has paid and accessed a course | `[ ] To Do` | |
| Razorpay test mode switched to live mode | `[ ] To Do` | |

---

## PHASE 2 — Exam Generator (Teacher Studio)
> Start only after Phase 1 is live and teacher is actively using the app.

### What It Does
Teacher inputs topic + difficulty + question count + sample questions
→ AI generates questions → Teacher reviews → Publishes exam to students

### P2.1 — AI Generation Core
| Task | Status | Notes |
|---|---|---|
| Design + test prompt for SSC CGL Math question generation | `[ ] To Do` | |
| Lock question output JSON schema | `[ ] To Do` | |
| Build `POST /api/generate-questions` endpoint | `[ ] To Do` | |
| Validation + retry for malformed AI responses | `[ ] To Do` | |

**Question JSON Schema:**
```json
{
  "question": "string",
  "options": { "A": "string", "B": "string", "C": "string", "D": "string" },
  "correct": "A | B | C | D",
  "explanation": "string",
  "topic": "string",
  "difficulty": "easy | medium | hard"
}
```

### P2.2 — Teacher Studio UI
| Task | Status | Notes |
|---|---|---|
| Input form — topic, difficulty, count, sample questions | `[ ] To Do` | |
| Question review cards — Accept / Reject / Regenerate per card | `[ ] To Do` | |
| Inline edit before accepting | `[ ] To Do` | |
| Exam builder — arrange questions, set title + duration | `[ ] To Do` | |
| Publish exam — generates shareable code for students | `[ ] To Do` | |

### P2.3 — Student Exam Taking
| Task | Status | Notes |
|---|---|---|
| Student joins via share code | `[ ] To Do` | |
| Exam UI — one question at a time, timer, progress bar | `[ ] To Do` | |
| Results screen — score, correct answers, explanations | `[ ] To Do` | |

---

## PHASE 3 — Student Instant Exam Engine
> Start only after teacher has 50+ questions per topic in the bank.

- [ ] Per-student question history tracking (no repeats)
- [ ] Auto exam generation by topic + difficulty
- [ ] Student progress dashboard — accuracy, weak topics

---

## PHASE 4 — Subscriptions + Polish
> Start only after Phase 3 validated with real students.

- [ ] Subscription plans — monthly / yearly / per exam
- [ ] Course + exam bundle pricing
- [ ] Full student accounts with history
- [ ] Teacher analytics — attempts, scores, popular topics

---

## Decisions Log
> Claude Code: append a row here when a significant technical decision is made.

| Date | Decision | Reason |
|---|---|---|
| — | Razorpay over Stripe | India-first, supports UPI, simpler Android SDK |
| — | Admin panel as plain HTML | No framework overhead, teacher just needs it to work |
| — | Phase 1 before exam generator | Teacher's daily pain is Telegram sharing, not exam creation |
| — | No vector DB until Phase 3+ | Not needed until question bank exceeds 5000+ questions |
| 2026-03-08 | Payments moved to web page — Google Play billing policy | Android in-app purchases for digital goods require Google Play Billing. Razorpay checkout runs in browser at /student. App calls GET /api/user/courses to know what is unlocked. |

---

## What to Ignore Until Later
- Vector DB / Pinecone
- Automated video processing
- Complex analytics dashboard
- PDF bulk ingestion pipeline
- Mobile app for exam taking (web first)

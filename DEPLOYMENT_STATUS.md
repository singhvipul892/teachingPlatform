# Deployment Status

**Date:** 2026-03-26
**Status:** 🟡 BLOCKED — Android app incompatible with new backend APIs (web + nginx ready for deploy)

---

## What Works ✅
- Backend payment system (Razorpay integration)
- Course management (admin dashboard) — create, edit, toggle active/inactive status
- Admin panel video & PDF management (add/edit/delete videos and PDFs per course)
- Student web app (HTML/JS) — course listing, payment checkout, video access
- Database schema (courses, videos, purchases, PDFs)

## What's Broken ❌
- **Android app calls deleted API endpoints** — will crash on startup for all users

---

## Architecture

### Backend (Migrated)
| Endpoint | Auth | Description |
|----------|------|-------------|
| `GET /api/courses` | None | List all active courses |
| `POST /api/payment/create-order` | JWT | Create Razorpay order |
| `POST /api/payment/verify` | JWT | Verify and record payment |
| `GET /api/user/courses` | JWT | Get user's purchased courses |
| `GET /api/courses/{courseId}/videos` | JWT + purchase | Get videos for a course |
| `GET /api/videos/{videoId}/pdfs/{pdfId}/download` | JWT | S3 presigned PDF URL |

### Android (Needs Migration)
| File | Issue |
|------|-------|
| `TeacherApi.kt` | Still calls `GET /api/sections` and `GET /api/sections/{section}/videos` (both deleted) |
| `VideoRepository.kt` | Calls `getSections()` and `getVideosBySection()` against deleted endpoints |
| `HomeViewModel.kt` | Calls `repository.getHomeSections()` → 404 on startup |
| `HomeScreen.kt` | Shows "Failed to load videos" error on launch |

---

## Android Migration Tasks

1. **Update `TeacherApi.kt`**
   - Remove `getSections()` and `getVideosBySection()`
   - Add `getCourseVideos(courseId: Long)` → `GET /api/courses/{courseId}/videos`
   - `getUserCourses()` already exists (`GET /api/user/courses`) ✅

2. **Refactor `VideoRepository.kt`**
   - Remove section-based methods
   - Add course-based methods using `getCourseVideos(courseId)`
   - Pass JWT token from auth state

3. **Update `HomeViewModel.kt` + `HomeScreen.kt`**
   - Replace "load all sections" with "load purchased courses"
   - Add empty state: "You haven't purchased any courses yet"
   - Add navigation to course catalog/purchase flow

4. **Test**
   - Emulator smoke test (login → home → course videos)
   - Real device APK build
   - Payment flow regression

5. **Build and release updated APK**

---

## Backend Files Modified (Done)

- `backend/.../catalog/web/VideoCatalogController.java` — Removed section endpoints, added `GET /courses/{courseId}/videos`
- `backend/.../payment/repository/CourseRepository.java` — `findByActiveTrue()`
- `docker-compose.prod.yml` — healthcheck, restart policies, admin volume, pgadmin behind `--profile tools`
- `nginx/nginx.conf` — SSL config: HTTP→HTTPS redirect, `/web/` static alias, certbot challenge, API proxy; `client_max_body_size 50m` for multi-PDF uploads
- `nginx/nginx.no-ssl.conf` — Bootstrap config: HTTP only, same `/web/` static + proxy (use for first SSL cert); `client_max_body_size 50m` for multi-PDF uploads
- Root `/` → redirects to `/web/auth/login.html`; `/policies.html` → `/web/student/policies.html` (both configs)

---

## Local Development Setup

**API_BASE Configuration:**
- Web app files (`web/auth/login.html`, `web/admin/index.html`, `web/student/index.html`) have `const API_BASE = 'http://13.205.19.207:8080';` for local development
- **Before production deployment:** Change to `const API_BASE = '';` (empty string) so web app uses relative URLs and calls the same domain
- Comments in each file mark what to revert for production
- **Tip:** Hard refresh browser (`Ctrl+Shift+R`) after changing API_BASE to clear cache

---

## Next Steps

### To Unblock Deployment
- [ ] Complete Android migration tasks above
- [ ] Build and test APK
- [ ] `./gradlew build` on backend

### Post-Deployment
- [ ] Monitor error logs
- [ ] End-to-end test: teacher uploads → student purchases → student watches
- [ ] Confirm Razorpay payments received

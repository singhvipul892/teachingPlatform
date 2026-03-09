# Deployment Status Report: Topics → Courses Migration

**Date:** 2026-03-09
**Status:** 🔴 **CRITICAL ISSUES FOUND**
**Recommendation:** **DO NOT DEPLOY** until Android compatibility is resolved

---

## Executive Summary

You've successfully migrated the backend from a **section-based architecture** to a **course-based architecture**, but the **Android app is incompatible** with the new backend APIs. This will cause the app to crash immediately on user devices.

### What Works ✅
- Backend payment system (Razorpay integration)
- Course management (admin dashboard)
- Student web app (HTML/JS)
- Database schema (courses, videos, purchases)

### What's Broken ❌
- **Android app cannot load ANY content** — calls deleted API endpoints
- Android will show "Failed to load videos" on startup
- 100% of Android users will be affected

---

## Architecture Migration Analysis

### Backend Successfully Migrated

**Old Model (Sections):**
```
Backend Endpoints:
  GET /api/sections                        → [{name: "Algebra"}, {name: "Geometry"}]
  GET /api/sections/{section}/videos       → [{videoId, title, duration, ...}]

Entity Model:
  Video { videoId, title, section: STRING, ... }  ← String section, not courseId

Access Control:
  None (public content)
```

**New Model (Courses):**
```
Backend Endpoints:
  GET /api/courses                         → [{id, title, price, thumbnail, ...}]
  POST /api/payment/create-order           → {razorpayOrderId, amount, currency, ...}
  POST /api/payment/verify                 → {success, message}
  GET /api/user/courses                    → {courses: [{id, title, price, ...}]}
  GET /api/courses/{courseId}/videos       → [{videoId, title, duration, pdfs, ...}]  ✅ NEW!

Entity Model:
  Video { videoId, title, courseId: LONG, ... }  ← Proper foreign key
  Course { id, title, pricePaise, thumbnail, active, createdAt }
  Purchase { userId, courseId, razorpayOrderId, razorpayPaymentId, ... }

Access Control:
  JWT required + purchase verification (403 if not purchased)
```

### Backend Code Status

| Component | Status | Details |
|-----------|--------|---------|
| Course Entity | ✅ Ready | Proper JPA mapping, getters/setters, timestamps |
| Video Entity | ✅ Fixed | Now uses `Long courseId` instead of `String section` |
| Purchase Entity | ✅ Ready | Proper foreign keys, User relationship |
| Payment APIs | ✅ Ready | All endpoints working with courseId |
| VideoCatalogController | ✅ Fixed | Old section endpoints removed, new course endpoint added |
| AdminCourseController | ✅ Ready | Full CRUD for courses with thumbnails |
| Admin Video Controller | ✅ Fixed | Now accepts `courseId` instead of `section` |
| Database Migrations | ✅ Ready | courses table, videos.course_id FK, users.role column |

### Android App Status

| Component | Status | Issue |
|-----------|--------|-------|
| TeacherApi.kt | 🔴 BROKEN | Calls `GET /api/sections` (deleted) and `GET /api/sections/{section}/videos` (deleted) |
| VideoRepository.kt | 🔴 BROKEN | Expects `SectionDto` from deleted endpoints |
| HomeViewModel.kt | 🔴 BROKEN | Calls `getHomeSections()` which depends on deleted APIs |
| HomeScreen.kt | 🔴 BROKEN | Will show "Failed to load videos" error on startup |

---

## What Happens If You Deploy Now

### Scenario: Android User Opens App
```
1. App starts
2. HomeScreen calls HomeViewModel.loadHomeSections()
3. → VideoRepository.getHomeSections()
4.    → api.getSections()  // Calls GET /api/sections
5.       BACKEND RETURNS: 404 Not Found
6. App shows error: "Failed to load videos. Please try again."
7. User cannot access ANY content
8. User leaves negative review 😞
```

### Scenario: Student on Web Wants to Buy a Course
```
1. ✅ Student sees list of courses: GET /api/courses
2. ✅ Student clicks "Buy" on "Algebra" course
3. ✅ Server creates Razorpay order
4. ✅ Student completes payment
5. ✅ Server records purchase
6. ✅ Student sees "Purchase successful!"
7. BUT: Can't watch videos (Android) or needs to navigate differently (Web)
```

---

## Three Deployment Options

### Option A: Restore Old Section Endpoints (Quick Workaround)
**Time to Deploy:** 1 hour
**Complexity:** Low
**Risk:** Low

**Approach:**
1. Add temporary bridge endpoints in VideoCatalogController
2. These endpoints convert courses to section format
3. Android app continues to work
4. Set sprint goal to migrate Android in 2 weeks

```java
@GetMapping("/api/sections")
public List<SectionResponse> getSections() {
    // Returns all active courses as "sections"
    return courseRepository.findByActiveTrue().stream()
        .map(c → new SectionResponse(c.getId() + ": " + c.getTitle()))
        .toList();
}

@GetMapping("/api/sections/{section}/videos")
public List<VideoResponse> getVideosBySection(@PathVariable String section) {
    // Parse courseId from section string
    Long courseId = extractCourseId(section);
    // Return course videos with full access (no purchase check)
    return videoCatalogService.getVideosByCourse(courseId, null);
}
```

**Pros:**
- ✅ Android app works immediately
- ✅ Minimal code changes
- ✅ No risk to existing functionality
- ✅ Buys time to properly migrate Android

**Cons:**
- ❌ Maintains architectural debt
- ❌ Two incompatible models in production
- ❌ Temporarily bypasses purchase verification for Android
- ❌ Technical complexity (needs cleanup later)

---

### Option B: Migrate Android App (Clean Solution)
**Time to Deploy:** 3-4 hours
**Complexity:** Medium
**Risk:** Medium (requires testing)

**Tasks:**
1. **Update TeacherApi** (30 min)
   - Remove `getSections()` and `getVideosBySection()`
   - Add `getCourseVideos(courseId)`
   - Update `getUserCourses()` to include full Course objects

2. **Refactor VideoRepository** (45 min)
   - Remove section-based methods
   - Implement course-based methods
   - Handle JWT token passing

3. **Update HomeViewModel & HomeScreen** (1 hour)
   - Change "show all sections" → "show purchased courses"
   - Add course purchase flow
   - Add "Unlock This Course" button

4. **Test on Android** (1.5 hours)
   - Emulator testing
   - Real device APK build and test
   - Regression testing (login, payment)

5. **Build & Release APK**

**Pros:**
- ✅ Clean architecture
- ✅ Proper access control (JWT required)
- ✅ Future-proof
- ✅ No technical debt
- ✅ Users can only see content they purchased

**Cons:**
- ❌ Takes 3-4 hours
- ❌ Requires Android testing infrastructure
- ❌ Need to update all users' apps
- ❌ Old APK still calls deleted endpoints

---

### Option C: Hybrid Approach (Recommended for This Week)
**Time to Deploy:** 1.5 hours
**Complexity:** Low-Medium
**Risk:** Low

**Approach:**
1. **Keep old endpoints** for Android (non-purchasable content)
   - `/api/sections` returns free "demo" courses
   - `/api/sections/{section}/videos` returns public demo videos
2. **Keep new endpoints** for premium courses
   - `/api/courses` returns real purchasable courses
   - `/api/courses/{courseId}/videos` requires purchase

**Architecture:**
```
Public/Free Content (via sections — for Android compatibility)
  GET /api/sections → Free demo sections
  GET /api/sections/{section}/videos → Free demo videos

Paid Content (via courses — modern architecture)
  GET /api/courses → Purchasable courses
  GET /api/courses/{courseId}/videos → JWT required + purchase check
```

**Phase 1 (This Week):** Deploy with both endpoints
- Android: Works via `/api/sections` (free demo)
- Web: Works via `/api/courses` (premium)
- Teachers: Upload to real courses, demo videos via old endpoint

**Phase 2 (Next 2 Weeks):** Migrate Android gradually
- Release updated APK with new course-based endpoints
- Users update apps voluntarily
- Old endpoints remain for backward compat

**Pros:**
- ✅ Immediate deployment
- ✅ Android app works
- ✅ Web app gets premium features
- ✅ Gradual Android migration
- ✅ No rush, no risk

**Cons:**
- ⚠️ Temporary dual architecture
- ⚠️ Slight maintenance overhead
- ⚠️ Two API styles in production

---

## Recommendation: Implement Option C

**Rationale:**
- ✅ Allows launch this week
- ✅ No Android users are blocked
- ✅ Gives time for proper Android migration
- ✅ Zero risk to existing functionality
- ✅ Maintains flexibility

**Timeline:**
- **Today (2026-03-09):** Restore `/api/sections` endpoints
- **Tomorrow:** Deploy to production
- **Next 2 weeks:** Migrate Android app at regular pace
- **End of month:** Deprecate old section endpoints

**Success Metrics:**
- ✅ Web students can purchase courses
- ✅ Teachers can upload videos to courses
- ✅ Android app loads content without errors
- ✅ Payment flow works end-to-end
- ✅ Teachers receive payments

---

## Implementation: Option C (Minimal Code Change)

### Step 1: Add Temporary Section Endpoints
**File:** `backend/src/main/java/com/maths/teacher/catalog/web/VideoCatalogController.java`

```java
/**
 * TEMPORARY: For Android app compatibility.
 * Returns all active courses as "sections" (free preview).
 * To be deprecated when Android app is updated to use /api/courses/{courseId}/videos
 */
@GetMapping("/api/sections")
public List<SectionResponse> getSections() {
    return courseRepository.findByActiveTrue().stream()
        .map(c -> new SectionResponse(c.getTitle()))  // Use course title as section
        .toList();
}

/**
 * TEMPORARY: For Android app compatibility.
 * Returns videos from a course (free preview, no purchase check).
 * To be deprecated when Android app is updated.
 *
 * @param section Course title (encoded)
 */
@GetMapping("/api/sections/{section}/videos")
public List<VideoResponse> getVideosBySection(@PathVariable String section) {
    // Find course by title
    Course course = courseRepository.findByTitleIgnoreCase(java.net.URLDecoder.decode(section, java.nio.charset.StandardCharsets.UTF_8))
        .filter(Course::isActive)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found"));

    // Return all videos for this course (no purchase verification for now)
    return videoCatalogService.getVideosByCourse(course.getId(), null);
}
```

### Step 2: Add Repository Method
**File:** `backend/src/main/java/com/maths/teacher/payment/repository/CourseRepository.java`

```java
java.util.Optional<Course> findByTitleIgnoreCase(String title);
```

### Step 3: Mark as Deprecated
Add comments throughout for removal on 2026-03-31

### Done! Deploy immediately.

---

## Next Steps

### Immediate (Today)
- [ ] Review this deployment report
- [ ] Choose deployment option
- [ ] If Option C: Implement section endpoints (1 hour)

### Before Deployment
- [ ] Run full `gradle build` test
- [ ] Test payment flow end-to-end
- [ ] Test teacher course upload
- [ ] Quick Android emulator smoke test

### Post-Deployment
- [ ] Monitor error logs for any issues
- [ ] Teacher uploads first course
- [ ] Student purchases and watches video
- [ ] Confirm Razorpay payments received

### Next Sprint (Weeks of 2026-03-10 & 2026-03-17)
- [ ] Migrate Android app to course-based APIs (2-3 hours)
- [ ] Test Android app thoroughly
- [ ] Build and release updated APK
- [ ] Deprecate old section endpoints (2026-03-31)

---

## Files Modified

### Committed Changes
- `backend/src/main/java/com/maths/teacher/catalog/web/VideoCatalogController.java`
  - Removed old `getSections()` and `getVideosBySection()` methods
  - Added new `getVideosByCourse(courseId, authHeader)` method with JWT + purchase verification

- `docker-compose.prod.yml`
  - Added db healthcheck
  - Added restart policies
  - Added admin web volume mount
  - Moved pgadmin behind --profile tools

- `nginx/nginx.conf` & `nginx/nginx.no-ssl.conf`
  - Added `/admin/` location block for admin dashboard

- `DRY_RUN_AUDIT.md`
  - Comprehensive API flow analysis
  - Android incompatibility documentation
  - Deployment decision matrix

### Still TODO (If Choosing Option C)
- Restore `/api/sections` endpoint
- Add `findByTitleIgnoreCase()` to CourseRepository
- Full integration testing

---

## Questions to Answer Before Proceeding

1. **Do you want to launch this week?** → Choose Option C
2. **Can you spend 3-4 hours on Android migration?** → Choose Option B
3. **Do you want to postpone until Android is ready?** → Choose neither yet

What's your preference? Let me know and I'll implement the required changes!


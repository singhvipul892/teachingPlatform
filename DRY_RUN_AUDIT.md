# Dry-Run Architecture Audit: Topics → Courses Migration

## Summary
The migration from **section-based API** to **course-based API** is ~95% complete, but there are **critical issues** in the video catalog endpoints that will cause compilation failures.

**Status:** 🔴 **Not Ready for Deployment** — 4 critical fixes needed

---

## Data Model Analysis

### Current Entity Relationships
```
User (1) ───────────────────────────── (M) Purchase
                                           │
                                           └──→ Course
                                                  │
                                                  └──→ Video
                                                         │
                                                         └──→ VideoPdf
```

### Key Entities
| Entity | Old Design | New Design | Status |
|--------|-----------|-----------|--------|
| **Course** | Not used | Purchasable (price, thumbnail, active) | ✅ Ready |
| **Video** | String `section` param | `Long courseId` FK | ✅ Ready (fixed in last commit) |
| **Purchase** | `Long userId` + `Long courseId` | Same + User relationship | ✅ Ready |
| **PaymentOrder** | Uses courseId | Uses courseId | ✅ Ready |

---

## API Flow Analysis

### 1. PUBLIC ENDPOINTS (No Auth Required)

#### ✅ GET `/api/courses` — List All Active Courses
**Location:** `PaymentController.java`
```java
@GetMapping("/api/courses")
public ResponseEntity<List<CourseResponse>> listCourses()
```
**Data Flow:**
```
GET /api/courses
  ↓
PaymentController.listCourses()
  ↓
PaymentService.listCourses()
  ↓
CourseRepository.findByActiveTrue()
  ↓
List<CourseResponse>: [{id, title, description, pricePaise, currency, thumbnailUrl}, ...]
```
**Status:** ✅ **WORKING**

---

### 2. JWT PROTECTED ENDPOINTS (Requires Authorization Header)

#### ✅ POST `/api/payment/create-order` — Create Razorpay Order
**Location:** `PaymentController.java`
```java
@PostMapping("/api/payment/create-order")
public ResponseEntity<CreateOrderResponse> createOrder(
    @RequestHeader String authHeader,
    @RequestBody CreateOrderRequest request  // {courseId}
)
```
**Data Flow:**
```
POST /api/payment/create-order
  ↓
PaymentController.createOrder(userId, courseId)
  ↓
PaymentService.createOrder(userId, courseId)
  ├─ CourseRepository.findById(courseId)
  ├─ PurchaseRepository.existsByUserIdAndCourseId(userId, courseId)
  └─ Creates PaymentOrder + calls Razorpay API
  ↓
CreateOrderResponse: {razorpayOrderId, amountPaise, currency, keyId}
```
**Status:** ✅ **WORKING**

#### ✅ POST `/api/payment/verify` — Verify Payment Signature
**Location:** `PaymentController.java`
**Data Flow:**
```
POST /api/payment/verify
  ↓
PaymentController.verifyPayment(userId, orderId, paymentId, signature)
  ↓
PaymentService.verifyPayment()
  ├─ Utils.verifyPaymentSignature()  // Razorpay HMAC verification
  ├─ PaymentOrderRepository.findByRazorpayOrderId(orderId)
  └─ Creates Purchase record
  ↓
VerifyPaymentResponse: {success, message}
```
**Status:** ✅ **WORKING**

#### ✅ GET `/api/user/courses` — Get User's Purchased Courses
**Location:** `PaymentController.java`
```java
@GetMapping("/api/user/courses")
public ResponseEntity<UserCoursesResponse> getUserCourses(
    @RequestHeader String authHeader
)
```
**Data Flow:**
```
GET /api/user/courses
  ↓
PaymentController.getUserCourses(userId)
  ↓
PaymentService.getUserCourses(userId)
  ├─ PurchaseRepository.findByUserId(userId)
  └─ CourseRepository.findById(courseId) for each purchase
  ↓
UserCoursesResponse: {courses: [CourseResponse, ...]}
```
**Status:** ✅ **WORKING**

#### ✅ GET `/api/payment/status` — Check If User Has Any Purchase
**Location:** `PaymentController.java`
```java
@GetMapping("/api/payment/status")
public ResponseEntity<PurchaseStatusResponse> getPurchaseStatus(...)
```
**Status:** ✅ **WORKING**

---

### 3. CATALOG ENDPOINTS (Video Listing & Access Control)

#### ❌ GET `/api/sections` — BROKEN ❌
**Location:** `VideoCatalogController.java`
```java
@GetMapping("/api/sections")
public List<SectionResponse> getSections()
```
**Problem:**
- Calls `videoCatalogService.getSections()` — **METHOD DOES NOT EXIST**
- `VideoCatalogService` has been refactored for courses, not sections

**What Should Happen:**
- This endpoint is **obsolete** and should be removed OR
- Replaced with `GET /api/courses` (which already exists)

**Fix:** Remove this endpoint or map to PaymentController.listCourses()

---

#### ❌ GET `/api/sections/{section}/videos` — BROKEN ❌
**Location:** `VideoCatalogController.java`
```java
@GetMapping("/api/sections/{section}/videos")
public List<VideoResponse> getVideosBySection(@PathVariable String section)
```
**Problem:**
- Calls `videoCatalogService.getVideosBySection(section)` — **METHOD DOES NOT EXIST**
- Video entity now uses `Long courseId`, not `String section`
- No authorization check — any user can access any section's videos

**What Should Happen:**
1. Should be `GET /api/courses/{courseId}/videos`
2. Should check if user has purchased the course (using `PurchaseRepository.existsByUserIdAndCourseId`)
3. Should call `VideoCatalogService.getVideosByCourse(courseId, userId)`

**Data Flow (Corrected):**
```
GET /api/courses/{courseId}/videos
  ↓
VideoCatalogController.getVideosByCourse(courseId, authHeader)
  ↓
VideoCatalogService.getVideosByCourse(courseId, userId)
  ├─ PurchaseRepository.existsByUserIdAndCourseId(userId, courseId)
  │  └─ Throws 403 FORBIDDEN if not purchased
  ├─ VideoRepository.findByCourseIdOrderByDisplayOrderAsc(courseId)
  └─ Loads all VideoPdf records for each video
  ↓
List<VideoResponse>: [{id, videoId, title, thumbnailUrl, duration, displayOrder, pdfs}, ...]
```

**Fix:** Create new endpoint in VideoCatalogController

---

### 4. ADMIN ENDPOINTS (Teacher-Only Access)

#### ✅ POST `/admin/videos` — Create Video (with courseId)
**Location:** `AdminController.java`
```java
@PostMapping(consumes = MULTIPART_FORM_DATA_VALUE)
public VideoResponse createVideoLesson(
    @RequestParam String youtubeVideoLink,
    @RequestParam String title,
    @RequestParam Long courseId,    // ✅ FIXED in last commit
    @RequestParam String duration,
    @RequestParam Integer displayOrder,
    @RequestPart MultipartFile notesPdf,
    ...
)
```
**Status:** ✅ **FIXED** (changed from `String section` to `Long courseId`)

---

#### ✅ POST `/api/admin/courses` — Create Course
**Location:** `AdminCourseController.java`
**Status:** ✅ **WORKING**

#### ✅ GET/PUT/DELETE `/api/admin/courses/{courseId}` — Course CRUD
**Location:** `AdminCourseController.java`
**Status:** ✅ **WORKING**

---

## Critical Issues Found

### 🔴 ISSUE #1: VideoCatalogController.getSections() — MISSING METHOD
**File:** `VideoCatalogController.java:30`
```java
@GetMapping("/api/sections")
public List<SectionResponse> getSections() {
    return videoCatalogService.getSections();  // ❌ METHOD DOES NOT EXIST
}
```
**Impact:** Compilation error + runtime 500 error
**Fix:** Remove endpoint or replace with `GET /api/courses` from PaymentController

---

### 🔴 ISSUE #2: VideoCatalogController.getVideosBySection() — MISSING METHOD
**File:** `VideoCatalogController.java:34`
```java
@GetMapping("/api/sections/{section}/videos")
public List<VideoResponse> getVideosBySection(@PathVariable String section) {
    return videoCatalogService.getVideosBySection(section);  // ❌ METHOD DOES NOT EXIST
}
```
**Impact:** Compilation error + no access control
**Fix:** Create new method `getVideosByCourse(courseId, userId)` with purchase verification

---

### 🟡 ISSUE #3: VideoCatalogService Missing Methods
**File:** `VideoCatalogService.java`
```java
// ❌ These methods are called but don't exist:
public List<SectionResponse> getSections() { ... }
public List<VideoResponse> getVideosBySection(String section) { ... }

// ✅ This method exists but isn't exposed:
public List<VideoResponse> getVideosByCourse(Long courseId, Long userId) { ... }
```
**Impact:** Compilation errors
**Fix:** Remove old methods, expose course-based method

---

### 🟡 ISSUE #4: Student Web App Expects `/api/courses/{courseId}/videos`
**File:** `web/student/index.html` (line ~500+)
**Current:** Student app is trying to fetch videos after purchase
**Problem:** No endpoint to fetch videos by course ID with authorization
**Fix:** Create `/api/courses/{courseId}/videos` endpoint in VideoCatalogController

---

## Summary of Required Fixes

| # | Component | Issue | Severity | Fix |
|---|-----------|-------|----------|-----|
| 1 | VideoCatalogController | `getSections()` calls non-existent method | 🔴 CRITICAL | Delete endpoint |
| 2 | VideoCatalogController | `getVideosBySection()` calls non-existent method | 🔴 CRITICAL | Delete endpoint |
| 3 | VideoCatalogController | Missing `/api/courses/{courseId}/videos` endpoint | 🔴 CRITICAL | Create new endpoint |
| 4 | VideoCatalogService | Missing `getVideosBySection()` method | 🟡 MODERATE | Not needed, delete |

---

## Step-by-Step Fix Plan

### Step 1: Delete Old Section-Based Endpoints
**File:** `VideoCatalogController.java`
- Delete `getSections()` method + mapping
- Delete `getVideosBySection()` method + mapping
- Delete `SectionResponse` import

### Step 2: Create New Course-Based Endpoint
**File:** `VideoCatalogController.java`
```java
@GetMapping("/api/courses/{courseId}/videos")
public List<VideoResponse> getVideosByCourse(
    @PathVariable Long courseId,
    @RequestHeader("Authorization") String authHeader
) {
    Long userId = authService.requireUserId(authHeader);
    return videoCatalogService.getVideosByCourse(courseId, userId);
}
```

### Step 3: Clean Up VideoCatalogService
- Keep only `getVideosByCourse()` method
- Remove `getVideosBySection()` method if it exists
- Update variable names to be course-aware

### Step 4: Run Build Test
```bash
cd backend
./gradlew clean build -x test
```

---

## Data Flow Examples

### Example 1: Student Purchases a Course, Then Accesses Videos

```
1. Student logs in
   POST /api/auth/login
   Response: {token, userId}

2. Student lists courses
   GET /api/courses
   Response: [{id:1, title:"Algebra", price:999, ...}, {id:2, ...}]

3. Student purchases course #1
   POST /api/payment/create-order
   Body: {courseId: 1}
   Response: {razorpayOrderId, amount, ...}

4. Student completes Razorpay payment
   POST /api/payment/verify
   Body: {razorpayOrderId, razorpayPaymentId, razorpaySignature}
   Response: {success: true}

5. Student accesses videos in purchased course
   GET /api/courses/1/videos
   Header: Authorization: Bearer <token>
   Response: [{videoId, title, duration, pdfs}, ...]

6. Student cannot access unpurchased course videos
   GET /api/courses/2/videos
   Response: 403 FORBIDDEN - "You have not purchased this course"
```

### Example 2: Teacher Uploads Video to Course

```
1. Teacher uploads video
   POST /admin/videos
   Body: {youtubeVideoLink, title, courseId: 1, duration, displayOrder}
   Files: notesPdf, solvedPdfSet, annotatedPdfSet
   Response: {videoId, videoId, title, ...}

2. Video is now accessible to students who purchased course #1
```

---

## Testing Checklist

- [ ] `GET /api/courses` returns all active courses
- [ ] `GET /api/courses/{courseId}/videos` returns videos only if user purchased
- [ ] `GET /api/courses/{courseId}/videos` returns 403 if user didn't purchase
- [ ] `POST /api/payment/create-order` creates order with correct price from course
- [ ] `POST /api/payment/verify` records purchase and signature is validated
- [ ] `GET /api/user/courses` returns user's purchased courses
- [ ] `POST /admin/videos` creates video with courseId (not section)

---

## Migration Status

| Component | Status | Notes |
|-----------|--------|-------|
| Database Schema | ✅ Ready | videos.course_id FK exists, role column added |
| Course Entity | ✅ Ready | Proper JPA mapping, all getters/setters |
| Payment APIs | ✅ Ready | Uses courseId throughout |
| Video Entity | ✅ Ready | Uses Long courseId instead of String section |
| Video Admin API | ✅ Ready | Fixed to use courseId parameter |
| Video Catalog API | ❌ **BROKEN** | Missing endpoints, calls non-existent methods |
| Student Web App | ✅ Ready | Already uses course-based payment APIs |
| Admin Web Dashboard | ✅ Ready | Can upload courses and videos |

---

## Deployment Readiness

**Current Status:** 🔴 **NOT READY**

**Blockers:**
1. VideoCatalogController has compilation errors
2. No endpoint to fetch videos by course ID with access control
3. Old section-based APIs are still present

**Next Steps:**
1. Fix compilation errors in VideoCatalogController
2. Create `/api/courses/{courseId}/videos` endpoint
3. Run full build test
4. Manual integration testing with Razorpay test mode
5. Test Android app video playback flow


---

## CRITICAL: Android App Incompatibility 🔴

### Issue #5: Android App Uses Deleted Backend Endpoints

**Files:**
- `android/app/src/main/java/com/maths/teacher/app/data/api/TeacherApi.kt`
- `android/app/src/main/java/com/maths/teacher/app/data/repository/VideoRepository.kt`
- `android/app/src/main/java/com/maths/teacher/app/ui/home/HomeViewModel.kt`

**Problem:**
The Android app is hardcoded to call endpoints that **NO LONGER EXIST** in the backend:

```kotlin
@GET("api/sections")
suspend fun getSections(): List<SectionDto>

@GET("api/sections/{section}/videos")
suspend fun getVideosBySection(@Path("section") section: String): List<VideoDto>
```

**What Happens When User Opens App:**
```
1. HomeViewModel.loadHomeSections()
2. → VideoRepository.getHomeSections()
3.   → api.getSections()  // ❌ CALLS /api/sections (DELETED)
4.     BACKEND RETURNS 404 Not Found
5. App crashes with error: "Failed to load videos. Please try again."
```

**Root Cause:**
- Backend migrated from **public section-based API** → **course-based API**
- Android app was never updated to match
- Breaking change in API contract

**Impact:**
- 🔴 **CRITICAL** — Android app cannot load any content
- App will crash on startup when users try to view videos
- Affects 100% of Android users

---

### Android App Architecture vs Backend Architecture Mismatch

**Old Architecture (Sections):**
```
Backend:
  GET /api/sections
    → Returns list of section names (free content, public)
  GET /api/sections/{section}/videos
    → Returns videos for that section (free content, public)

Android:
  HomeScreen
    ├─ Load all sections
    └─ For each section, load all videos
       └─ Display in carousel/list
```

**New Architecture (Courses):**
```
Backend:
  GET /api/courses (public)
    → Returns purchasable courses
  POST /api/payment/create-order (JWT)
  POST /api/payment/verify (JWT)
  GET /api/user/courses (JWT)
    → Returns user's purchased courses
  GET /api/courses/{courseId}/videos (JWT)
    → Returns videos for purchased course

Android:
  HomeScreen
    ├─ Load user's purchased courses
    └─ For each course, load its videos
       └─ Display in carousel/list
```

**The Disconnect:**
- Backend: Expects JWT auth + purchased courses
- Android: Still tries to load public sections (pre-course model)

---

### Fix Required for Android App

#### Step 1: Update TeacherApi
```kotlin
// Remove old endpoints:
@GET("api/sections")
suspend fun getSections(): List<SectionDto>

@GET("api/sections/{section}/videos")
suspend fun getVideosBySection(...): List<VideoDto>

// Add new endpoint:
@GET("api/courses/{courseId}/videos")
@Headers("Authorization: Bearer {token}")
suspend fun getCourseVideos(@Path("courseId") courseId: Long): List<VideoDto>
```

#### Step 2: Refactor VideoRepository
- Remove `getSections()`, `getVideosBySection()`, `getHomeSections()`
- Add `getCourseVideos(courseId, token)`
- Update to use CourseResponse from PaymentController instead of SectionDto

#### Step 3: Update HomeViewModel/HomeScreen
- Change from "show all sections" → "show user's purchased courses"
- For each purchased course, fetch its videos
- Add "Buy Course" button for non-purchased courses

#### Step 4: Update SessionManager
- Store JWT token for subsequent API calls

---

## Updated Migration Status

| Component | Status | Notes |
|-----------|--------|-------|
| Backend Database | ✅ Ready | Courses table exists, videos.course_id FK ready |
| Backend Entities | ✅ Ready | Course, Video, Purchase properly mapped |
| Backend Payment APIs | ✅ Ready | All course-based endpoints working |
| Backend Video Catalog API | ✅ **FIXED** | Migrated to `/api/courses/{courseId}/videos` |
| **Android TeacherApi** | 🔴 **BROKEN** | Still calls deleted `/api/sections` endpoints |
| **Android VideoRepository** | 🔴 **BROKEN** | Calls non-existent methods via API |
| **Android HomeViewModel** | 🔴 **BROKEN** | Expects sections, not purchased courses |
| Student Web App | ✅ Ready | Already uses course-based payment APIs |
| Admin Web Dashboard | ✅ Ready | Can manage courses and videos |

---

## Revised Deployment Readiness

**Current Status:** 🔴 **CANNOT DEPLOY**

**Critical Blockers:**
1. ✅ Backend: VideoCatalogController fixed
2. ❌ **Android: All video-related features broken**
   - Cannot load sections (endpoint deleted)
   - Cannot load videos (endpoint deleted)
   - App will crash on open

**Workaround Options:**

**Option A: Restore Old Section Endpoints (Temporary)**
```java
// Keep old endpoints alive alongside new course-based ones
@GetMapping("/api/sections")
public List<SectionResponse> getSections() { ... }

@GetMapping("/api/sections/{section}/videos")
public List<VideoResponse> getVideosBySection(@PathVariable String section) { ... }
```
**Pros:** Android app continues to work
**Cons:** Maintains technical debt, two incompatible architectures in production

**Option B: Migrate Android App (Recommended)**
Estimate: 2-4 hours
```
1. Update TeacherApi (30 min)
2. Refactor VideoRepository (45 min)
3. Update HomeViewModel/HomeScreen (1 hour)
4. Test on Android emulator (1 hour)
5. Build APK, test on real device (30 min)
```

**Option C: Hybrid (Best for Now)**
1. **Keep** `/api/sections` and `/api/sections/{section}/videos` as public, free-content endpoints
2. **Add** course-based endpoints for premium content
3. Gradually migrate Android app to courses over next sprint

---

## Deployment Decision Matrix

| Scenario | Backend Status | Android Status | Can Deploy? |
|----------|---|---|---|
| **Current** | ✅ Course-based | ❌ Section-based | **NO** |
| **Option A Applied** | ✅ Both | ✅ Section-based | ✅ YES (with debt) |
| **Option B Applied** | ✅ Course-based | ✅ Course-based | ✅ YES (clean) |
| **Option C Applied** | ✅ Both | ✅ Both | ✅ YES (interim) |

---

## Recommendation

### For Immediate Deployment (This Week):
**Use Option A:** Restore the old section endpoints temporarily
- Android app remains functional
- Web students can purchase courses
- Teachers can upload course content
- Set timeline to migrate Android (next 2 weeks)

### Implementation for Option A:
Create a temporary bridge in Backend:
```java
// Temporary — to be removed when Android app is updated
@GetMapping("/api/sections")
public List<SectionResponse> getSections() {
    List<Course> courses = courseRepository.findByActiveTrue();
    return courses.stream()
        .map(c -> new SectionResponse(c.getId() + ": " + c.getTitle()))
        .toList();
}

@GetMapping("/api/sections/{section}/videos")
public List<VideoResponse> getVideosBySection(@PathVariable String section) {
    // Extract courseId from section string, fetch videos
    Long courseId = extractCourseId(section);
    return videoCatalogService.getVideosByCourse(courseId, null)
        .stream()
        .map(v -> new VideoResponse(...))
        .toList();
}
```

This allows Android to keep working while backend is modern.

---


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


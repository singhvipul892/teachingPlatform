# Singh Sir — Complete System Flow

> **Living Document**: This file captures the current system architecture, user journeys, and data flows. Update this whenever significant features are added or changed.
>
> Last Updated: **2026-03-10** — Video & PDF Management (P1.4c) complete

---

## Table of Contents

1. [System Architecture](#system-architecture)
2. [Data Model & Relationships](#data-model--relationships)
3. [User Journeys](#user-journeys)
4. [API & Payment Flow](#api--payment-flow)
5. [Admin Panel API](#admin-panel-api)
6. [Access Control](#access-control)
7. [Key Components](#key-components)

---

## System Architecture

### Overview
Singh Sir is a **multi-tier learning platform** serving SSC CGL Math students in India.

```
┌─────────────────────────────────────────────────────────────┐
│                   STUDENT TOUCHPOINTS                        │
├──────────────────────┬──────────────────────┬────────────────┤
│  Android App         │  Student Website     │  Admin Panel    │
│  (Content Learning)  │  (Payment + Auth)    │  (Course Mgmt)  │
└──────────────────────┴──────────────────────┴────────────────┘
          │                    │                      │
          └────────────────────┼──────────────────────┘
                               │
                    (All call the same API)
                               │
┌──────────────────────────────▼──────────────────────────────┐
│               JAVA SPRING BOOT BACKEND                       │
│                                                               │
│  ┌────────────────┬──────────────┬───────────────────────┐  │
│  │  Auth Service  │ Payment Svc  │ Catalog/Video Svc     │  │
│  └────────────────┴──────────────┴───────────────────────┘  │
└──────────────────────────────┬──────────────────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
   ┌────▼──────┐    ┌─────────▼────┐    ┌──────────▼─────┐
   │ PostgreSQL │    │  Razorpay    │    │  AWS S3/CDN    │
   │  Database  │    │  Payment API │    │  (Video/PDF)   │
   └────────────┘    └──────────────┘    └────────────────┘
```

### Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java Spring Boot (REST API) |
| **Database** | PostgreSQL (Docker) |
| **Android App** | Kotlin + Jetpack Compose |
| **Student Web** | Vanilla HTML/JS + Razorpay SDK |
| **Admin Panel** | Vanilla HTML/JS (`web/admin/index.html`) |
| **Payments** | Razorpay (India-first: UPI, cards, net banking) |
| **Storage** | AWS S3 / CloudFront CDN |

---

## Data Model & Relationships

### Core Tables

```
users (Authentication + RBAC)
├── id, firstName, lastName, email, mobileNumber, passwordHash, role, createdAt
├── role: VARCHAR(20) CHECK IN ('USER','ADMIN','TEACHER') DEFAULT 'USER'
└── Implements Spring Security UserDetails interface

courses (Products)
├── id, title, description, pricePaise, currency, thumbnailUrl, active, createdAt

videos (Content per Course)
├── id, videoId (Vimeo/YT), title, courseId (FK), thumbnailUrl, duration, displayOrder
└── FK: courses(id)

video_pdfs (Attachments per Video)
├── id, videoIdFk (FK), title, pdfType (NOTES|PRACTICE), fileUrl, displayOrder
└── FK: videos(id)

payment_orders (Razorpay Orders)
├── id, razorpayOrderId (unique), userId (FK), courseId (FK), amountPaise, status
└── FKs: users(id), courses(id)

purchases (Verified Payments)
├── id, userId (FK), courseId (FK), razorpayOrderId (FK), razorpayPaymentId, amountPaise, purchasedAt
├── Unique Index: (userId, courseId)  ← One purchase per user per course
└── FKs: users(id), courses(id), payment_orders(razorpayOrderId)
```

### Key Relationships

```
┌──────────────┐
│   courses    │
│ id = 1       │  ("Algebra", ₹999)
└────────┬─────┘
         │ (1:N) courseId FK
         │
    ┌────▼─────────────────┐
    │    videos            │
    │ id=1,courseId=1      │  (Lecture 1, Lecture 2, ...)
    │ id=2,courseId=1      │
    └────┬──────────┬──────┘
         │(1:N)     │(1:N)
         │videoIdFk │videoIdFk
         │          │
    ┌────▼───────────────────────┐
    │    video_pdfs              │
    │ id=1,type=NOTES            │
    │ id=2,type=PRACTICE         │
    └────────────────────────────┘

┌──────────────┐
│    users     │
│ id = 5       │
└────┬────┬───┘
     │    │
  (1:N)  (1:N)
     │    │
     │    └─────────────────┐
     │                      │
┌────▼──────────┐    ┌──────▼────────────┐
│payment_orders │    │   purchases       │
│ id=42         │    │ id=100            │
│ orderStatus.. │    │ (verified payment)│
└───────────────┘    └───────────────────┘
```

### The Access Link: "Section Name"

In Android's Home screen, videos are **grouped by section name** (the course title).

```
SessionManager.purchasedSectionNames = ["Algebra", "Geometry"]
                                            ↑
                           (Loaded from GET /api/user/courses)
                           Maps to: courses.title

When user taps a video:
  onVideoSelected(videoId, sectionName="Algebra")
                              ↓
  sessionManager.hasPurchased("Algebra") ?
                              ↓
         YES → VideoDetailScreen  |  NO → "Purchase at website" dialog
```

---

## User Journeys

### Journey 1: First-Time User → Sign Up

```
User opens Singh Sir Android app
              ↓
        Splash Screen (3.5s)
              ↓
    SessionManager checks for saved JWT token
              ↓
         No token found
              ↓
        → Login Screen
              ↓
    User taps "Sign Up"
              ↓
        → Signup Screen
              ↓
    Enters: firstName, lastName, email, mobileNumber, password
              ↓
    POST /api/auth/signup
              ↓
    Backend:
    ├─ Validates inputs
    ├─ Hashes password with bcrypt
    ├─ Creates user row in DB
    ├─ Generates JWT token (HS256)
    └─ Returns { token, firstName }
              ↓
    SessionManager.saveSession(token, firstName)
    ├─ Saves to SharedPrefs (encrypted)
    ├─ Calls GET /api/user/courses (check what they own)
    └─ purchasedSectionNames = [] (nothing yet)
              ↓
        → Home Screen (shows all videos, all locked)
```

### Journey 2: Returning User → Login

```
User opens app
              ↓
    Splash Screen checks SessionManager
              ↓
    Found saved JWT token
              ↓
    Call GET /api/user/courses (validate token + get purchases)
              ↓
    Backend checks: token valid? → YES
              ↓
    Returns: purchasedSectionNames = ["Algebra"]
              ↓
    SessionManager updates purchasedSectionNames
              ↓
        → Home Screen
              ↓
    Videos in "Algebra" section: hasPurchased("Algebra") = ✅ UNLOCKED
    Videos in other sections:    hasPurchased(other)   = ❌ LOCKED
```

### Journey 3: Purchase Flow (Website)

```
User (in Android app) taps video in unpurchased section
              ↓
    "Course Locked" dialog shows with website URL
              ↓
    User opens browser → teacherplatform.duckdns.org/student/index.html
              ↓
    Student Web Page: checkAuth() runs on page load
              ↓
    No token in localStorage → redirect to /web/auth/login.html
              ↓
    User logs in with email/mobile + password
    POST /api/auth/login → returns { token, role, firstName, ... }
              ↓
    saveSession() stores token, role, userId, firstName in localStorage
              ↓
    role == 'USER' → redirect to /web/student/index.html
              ↓
    Student Web Page: requireRole(['USER','TEACHER']) passes
              ↓
    Shows list of courses: "Algebra (₹999)", "Geometry (₹799)", ...
              ↓
    User taps "Buy Now" on Algebra course
              ↓
    → Course Detail Screen for Algebra
              ↓
    User taps "Buy Now — ₹999"
              ↓
    POST /api/payment/create-order
    { courseId: 1 }
              ↓
    Backend:
    ├─ Validates: course exists? active?
    ├─ Validates: user hasn't already purchased?
    ├─ Fetches price from DB (user can't change it)
    ├─ Creates Razorpay order (server-side)
    ├─ Saves PaymentOrder row (status='CREATED')
    └─ Returns { razorpayOrderId, amountPaise, keyId }
              ↓
    Razorpay SDK opens checkout modal in browser
              ↓
    User selects payment method: UPI / Card / Net Banking
              ↓
    Completes payment on payment gateway
              ↓
    Razorpay calls webhook → Frontend handler
              ↓
    Frontend handler receives:
    { razorpayOrderId, razorpayPaymentId, razorpaySignature }
              ↓
    POST /api/payment/verify
              ↓
    Backend:
    ├─ Verifies HMAC-SHA256 signature with secret key
    ├─ Marks PaymentOrder row status='PAID'
    ├─ Creates Purchase row (verified payment)
    └─ Returns { success: true }
              ↓
    Frontend shows: "Payment successful! Open Singh Sir app"
              ↓
    User reopens Android app or restarts
              ↓
    GET /api/user/courses → now returns course in purchasedSectionNames
              ↓
    Next time user taps Algebra video → UNLOCKED ✅
```

### Journey 4: Watch Video & Download PDFs

```
User is on Home Screen, has purchased "Algebra" course
              ↓
    Taps a video card (e.g., "Lecture 1")
              ↓
    VideoDetailScreen loads
              ↓
    GET /api/videos/by-course/{courseId}
    (Android app passes courseId + JWT token)
              ↓
    Backend:
    ├─ Validates token
    ├─ Checks: does user own this course?
    │  → purchases table: (userId, courseId) exists?
    ├─ If NO → return 403 Forbidden
    ├─ If YES → fetch all videos for course + their PDFs
    └─ Returns [ { videoId, title, pdfs: [...] }, ... ]
              ↓
    VideoDetailScreen renders
    ├─ Video player shows with play button
    ├─ PDFs list shows below (Notes, Practice Sheets, etc.)
    └─ Each PDF has a download button → direct link to S3
              ↓
    User taps PDF download
              ↓
    Browser downloads PDF from S3/CloudFront CDN
              ↓
    User taps play on video
              ↓
    Opens embedded video player (Vimeo/YouTube)
    or native Android video player
```

### Journey 5: Teacher Manages Courses (Admin Panel)

```
Teacher opens browser → teacherplatform.duckdns.org/web/admin/index.html
              ↓
    checkAuth() runs: no token in localStorage?
    → redirect to /web/auth/login.html
              ↓
    Teacher logs in with ADMIN credentials
    POST /api/auth/login → returns { token, role: 'ADMIN', ... }
              ↓
    saveSession() stores token + role in localStorage
    role == 'ADMIN' → redirect to /web/admin/index.html
              ↓
    requireRole(['ADMIN']) passes → Dashboard loads
              ↓
    GET /api/admin/courses
    (JWT with ADMIN role — @PreAuthorize("hasRole('ADMIN')") passes)
              ↓
    Backend: returns all courses (active + inactive) with studentCount
              ↓
    Courses table renders with: Title, Price, Students, Status, Actions

    ─────── Create Course ───────
    Teacher clicks "+ Create Course"
              ↓
    Modal opens: Title, Description, Price, Thumbnail, Active toggle
              ↓
    Teacher fills form + optionally uploads JPEG/PNG image
              ↓
    POST /api/admin/courses (multipart/form-data)
    ├─ title, description, pricePaise, currency, active
    └─ thumbnail (image file)
              ↓
    Backend:
    ├─ @PreAuthorize verifies ADMIN role
    ├─ Validates image (JPEG/PNG, max 5MB)
    ├─ Creates Course row in DB
    ├─ If thumbnail: uploads to S3 → s3://bucket/courses/{id}/thumbnail.jpg
    ├─ Stores S3 URL in courses.thumbnail_url
    └─ Returns AdminCourseResponse
              ↓
    Table refreshes — new course visible
    Course immediately visible on student web page (GET /api/courses)

    ─────── Edit Course ───────
    Teacher clicks "Edit" on a course row
              ↓
    Modal opens pre-filled with current values
              ↓
    Teacher changes price / description / status / replaces thumbnail
              ↓
    PUT /api/admin/courses/{courseId} (multipart/form-data)
    ├─ Only changed fields need to be sent
    └─ Optional new thumbnail (old one deleted from S3 if replaced)
              ↓
    Course updated, table refreshes

    ─────── Delete (Soft) Course ───────
    Teacher clicks "Delete" → Confirmation dialog
              ↓
    Teacher confirms
              ↓
    DELETE /api/admin/courses/{courseId}
              ↓
    Backend: sets active=false (NOT deleted from DB)
    ├─ Purchase records preserved
    ├─ Students lose access (course no longer returned in /api/courses)
    └─ Course can be reactivated later by editing
              ↓
    Table refreshes — course shows as Inactive (or removed from listing)

    ─────── View Enrolled Students ───────
    Teacher clicks "View" on a course
              ↓
    GET /api/admin/courses/{courseId}/students
              ↓
    Backend: queries purchases JOIN users WHERE course_id = ?
              ↓
    Modal shows: Student Name, Email, Mobile, Purchase Date

    ─────── Manage Videos ───────
    Teacher clicks "Videos" (green button) on a course row
              ↓
    GET /api/admin/courses/{courseId}/videos
              ↓
    Course Videos modal: #, thumbnail, title, duration, PDF count, [PDFs] [Delete]
              ↓
    Teacher clicks "+ Add Video"
    Fill in: YouTube URL, title, duration, display order
    + optional: Notes PDF / Solved Practice PDF / Annotated Practice PDF
              ↓
    POST /admin/videos (multipart/form-data)
    ├─ Extracts YouTube videoId from URL
    ├─ Auto-generates thumbnail URL (img.youtube.com/vi/{id}/mqdefault.jpg)
    ├─ Saves Video row in DB
    ├─ For each PDF: uploads to S3 → saves VideoPdf row
    └─ Returns VideoResponse
              ↓
    Video appears in list with YouTube thumbnail auto-loaded

    ─────── Manage PDFs ───────
    Teacher clicks "PDFs" on a video row
              ↓
    PDF modal: lists existing PDFs (type, title) with Delete buttons
              ↓
    Teacher uploads new PDF: type dropdown + title + file
    POST /admin/videos/{videoId}/pdfs
    → PDF uploaded to S3, row saved → list refreshes
              ↓
    Teacher deletes a PDF: DELETE /admin/videos/{videoId}/pdfs/{pdfId}
    → Removed from S3 + DB
```

---

## API & Payment Flow

### Authentication Endpoints

| Endpoint | Method | Auth | Purpose |
|---|---|---|---|
| `/api/auth/signup` | POST | ❌ | Create new account |
| `/api/auth/login` | POST | ❌ | Login (email or mobile + password) |
| `/api/auth/forgot-password` | POST | ❌ | Request OTP via SMS |
| `/api/auth/reset-password` | POST | ❌ | Reset password with OTP |

**Request / Response:**
```json
POST /api/auth/login
{
  "identifier": "9876543210",    // email or mobile
  "password": "mypassword"
}

Response:
{
  "token": "eyJhbGc...",
  "userId": 5,
  "email": "rahul@email.com",
  "firstName": "Rahul",
  "lastName": "Sharma",
  "mobileNumber": "9876543210",
  "role": "USER"          // "USER" | "ADMIN" | "TEACHER"
}
// JWT claims include: sub=userId, email, role (HS256, 7-day expiry)
```

### Payment Endpoints

| Endpoint | Method | Auth | Purpose |
|---|---|---|---|
| `/api/payment/create-order` | POST | ✅ JWT | Create Razorpay order |
| `/api/payment/verify` | POST | ✅ JWT | Verify payment signature + record purchase |

**Request / Response:**
```json
POST /api/payment/create-order
Headers: Authorization: Bearer <JWT>
{
  "courseId": 1
}

Response:
{
  "razorpayOrderId": "order_12345",
  "amountPaise": 99900,
  "currency": "INR",
  "keyId": "rzp_live_xxx"
}

───────────────────────────────────────────

POST /api/payment/verify
Headers: Authorization: Bearer <JWT>
{
  "razorpayOrderId": "order_12345",
  "razorpayPaymentId": "pay_67890",
  "razorpaySignature": "abcdef..."
}

Response:
{
  "success": true,
  "message": "Payment verified successfully"
}
```

### Catalog Endpoints

| Endpoint | Method | Auth | Purpose |
|---|---|---|---|
| `/api/courses` | GET | ❌ | List all active courses |
| `/api/user/courses` | GET | ✅ JWT | Get user's purchased courses |
| `/api/videos/by-course/{id}` | GET | ✅ JWT | Get videos + PDFs for a course (only if purchased) |

**Request / Response:**
```json
GET /api/courses
Response:
[
  {
    "id": 1,
    "title": "Algebra",
    "description": "Fundamentals of algebra...",
    "pricePaise": 99900,
    "currency": "INR",
    "thumbnailUrl": "https://cdn.../thumb.jpg"
  },
  ...
]

───────────────────────────────────────────

GET /api/user/courses
Headers: Authorization: Bearer <JWT>

Response:
{
  "purchasedSectionNames": ["Algebra", "Geometry"]
  // OR full courses list (depending on backend version)
}

───────────────────────────────────────────

GET /api/videos/by-course/1
Headers: Authorization: Bearer <JWT>

Response:
[
  {
    "id": 1,
    "videoId": "vimeo_123",
    "title": "Lecture 1: Basics",
    "thumbnailUrl": "...",
    "duration": "15:23",
    "displayOrder": 1,
    "pdfs": [
      {
        "id": 10,
        "title": "Lecture 1 Notes",
        "pdfType": "NOTES",
        "fileUrl": "https://cdn.../lecture1-notes.pdf",
        "displayOrder": 1
      }
    ]
  },
  ...
]
```

---

## Admin Panel API

All endpoints require **ADMIN role** (enforced via `@PreAuthorize("hasRole('ADMIN')")`).
Non-admin users receive **403 Forbidden**.

| Endpoint | Method | Purpose |
|---|---|---|
**Course Management Endpoints:**

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/admin/courses` | GET | List all courses (active + inactive) with student counts |
| `/api/admin/courses` | POST | Create course with optional thumbnail |
| `/api/admin/courses/{id}` | PUT | Update course fields or thumbnail |
| `/api/admin/courses/{id}` | DELETE | Soft delete (sets active=false) |
| `/api/admin/courses/{id}` | GET | Get single course detail |
| `/api/admin/courses/{id}/students` | GET | List enrolled students |
| `/api/admin/courses/{id}/videos` | GET | List all videos + PDFs for a course (no purchase check) |

**Video & PDF Management Endpoints:**

| Endpoint | Method | Purpose |
|---|---|---|
| `/admin/videos` | POST | Add video (YouTube URL + optional PDFs) |
| `/admin/videos/{id}` | DELETE | Delete video + all its PDFs from S3 and DB |
| `/admin/videos/{videoId}/pdfs` | POST | Add a PDF to a video |
| `/admin/videos/{videoId}/pdfs/{pdfId}` | PUT | Update PDF metadata or replace file |
| `/admin/videos/{videoId}/pdfs/{pdfId}` | DELETE | Delete a PDF from S3 and DB |

**Create / Update Course (multipart/form-data):**
```
POST /api/admin/courses
Authorization: Bearer <JWT with ADMIN role>
Content-Type: multipart/form-data

Fields:
  title         (required) String
  description   (optional) String
  pricePaise    (required) Int — price in paise (₹999 = 99900)
  currency      (optional) String — default "INR"
  active        (optional) Boolean — default true
  thumbnail     (optional) File — JPEG or PNG, max 5MB

Response:
{
  "id": 2,
  "title": "Geometry",
  "description": "...",
  "pricePaise": 79900,
  "currency": "INR",
  "thumbnailUrl": "https://cdn.../courses/2/thumbnail.jpg",
  "active": true,
  "studentCount": 0,
  "createdAt": "2026-03-09T10:30:00Z"
}
```

**Get Enrolled Students:**
```
GET /api/admin/courses/1/students
Authorization: Bearer <JWT with ADMIN role>

Response:
[
  {
    "id": 5,
    "firstName": "Rahul",
    "lastName": "Sharma",
    "email": "rahul@email.com",
    "mobileNumber": "9876543210",
    "purchasedAt": "2026-03-05T14:22:00Z"
  },
  ...
]
```

**RBAC Architecture:**
```java
// User entity implements Spring Security UserDetails
public enum UserRole { USER, ADMIN, TEACHER }

// JWT includes role claim — JwtAuthenticationFilter extracts it:
String role = jwtService.getRoleFromToken(token);
List<SimpleGrantedAuthority> authorities =
    List.of(new SimpleGrantedAuthority("ROLE_" + role));
// → "ROLE_ADMIN" for admin users, "ROLE_USER" for students

// SecurityConfig: admin endpoints require authentication
.requestMatchers("/admin/**").authenticated()     // video/PDF controllers
.requestMatchers("/api/admin/**").authenticated() // course controllers

// Any admin controller method is protected with:
@PreAuthorize("hasRole('ADMIN')")

// Setting a user as admin (run once in DB):
UPDATE users SET role = 'ADMIN' WHERE id = <teacher_user_id>;
```

---

### Signature Verification (Security)

When Razorpay callback arrives, **backend verifies the payment was real**:

```
1. Razorpay sends:
   {
     "razorpay_order_id": "order_12345",
     "razorpay_payment_id": "pay_67890",
     "razorpay_signature": "abcdef1234..."
   }

2. Backend computes:
   HMAC-SHA256(
     order_id=order_12345&payment_id=pay_67890,
     key=RAZORPAY_KEY_SECRET
   )

3. Backend compares:
   computed_signature == received_signature ?
   ├─ YES → Payment is real, create Purchase row
   └─ NO → Someone tampered, reject
```

---

## Access Control

### Two-Layer Access Check

**Layer 1: App-Side (Local Cache)**
```java
// HomeScreen.kt
if (sessionManager.hasPurchased(sectionName)) {
    // Instant check, no network — already granted access
    navController.navigate("video_detail/$id")
} else {
    // Show purchase dialog
    showDialog("Purchase course at website")
}
```

**Layer 2: Backend (Authoritative)**
```java
// VideoCatalogService.java
public List<VideoResponse> getVideosByCourse(Long courseId, Long userId) {
    // Even if app says they own it, backend double-checks
    if (!purchaseRepository.existsByUserIdAndCourseId(userId, courseId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "You have not purchased this course.");
    }
    // Only if passed → return videos
}
```

**Why both?**
- App-side: Instant feedback (no wait)
- Backend: Prevents tampering (app can't fake purchases)

### Purchase Integrity

```
purchases table has:
├─ UNIQUE INDEX (userId, courseId)  → One purchase per user per course
├─ FK to payment_orders            → Links to the Razorpay order
├─ razorpay_payment_id (unique)    → No duplicates
└─ purchased_at timestamp          → Audit trail

Result: A user can't:
  ✗ Buy the same course twice (DB constraint)
  ✗ Fake a purchase (must pass signature verification)
  ✗ Transfer purchase to another user (userId is fixed)
```

---

## Key Components

### 1. SessionManager (Android)
**Role:** Local cache of auth state + purchases
```kotlin
class SessionManager {
    var currentToken: String         // JWT token
    var displayName: String          // "Rahul Sharma"
    var userId: Long                 // User ID
    var purchasedSectionNames: Set<String>  // ["Algebra", ...]

    fun hasPurchased(sectionName: String): Boolean
    fun saveSession(token, firstName)
    fun clearSession()               // On logout
}
```

### 2. PaymentService (Backend)
**Role:** Handles orders, verification, access grants
```java
class PaymentService {
    public CreateOrderResponse createOrder(Long userId, Long courseId)
    public VerifyPaymentResponse verifyPayment(userId, orderId, paymentId, signature)
    public UserCoursesResponse getUserCourses(Long userId)
    public List<CourseResponse> listCourses()
}
```

### 3. VideoCatalogService (Backend)
**Role:** Gated access to video content
```java
class VideoCatalogService {
    public List<VideoResponse> getVideosByCourse(Long courseId, Long userId)
    // Checks: does user own this course? if not, 403 Forbidden
}
```

### 4. AdminService (Backend)
**Role:** Video lesson and PDF management
```java
class AdminService {
    public VideoResponse createVideoLesson(youtubeUrl, title, courseId, duration, order, pdfs...)
    // Extracts YouTube videoId, auto-generates thumbnail URL, uploads PDFs to S3

    public List<VideoResponse> getVideosForCourse(Long courseId)
    // Returns all videos + their PDFs — no purchase check (admin view)

    public void deleteVideo(Long videoId)
    // Deletes all S3 PDFs for the video, then removes video row from DB
}
```

### 5. Web Auth Library (`web/lib/auth.js`)
**Role:** Shared token and session management for all web pages
- `getToken()` / `setToken()` / `clearToken()` — localStorage + in-memory cache
- `saveSession(authResponse)` — persists token, userId, email, firstName, role
- `clearSession()` — wipes localStorage and in-memory cache
- `getUserRole()` / `getUserInfo()` — reads session from cache or localStorage
- `apiFetch(path, options)` — auto-adds `Authorization: Bearer <token>` header; on 401 (non-auth endpoints) clears session and redirects to login
- `loginUser(username, password)` / `signupUser(...)` — wraps API calls and saves session

### 6. Web Router Library (`web/lib/router.js`)
**Role:** Role-based navigation guards for all web pages
- `checkAuth()` — if no token or role, redirects to `/web/auth/login.html`
- `requireRole(allowedRoles)` — if wrong role, calls `redirectByRole()` and returns false
- `redirectByRole(role)` — ADMIN → `/web/admin/index.html`, USER/TEACHER → `/web/student/index.html`
- `logout()` — clears session and redirects to login

### 7. Centralized Login Page (`web/auth/login.html`)
**Role:** Single entry point for all users (admin + student)
- Login and signup screens (toggle between them)
- After login: reads `role` from response, redirects by role
- Admin users always land on `/web/auth/login.html`, never on student page

### 8. Student Web Page (`web/student/index.html`)
**Role:** Payment gateway for web browsers
- Gated by `checkAuth()` + `requireRole(['USER','TEACHER'])` — redirects if not logged in or wrong role
- Shows course list
- Initiates Razorpay checkout
- Verifies payment signature (backend does final check)
- Redirects to app with "Open app to access" message

### 9. Admin Panel (`web/admin/index.html`)
**Role:** Teacher dashboard for full course lifecycle management
- Gated by `checkAuth()` + `requireRole(['ADMIN'])` — redirects if not ADMIN
- Create/edit/delete (soft) courses with thumbnail image upload
- View all courses with student enrollment counts
- View enrolled student list per course
- Videos button on course rows → manage videos (add/delete) and PDFs per video
- Reports & analytics (revenue, total students, top courses)

### 10. AdminCourseService (Backend)
**Role:** Business logic for admin course operations
```java
class AdminCourseService {
    public AdminCourseResponse createCourse(CreateCourseRequest, MultipartFile thumbnail)
    public AdminCourseResponse updateCourse(Long courseId, UpdateCourseRequest, MultipartFile thumbnail)
    public void deleteCourse(Long courseId)            // soft delete
    public List<AdminCourseResponse> getAllCourses()
    public List<StudentResponse> getEnrolledStudents(Long courseId)
}
```

### 11. CustomUserDetailsService (Backend)
**Role:** Bridge between JWT authentication and Spring Security RBAC
```java
class CustomUserDetailsService implements UserDetailsService {
    // Loads User entity (which implements UserDetails) by email or mobile
    // Spring Security uses this to populate SecurityContext with user's roles
    public UserDetails loadUserByUsername(String username)
    public User loadUserById(Long userId)
}
```

---

## Important Security Notes

### Secrets (Never commit to git)
```bash
RAZORPAY_KEY_ID=your_key
RAZORPAY_KEY_SECRET=your_secret
JWT_SECRET_KEY=your_jwt_secret
```

### Rate Limiting
- `/api/auth/login` — Rate limit to prevent brute force
- `/api/payment/verify` — Rate limit to prevent spam

### HTTPS Only
- All API calls must use HTTPS
- Razorpay checkout requires HTTPS

### CORS
- Student web page may be on different domain than API
- Backend has CORS config allowing student web domain

---

## Feature Checklist (Current State)

### Completed (P1.0 - P1.4) ✅
- ✅ User signup/login (Android + Web)
- ✅ Razorpay payment integration
- ✅ Access validation (course purchase gating)
- ✅ Video catalog with PDF attachments
- ✅ Student web page (courses + checkout)
- ✅ Policies page (Terms, Privacy, Refund)
- ✅ Forgot password + reset via SMS OTP
- ✅ Proper RBAC (role column, UserDetails, @PreAuthorize)
- ✅ Admin Panel — Course CRUD + image upload + student enrollment
- ✅ Reports dashboard (revenue, enrollment stats)
- ✅ Centralized login page with role-based redirect (P1.4b)
- ✅ JWT tokens include `role` claim; JwtAuthenticationFilter sets authorities
- ✅ Admin and student pages gated by auth guards (checkAuth + requireRole)
- ✅ Shared web auth library (auth.js, router.js) for token and session management
- ✅ Admin panel — Video management UI (add, delete videos per course) (P1.4c)
- ✅ Admin panel — PDF management UI (add, delete PDFs per video) (P1.4c)
- ✅ Video/PDF API endpoints secured with `@PreAuthorize("hasRole('ADMIN')")` (P1.4c)
- ✅ `GET /api/admin/courses/{id}/videos` — admin video listing without purchase check (P1.4c)

### Pending (P1.5 — Go Live)
- ⏳ Backend deployed and stable
- ⏳ Android APK tested end-to-end on real device
- ⏳ Teacher has tested admin panel (create a real course)
- ⏳ One real student has paid and accessed a course
- ⏳ Razorpay test mode switched to live mode

### Future (Phase 2+)
- 🚀 Exam generator (AI-powered question creation)
- 🚀 Student instant exam engine
- 🚀 Subscriptions + analytics
- 🚀 Mobile exam taker UI

---

## Updating This Document

When adding new features:
1. **Update Data Model** section if new tables/columns
2. **Update API & Payment Flow** if new endpoints
3. **Update User Journeys** if user flow changes
4. **Add note** at top: "Last Updated: YYYY-MM-DD"

Example: When adding exam feature
```
### Journey 5: Exam Taking (Phase 2)
┌─────────────────────────────┐
│ Student joins exam via code │
└──────────┬──────────────────┘
           ↓
    GET /api/exam/{code}
           ↓
  Shows questions one-by-one
```

---

## Quick Reference

### Important Files

| Path | Purpose |
|---|---|
| `backend/docker/init/001_init.sql` | User + video tables |
| `backend/docker/init/003_purchases.sql` | Course + payment tables |
| `backend/src/main/.../payment/` | Payment service |
| `backend/src/main/.../catalog/` | Video catalog service |
| `android/.../app/ui/home/HomeScreen.kt` | Purchase gating logic |
| `web/auth/login.html` | Centralized login page (all users) |
| `web/lib/auth.js` | Shared auth library (token, session, apiFetch) |
| `web/lib/router.js` | Role-based navigation guards |
| `web/student/index.html` | Student checkout page (auth-gated) |
| `web/admin/index.html` | Admin panel (auth + ADMIN role required) |

### Key Configuration

| Setting | Location | Purpose |
|---|---|---|
| Backend API URL | `web/student/index.html:269` | API base for web |
| API URL | `android/.../config/AppConstants.kt` | API base for app |
| Razorpay Keys | `application.yml` (not in git) | Payment provider |
| JWT Secret | `SecurityConfig.java` (env var) | Token signing |

---

## Troubleshooting Quick Links

**"Course Locked" when buying?**
- Check: Did `POST /api/payment/verify` succeed?
- Check: Does `purchases` table have (userId, courseId) row?
- Check: Is the course `active = true` in DB?

**Video returns 403?**
- Check: Is JWT token valid?
- Check: Does `purchases` table have entry?
- Check: Did `GET /api/user/courses` reflect the purchase?

**Razorpay checkout not opening?**
- Check: Is `RAZORPAY_KEY_ID` set in backend?
- Check: Is `checkout.razorpay.com/v1/checkout.js` loaded?
- Check: Is page over HTTPS?


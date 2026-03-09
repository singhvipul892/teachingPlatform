# Singh Sir — Complete System Flow

> **Living Document**: This file captures the current system architecture, user journeys, and data flows. Update this whenever significant features are added or changed.
>
> Last Updated: **2026-03-09**

---

## Table of Contents

1. [System Architecture](#system-architecture)
2. [Data Model & Relationships](#data-model--relationships)
3. [User Journeys](#user-journeys)
4. [API & Payment Flow](#api--payment-flow)
5. [Access Control](#access-control)
6. [Key Components](#key-components)

---

## System Architecture

### Overview
Singh Sir is a **multi-tier learning platform** serving SSC CGL Math students in India.

```
┌─────────────────────────────────────────────────────────────┐
│                   STUDENT TOUCHPOINTS                        │
├──────────────────────┬──────────────────────┬────────────────┤
│  Android App         │  Student Website     │  Admin Panel    │
│  (Content Learning)  │  (Payment + Auth)    │  (Mgmt - TODO)  │
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
| **Admin Panel** | Vanilla HTML/JS (forthcoming) |
| **Payments** | Razorpay (India-first: UPI, cards, net banking) |
| **Storage** | AWS S3 / CloudFront CDN |

---

## Data Model & Relationships

### Core Tables

```
users (Authentication)
├── id, firstName, lastName, email, mobileNumber, passwordHash, createdAt

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
    Student Web Page loads (public, no auth required initially)
              ↓
    Shows list of courses: "Algebra (₹999)", "Geometry (₹799)", ...
              ↓
    User taps "Buy Now" on Algebra course
              ↓
    Redirects to login if not logged in
              ↓
    POST /api/auth/login (same backend, same credentials as app)
              ↓
    Backend: token returned + login state saved in localStorage
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
  "firstName": "Rahul"
}
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

### 4. Student Web Page (Vanilla JS)
**Role:** Payment gateway for desktop browsers
- Reads JWT from localStorage (same as app)
- Shows course list
- Initiates Razorpay checkout
- Verifies payment signature (backend does final check)
- Redirects to app with "Open app to access" message

### 5. Admin Panel (TODO - P1.4)
**Role:** Teacher dashboard for course management
- Create/edit courses
- Upload videos (with course_id)
- Attach PDFs to videos
- View student enrollments
- Toggle course active/inactive

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

### Completed (P1.0 - P1.3)
- ✅ User signup/login (Android + Web)
- ✅ Razorpay payment integration
- ✅ Access validation (course purchase gating)
- ✅ Video catalog with PDF attachments
- ✅ Student web page (courses + checkout)
- ✅ Policies page (Terms, Privacy, Refund)
- ✅ Forgot password + reset via SMS OTP

### Pending (P1.4 - P1.5)
- ⏳ Admin panel (teacher dashboard for course mgmt)
- ⏳ End-to-end testing (unpaid → paid flow)
- ⏳ Deployment (backend + Android APK)
- ⏳ Go-live checks

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
| `web/student/index.html` | Student checkout page |

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


# BorrowBox - Firestore Schema & Security Rules Specification

This document details the database architecture, Firestore collections, Cloud Storage layout, and security rules implemented for BorrowBox (`com.borrowbox.kuet`).

---

## 1. Firestore Collections Architecture

### 1.1 Users Collection (`users/{uid}`)
Represents a student's profile, contact details, academic standing, and verification level.

| Field | Type | Description | Security Constraints |
| :--- | :--- | :--- | :--- |
| `uid` | string | Firebase Auth UID | Immutable |
| `fullName` | string | Student's full name (2–100 chars) | Editable by owner |
| `email` | string | Academic institutional email | Editable by owner |
| `phoneNumber` | string | Contact phone number (10–25 chars) | Editable by owner |
| `whatsappNumber`| string | Active WhatsApp number for wa.me links | Editable by owner |
| `presentAddress` | string | Campus hall name and room number | Editable by owner |
| `department` | string | KUET department (CSE, EEE, ME, etc.) | **Locked when Approved** |
| `rollNumber` | string | Student roll number (e.g. 1907001) | **Locked when Approved** |
| `crName` | string | Class representative name | Editable by owner |
| `profilePictureUrl`| string | Cloud Storage avatar URL | Editable by owner |
| `verificationStatus`| string | `Not Submitted`, `Pending`, `Approved`, `Rejected` | **Admin only for 'Approved'** |
| `trustScore` | number | Rating (0.0 to 5.0, default 5.0) | **Admin / System only** |
| `createdAt` | number | Timestamp millis | Immutable |
| `updatedAt` | number | Timestamp millis | Updated on write |

---

### 1.2 Student Verifications Collection (`studentVerifications/{id}`)
Submitted identity proofs containing photos of student ID cards for admin verification.

| Field | Type | Description | Security Constraints |
| :--- | :--- | :--- | :--- |
| `userId` | string | Submitting student's UID | Immutable; matches `request.auth.uid` |
| `rollNumber` | string | Stated roll number | Submitter on create |
| `department` | string | Stated department | Submitter on create |
| `crName` | string | Stated class representative | Submitter on create |
| `studentIdCardFrontUrl` | string | Front ID card photo path in Storage | Private to Admin & Submitter |
| `studentIdCardBackUrl` | string | Back ID card photo path in Storage | Private to Admin & Submitter |
| `status` | string | `Pending`, `Approved`, `Rejected` | Admin only for update |
| `reviewNote` | string | Optional feedback from reviewing admin | Admin only |
| `reviewedBy` | string | UID of admin reviewer | Admin only |
| `createdAt` | number | Timestamp millis | Immutable |
| `updatedAt` | number | Timestamp millis | Updated on write |

---

### 1.3 Rental Requests Collection (`rentalRequests/{id}`)
A formal borrow request submitted by a student for an item listing.

| Field | Type | Description | Security Constraints |
| :--- | :--- | :--- | :--- |
| `id` | string | Request document ID | System generated |
| `productId` | string | Target product listing ID | Immutable |
| `productName` | string | Target product title | Immutable |
| `productPricePerDay` | number | Daily rate (in BDT) | Immutable; recomputed server/repo side |
| `ownerId` | string | Listing owner UID | Immutable; **cannot equal renterId** |
| `ownerName` | string | Owner display name | Immutable |
| `renterId` | string | Borrower student UID | Must match `request.auth.uid` |
| `renterName` | string | Borrower display name | Snapshot from profile |
| `renterDepartment` | string | Borrower department | Snapshot from profile |
| `renterRollNumber` | string | Borrower roll number | Snapshot from profile |
| `renterAddress` | string | Borrower campus address | Snapshot from profile |
| `renterWhatsApp` | string | Borrower WhatsApp number | Snapshot from profile |
| `renterTrustScore` | number | Borrower trust rating | Snapshot from profile |
| `renterVerificationStatus`| string | Borrower verification badge | Snapshot from profile |
| `startDateMillis` | number | Rental start date | Immutable; not in past |
| `returnDateMillis` | number | Rental expected return date | Immutable; > startDate |
| `durationDays` | number | Calculated duration in days | >= 1 |
| `totalCost` | number | `durationDays * productPricePerDay` | Immutable; calculated |
| `status` | string | `Pending`, `Accepted`, `Rejected`, `Active`, `Returned` | **Owner only for Accept/Reject** |
| `createdAt` | number | Timestamp millis | Immutable |
| `updatedAt` | number | Timestamp millis | Updated on write |

---

### 1.4 Rentals Collection (`rentals/{id}`)
Active or completed rental agreements created once an owner accepts a rental request.

| Field | Type | Description | Security Constraints |
| :--- | :--- | :--- | :--- |
| `id` | string | Rental agreement ID | System generated |
| `requestId` | string | Originating `rentalRequests/{id}` | Immutable |
| `productId` | string | Item ID | Immutable |
| `productName` | string | Item name | Immutable |
| `ownerId` | string | Item owner UID | Immutable |
| `renterId` | string | Borrower UID | Immutable |
| `startDateMillis` | number | Handover date | Stored timestamp |
| `returnDateMillis` | number | Deadline return date | Stored timestamp |
| `totalCost` | number | Total agreed rental charge | Immutable |
| `status` | string | `Active`, `Returned`, `Overdue` | Dynamic based on return timestamp |
| `confirmedAt` | number | Time owner accepted request | Immutable |
| `returnedAt` | number | Time item was confirmed returned | Set on return |

---

### 1.5 In-App Notifications Collection (`notifications/{id}`)
System and user notifications alerting parties about request updates, approvals, or messages.

| Field | Type | Description | Security Constraints |
| :--- | :--- | :--- | :--- |
| `id` | string | Notification ID | System generated |
| `recipientId` | string | Target user UID | **Only recipient can read** |
| `title` | string | Notification header | Immutable |
| `message` | string | Notification body | Immutable |
| `type` | string | `RENTAL_REQUEST`, `REQUEST_ACCEPTED`, etc. | Immutable |
| `relatedId` | string | Target request/rental ID | Immutable |
| `timestamp` | number | Created timestamp | Immutable |
| `isRead` | boolean | Read state | **Recipient can toggle to true** |

---

## 2. Cloud Storage Rules (`storage.rules`)

1. **User Avatars (`users/{userId}/{fileName}`)**:
   - Owner can upload or replace images (`request.auth.uid == userId`).
   - Allowed file types: `image/jpeg`, `image/png`, `image/webp`.
   - Max file size: 5 MB.
   - Any authenticated student can view avatar images for product cards and borrower details.
2. **Student ID Cards (`verifications/{userId}/{fileName}`)**:
   - Sensitive identity documents.
   - Only the submitting student can upload their ID card image.
   - **Privacy Guard**: Only admins (`request.auth.token.admin == true`) can read or view ID card photos. Regular users and peers can **never** view ID photos.

---

## 3. Security Highlights & Invariants

- **Closed-by-Default (Default Deny)**: Any unmatched path is blocked by default.
- **Anti-Self-Rental**: A user cannot submit a rental request for an item where `ownerId == renterId`.
- **Anti-Self-Approval**: A borrower cannot approve their own request; only the item owner can transition status to `Accepted` or `Rejected`.
- **Verified Identity Lock**: Once a student's verification status is `Approved`, their department and roll number cannot be modified without admin re-verification.
- **Timestamp-Based Countdown**: Rental remaining time and overdue states are calculated mathematically from stored `returnDateMillis` and `currentTimeMillis`, avoiding volatile in-memory timers.

# BorrowBox Feature Audit & Architecture Guide

A comprehensive architectural and feature audit of the **BorrowBox** Android application (KUET Peer-to-Peer Campus Rental/Borrowing Platform), detailing all mobile engineering components, services, database integrations, and demo instructions for academic presentation.

---

## 1. Complete Feature & Component Status Table

| Item | Final Status | Files and Classes | One-line Explanation of How It Works | How to Demo It |
|---|---|---|---|---|
| **A. App Overview & Purpose** | **INCLUDED** | `app/src/main/AndroidManifest.xml`, `MainActivity.java` | KUET campus peer-to-peer equipment and book rental sharing platform with role-based permissions and verification. | Launch app, browse items across categories (Calculators, Drafters, Books, Lab Gear). |
| **B. Multi-Activity Navigation** | **INCLUDED** | `MainActivity.java`, `AuthActivity.java`, `ProductDetailsActivity.java`, `MyRentalsActivity.java`, `ProfileActivity.java`, `SearchActivity.java`, `AddProductActivity.java` | Standard stack navigation between specialized activities with custom animations and clear top bar back buttons. | Tap cards, profile tabs, or search to transition across screens. |
| **B. Explicit Intents** | **INCLUDED** | All Activity classes | Intent extras pass entity IDs, objects, flags, and authentication tokens between screens. | Tap any item card on the Home screen to view its specific details via `EXTRA_PRODUCT`. |
| **B. Implicit Intents** | **ADDED** | `ProductDetailsActivity.java`, `AddProductActivity.java` | Triggers external Android apps for WhatsApp chat (`ACTION_VIEW`), phone dialer (`ACTION_DIAL`), SMS messaging (`ACTION_VIEW`), contact picking (`ACTION_PICK`), and native system sharing (`ACTION_SEND`). | 1. Tap the Share button (`↗`) in Product Details to invoke system share.<br>2. Tap "Chat on WhatsApp" to open WhatsApp or browser.<br>3. Tap "Contacts" on Add Product to launch contact picker. |
| **B. View Binding** | **ADDED** | `app/build.gradle`, `BaseActivity.java`, all Activities, `NotificationAdapter.java` | ViewBinding enabled across all activities and adapters, eliminating `findViewById` for compile-time null safety. | Inspect layouts and adapters; all views accessed through generated type-safe bindings. |
| **B. Event Handling** | **INCLUDED** | `MainActivity.java`, `AuthActivity.java`, `SearchActivity.java` | OnClickListeners, TextWatchers (for live roll parsing & search querying), and Chip selection listeners. | Type in search bar to see instant debounced results; type KUET roll to see auto-department detection. |
| **C. Layout Managers** | **INCLUDED** | `activity_main.xml`, `activity_product_details.xml`, `activity_auth.xml`, `activity_profile.xml` | ConstraintLayout with guidelines/barriers, responsive LinearLayouts, and CoordinatorLayout with BottomAppBar cradle. | Rotate device or view on different screen sizes to observe fluid constraint boundaries. |
| **C. RecyclerView & ViewHolders** | **INCLUDED** | `ProductAdapter.java`, `RentalAdapter.java`, `NotificationAdapter.java` | Efficient view recycling using modern ViewHolder patterns for products, rentals, and notification feeds. | Scroll through the product feed, notification center, and My Rentals list. |
| **D.1 Dynamic BroadcastReceiver** | **ADDED** | `BaseActivity.java` | Dynamically registered BroadcastReceiver listening to `ACTION_AIRPLANE_MODE_CHANGED`, `CONNECTIVITY_ACTION`, `ACTION_BATTERY_LOW`, and `ACTION_BATTERY_OKAY` with branded Snackbars. | 1. Toggle Airplane Mode in phone settings to see "✈️ Airplane mode is on" Snackbar.<br>2. Turn off Wi-Fi/Data to see "📡 You are offline" notification.<br>3. Low battery triggers "🪫 Battery is low" notice. |
| **D.2 ContentProvider Usage** | **ADDED** | `AddProductActivity.java`, `EditProfileActivity.java` | Reads device contacts via `ContactsContract` using `ContentResolver`, queries image streams via `MediaStore`, and provides file sharing via `FileProvider`. | On "Add Product" screen, tap the "Contacts" button next to WhatsApp field to select a contact number (automatically normalized to `01XXXXXXXXX`). |
| **D.3 Foreground Service with Live Notification** | **ADDED** | `PhotoUploadForegroundService.java`, `AddProductActivity.java`, `ProfileActivity.java` | Runs a `foregroundServiceType="dataSync"` Service during photo optimization/upload showing ongoing notification with live progress bar. | Pick a photo on Add Product or Profile: observe ongoing system notification with live progress bar ending in "Upload complete". |
| **D.4 Runtime Permissions** | **ADDED** | `AddProductActivity.java`, `MainActivity.java`, `EditProfileActivity.java` | Dynamic runtime requests for `READ_CONTACTS`, `POST_NOTIFICATIONS` (Android 13+), and `CAMERA` with rationale dialogs and denied fallbacks. | Tap "Contacts" on Add Product; system requests permission with rationale dialog if previously declined. |
| **E. HTTP/JSON REST API** | **INCLUDED** | `ApiClient.java`, `backend/src/server.js` | Full asynchronous REST API client parsing JSON with loading indicators, retry logic, and error handlers. | Browse, register, login, or submit rental requests to observe live JSON communication with Node/Express backend. |
| **E. Firebase Authentication** | **ADDED** | `AuthActivity.java`, `ProfileActivity.java`, `ForgotPasswordActivity.java` | Dual-sync authentication with `FirebaseAuth` (`signInWithEmailAndPassword`, `createUserWithEmailAndPassword`), `sendPasswordResetEmail`, and UID display on Profile. | 1. Log in or sign up: Firebase Auth signs in automatically.<br>2. On Profile screen under Account, observe the real Firebase UID line.<br>3. On Login tab, tap "Forgot Password?" to trigger password reset email. |
| **E. Firebase Realtime Database (RTDB)** | **ADDED** | `BorrowBoxApp.java`, `MainActivity.java`, `backend/src/routes/rentalRoutes.js` | 1. Live presence under `/presence/<userId>` using `onDisconnect`.<br>2. Instant notification counter under `/unread/<userId>` updated by backend and listened to via `addValueEventListener`. | When a rental request is created, owner's bell badge increments in real time via Firebase Realtime Database without polling. |
| **E. Cloud Firestore from App** | **ADDED** | `MyRentalsActivity.java` | Real-time `addSnapshotListener` on `/rentals` collection filtering by `renterId == currentUserId` to update rental request status live. | Owner accepts or rejects a request; renter's My Rentals list updates status badge in real time. |
| **E. Picture Storage** | **INCLUDED** | `AddProductActivity.java`, `ProfileActivity.java`, `RentalImageUtils.java` | Photos compressed to max 800px / 400px JPEG quality 70 (under 200KB) and stored safely with in-memory LRU caching and fallback placeholders. | Upload product image and profile picture; view rendered thumbnails across cards and full-screen zoomable photo viewer. |
| **E. FCM Mobile Push Notifications** | **UPDATED** | `BorrowBoxFirebaseMessagingService.java`, `FcmHelper.java`, `BorrowBoxApp.java`, `fcmService.js` | WhatsApp-style high-priority heads-up banner with sound & vibration on channel `rent_requests_v3`, dynamic token sync to backend, and deep-link routing on tap. | 1. Tap "Send test notification" on Profile screen to receive instant push.<br>2. When a rental is requested or status changes, owner/renter receives heads-up banner even with app closed. |
| **G. My Posts (Product Management)** | **ADDED** | `MyPostsActivity.java`, `MyPostsAdapter.java`, `AddProductActivity.java`, `productRoutes.js` | Post management for owners: view listed items, edit details/pricing/images in pre-filled form, or delete post with automatic cancellation of pending rentals. | 1. Go to Profile > tap "My Posts".<br>2. Tap "Edit" to modify post in `AddProductActivity`.<br>3. Tap "Delete" to remove post after confirmation. |
| **G. Hall Room Number Support** | **ADDED** | `UserProfile.java`, `SessionManager.java`, `AuthActivity.java`, `EditProfileActivity.java`, `RentalRequestDetailsActivity.java` | Optional hall room number added to registration, user profile, and owner request inspection (`<Hall> • Room <RoomNo>`). | 1. Register or edit profile with room number.<br>2. Receive rental request: owner sees requester's hall and room number. |
| **G. Cascading Account Deletion** | **ADDED** | `ProfileActivity.java`, `userRoutes.js`, `ApiClient.java` | Irreversible account wipe requiring confirmation typing "DELETE"; deletes user document, products, rentals, notifications, FCM tokens, Firebase Auth, and RTDB nodes. | On Profile screen, tap "Delete Account", type `DELETE`, and confirm. App logs out and wipes user data. |

---

## 2. Summary Grouping

### Already Included in the Project
1. Multi-Activity screen-to-screen navigation (`MainActivity`, `AuthActivity`, `ProfileActivity`, `MyRentalsActivity`, etc.).
2. Explicit Intents passing parameters and IDs.
3. Basic Implicit Intent for WhatsApp (`wa.me`) and Dialer (`tel:`).
4. Responsive ConstraintLayout, LinearLayout, and CoordinatorLayout with BottomAppBar and FloatingActionButton cradle.
5. RecyclerView with custom Adapters and ViewHolders for products, rentals, and notifications.
6. HTTP/JSON client with asynchronous parsing, error handling, and session management.
7. Lightweight compressed picture storage in Firestore and local LRU bitmap memory caching.
8. FCM push notification service with high-importance sound and vibration channel (`rent_requests_v2`).

### Newly Added by You
1. **View Binding conversion**: Ensured `viewBinding true` across the project, converted `NotificationAdapter` from `findViewById` to `ItemNotificationBinding`, and cleaned remaining `findViewById` usages.
2. **Dynamic BroadcastReceiver**: Built `BaseActivity` dynamically registering receivers for Airplane Mode changes, Network Connectivity changes (with `NetworkCallback`), and Low Battery warnings with branded Snackbars.
3. **ContentProvider Contacts Integration**: Added "Contacts" button next to WhatsApp field in `AddProductActivity` querying `ContactsContract.CommonDataKinds.Phone` via `ContentResolver`, with runtime permission rationale, denied fallback, and automatic normalization to `01XXXXXXXXX`.
4. **Camera & FileProvider Integration**: Integrated `FileProvider` camera photo capture alongside MediaStore gallery picking in `AddProductActivity`.
5. **Foreground Service with Live Progress Notification**: Created `PhotoUploadForegroundService` (`foregroundServiceType="dataSync"`) displaying an ongoing notification with live progress bar during image compression and upload.
6. **Implicit Share Intent**: Added Share button (`↗`) in `ProductDetailsActivity` using `Intent.ACTION_SEND` (`text/plain`).
7. **Firebase Authentication Synchronization**: Integrated Firebase Auth login/signup synchronization alongside backend authentication, "Forgot Password" link triggering `sendPasswordResetEmail`, and visible Firebase UID on Profile screen.
8. **Firebase Realtime Database (RTDB)**: Implemented online/offline presence tracking (`/presence/<userId>`) and real-time live notification badge counter (`/unread/<userId>`) connected to backend triggers.
9. **Cloud Firestore Snapshot Listener**: Added live Firestore `addSnapshotListener` on `MyRentalsActivity` filtering by user ID to reflect instant rental status updates (pending → accepted/rejected).
10. **Landscape Layouts & State Survival**: Created `layout-land` variants for `activity_auth.xml`, `activity_main.xml`, and `activity_product_details.xml`, and implemented `onSaveInstanceState`/`onRestoreInstanceState` to retain form data across rotation.
11. **FCM Push Notification Overhaul (Channel `rent_requests_v3`)**: Enabled Google Services Gradle plugin, configured `FcmHelper` to sync tokens across login/app-start/`onNewToken`, created high-importance `rent_requests_v3` channel with sound & vibration, set heads-up visibility, handled deep-link intent routing (foreground & background), added runtime permission check & banner with settings shortcut, and added a test push feature.
12. **My Posts Screen & Product Lifecycle**: Built `MyPostsActivity` and `MyPostsAdapter` with pull-to-refresh, empty state, and error handling. Added edit product flow in `AddProductActivity` and backend `PUT /products/:id`. Added post deletion (`DELETE /products/:id`) that automatically cancels pending rental requests, alerts renters, and excludes deleted items from catalog and search.
13. **Hall Room Number Field**: Added `hallRoomNo` to user profile, registration screen, profile edit dialog, and owner rental request inspection.
14. **Cascading Account Deletion**: Implemented `DELETE /users/me` endpoint and client-side confirmation dialog (typing "DELETE") to cleanly purge all associated products, rentals, notifications, tokens, Auth account, and presence.

---

## 3. Console Steps to Perform Manually (If Required)

1. **Firebase Authentication (Console)**:
   - Go to [Firebase Console](https://console.firebase.google.com/) > Your Project (`borrowbox-13c4a`) > **Authentication** > **Sign-in method**.
   - Ensure **Email/Password** provider is enabled.
2. **Firebase Realtime Database (Console)**:
   - Go to **Build** > **Realtime Database**.
   - If not already created, click **Create Database** (location `us-central1`).
   - Under **Rules**, ensure authenticated read/write is allowed:
     ```json
     {
       "rules": {
         "presence": {
           "$uid": {
             ".read": "auth != null",
             ".write": "auth != null && auth.uid == $uid"
           }
         },
         "unread": {
           "$uid": {
             ".read": "auth != null",
             ".write": true
           }
         }
       }
     }
     ```
3. **Cloud Firestore Security Rules (Console)**:
   - Go to **Build** > **Firestore Database** > **Rules**.
   - To permit the app's snapshot listener on rentals:
     ```javascript
     rules_version = '2';
     service cloud.firestore {
       match /databases/{database}/documents {
         match /rentals/{rentalId} {
           allow read: if request.auth != null && (resource.data.renterId == request.auth.uid || resource.data.ownerId == request.auth.uid);
           allow write: if request.auth != null;
         }
         match /{document=**} {
           allow read, write: if request.auth != null;
         }
       }
     }
     ```
4. **google-services.json**:
   - The project already contains the matching `google-services.json` in `app/`. No replacement is needed unless you add a new client package ID.

---

## 4. App Rebuild Instructions

- **Do you need to rebuild the app?** **Yes.**
- Since new Android permissions, layouts, services, and dependencies (`firebase-database`) were added, sync Gradle and rebuild the application from Android Studio:
  ```bash
  Build > Rebuild Project
  ```
  or run from terminal:
  ```bash
  ./gradlew assembleDebug
  ```

# BorrowBox

A KUET-exclusive student borrowing and rental marketplace built as a university project.

## Description

BorrowBox allows KUET students to list, discover, borrow, and rent items within the campus community. The platform is scoped exclusively to verified KUET students.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java |
| UI | XML Layouts, View Binding |
| Authentication | Firebase Auth |
| Database (cloud) | Firebase Firestore |
| File Storage | Firebase Storage |
| Local Database | SQLite (via Room or raw SQLite) |
| Build | Gradle |

## Project Structure

```
app/src/main/java/com/borrowbox/kuet/
├── auth/             # Login, registration flows
├── verification/     # KUET student verification
├── home/             # Home screen
├── search/           # Search and filtering
├── items/            # Item listing and details
├── bookings/         # Booking and rental management
├── chat/             # In-app messaging
├── profile/          # User profile
├── notifications/    # Push and local notifications
├── localdatabase/    # SQLite / Room database
├── networking/       # API calls, Firebase helpers
├── background/       # WorkManager, services
├── models/           # Data model classes
├── adapters/         # RecyclerView adapters
├── repositories/     # Repository pattern classes
└── utils/            # Utility and helper classes
```

## Setup Steps

1. **Clone the repository**
   ```bash
   git clone <repo-url>
   cd BorrowBox
   ```

2. **Open in Android Studio** — File → Open → select the `BorrowBox` folder.

3. **Add Firebase configuration** — See the [Firebase Setup](#firebase-setup) section below.

4. **Build** — Let Android Studio sync Gradle, then run:
   ```bash
   ./gradlew assembleDebug
   ```

## Firebase Setup

> **`google-services.json` is NOT included in this repository** and must be added manually.

1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a project named `BorrowBox`.
2. Register an Android app with package name `com.borrowbox.kuet`.
3. Download `google-services.json` and place it at `app/google-services.json`.
4. Enable **Firebase Auth**, **Firestore**, and **Storage** in the Firebase Console.
5. Uncomment the Firebase dependencies in `app/build.gradle` and add the `google-services` plugin.

⚠️ Never commit `google-services.json` to version control. It is listed in `.gitignore`.

## Known Limitations

<!-- To be filled in as the project progresses -->

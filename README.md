# BorrowBox

A KUET-exclusive student borrowing and rental marketplace built as a university project.

## Description

BorrowBox allows KUET students to list, discover, borrow, and rent items within the campus community. The platform is scoped exclusively to verified KUET students.

## Tech Stack

| Layer | Technology |
|---|---|
| Mobile App | Native Android (Java) |
| UI | Material Components 3/AndroidX, XML Layouts, View Binding |
| Backend API | Node.js + Express (located in `/backend`) |
| Cloud Database | MySQL (via `mysql2`) |
| Local Database | SQLite (Recent searches) |
| Authentication | JWT + bcrypt password hashing + KUET 6-digit Email OTP |
| Build System | Gradle 8.14 |

---

## Project Structure

```
BorrowBox/
├── backend/                  # Node.js + Express + MySQL Backend API
│   ├── .env.example          # Environment template
│   ├── schema.sql            # MySQL schema & initial seed data
│   └── src/
│       ├── config/kuet.js    # KUET roll format & department codes config
│       ├── db/               # MySQL connection pool & init script
│       ├── middleware/       # JWT auth & optional auth middleware
│       ├── routes/           # /auth, /products, /rentals
│       ├── services/         # 6-digit crypto OTP email service
│       └── server.js         # Express app entrypoint
├── app/src/main/
│   ├── java/com/borrowbox/kuet/
│   │   ├── auth/             # WelcomeActivity, AuthActivity (login/register unified), SplashActivity
│   │   ├── verification/     # VerificationActivity (6-box OTP & resend cooldown)
│   │   ├── items/            # ProductDetailsActivity (full separate page)
│   │   ├── networking/       # ApiClient, SessionManager
│   │   ├── bookings/         # RentActivity, Rental reviews
│   │   ├── profile/          # ProfileActivity (status badge, logout)
│   │   ├── search/           # SearchActivity
│   │   ├── models/           # Product, UserProfile, Rental
│   │   └── utils/AppConfig.java # KUET roll regex, Dept codes, API Base URL
│   └── res/
│       ├── layout/           # activity_main, activity_auth, activity_product_details, etc.
│       └── values/           # colors.xml, themes.xml, strings.xml
└── build.gradle
```

---

## Backend & MySQL Setup

1. **Install & Start MySQL**:
   - macOS: `brew install mysql && brew services start mysql`
   - Linux: `sudo apt install mysql-server && sudo systemctl start mysql`
   - Windows: Start MySQL via XAMPP, WAMP, or MySQL Windows Service.

2. **Configure Backend (`.env`)**:
   ```bash
   cd backend
   cp .env.example .env
   ```
   Edit `.env` with your MySQL credentials (`DB_USER`, `DB_PASSWORD`) and Gmail SMTP credentials:
   ```env
   PORT=5050
   DB_HOST=localhost
   DB_PORT=3306
   DB_USER=root
   DB_PASSWORD=
   DB_NAME=borrowbox

   # Gmail SMTP for Email Verification
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=465
   SMTP_SECURE=true
   SMTP_USER=your.email@gmail.com
   SMTP_PASS=your_16_char_google_app_password
   MAIL_FROM="BorrowBox KUET" <your.email@gmail.com>
   ```

   > **How to create a Gmail App Password:**
   > 1. Go to your Google Account (myaccount.google.com) → **Security**.
   > 2. Ensure **2-Step Verification** is turned ON.
   > 3. Search for **App passwords** (or go to myaccount.google.com/apppasswords).
   > 4. Create an App password named "BorrowBox" and paste the generated 16-character code into `SMTP_PASS`.

3. **Install Dependencies & Initialize Database**:
   ```bash
   cd backend
   npm install
   npm run db:init
   ```

4. **Verify Email Setup**:
   ```bash
   npm run test:mail -- your_kuet_email@stud.kuet.ac.bd
   ```

5. **Start the API Server**:
   ```bash
   npm start
   # Server listens on 0.0.0.0:5050
   ```
   > **Note:** In development mode, if SMTP credentials are not yet configured, 6-digit OTP verification codes will be printed visibly in the terminal (`VERIFICATION CODE for <email>: 123456`) so you can continue testing immediately.

---

## Network & Firewall Configuration for Physical Android Phones

When testing on a **real Android phone** connected to the same Wi-Fi router as your computer:

### 1. Allow Inbound Traffic on Port 5050 (Windows Firewall)
Run Command Prompt as Administrator:
```cmd
netsh advfirewall firewall add rule name="BorrowBox API" dir=in action=allow protocol=TCP localport=5050
```
Or in PowerShell as Administrator:
```powershell
New-NetFirewallRule -DisplayName "BorrowBox API" -Direction Inbound -LocalPort 5050 -Protocol TCP -Action Allow
```

*(On macOS, go to System Settings → Network → Firewall and ensure Node.js is allowed).*

### 2. Find Your PC's Wi-Fi LAN IP Address
- **Windows**: Run `ipconfig` in Command Prompt. Look for `IPv4 Address` under your active **Wireless LAN adapter Wi-Fi** (e.g. `192.168.0.xxx` or `10.40.xx.xxx`).
- **macOS**: Run `ipconfig getifaddr en0`.
- **Linux**: Run `hostname -I`.

### 3. Verify Server Reachability from Phone
Open your phone's browser (Chrome) and navigate to:
```
http://<YOUR_PC_LAN_IP>:5050/health
```
You should see:
```json
{"ok":true,"db":true,"smtp":true,"app":"BorrowBox KUET Backend API",...}
```

### 4. Alternative USB Debugging Backup (`adb reverse`)
If Wi-Fi isolation is enabled on your router or you don't have Wi-Fi:
1. Connect your phone via USB with USB Debugging enabled.
2. Run:
   ```bash
   adb reverse tcp:5050 tcp:5050
   ```
3. Set the app's Server URL to:
   ```
   http://127.0.0.1:5050
   ```
All traffic from your phone on port 5050 will route directly through the USB cable to your PC!

---

## Running the Mobile App

### 1. In-App Server URL Configuration (No Rebuilding Needed!)
You do **not** need to rebuild the APK when switching Wi-Fi networks:
- **Option A**: Long-press on the **BorrowBox logo** on the Welcome or Login screen to open the **Server Configuration** dialog.
- **Option B**: If the server is unreachable at startup, a banner appears with a **Change Server URL** button. Tap it, enter your PC IP (e.g. `http://192.168.0.109:5050`), tap **Test**, and tap **Save**. It is persistently stored.

### 2. Build-Time Default URL
The default URL is defined in `app/build.gradle`:
```groovy
buildConfigField "String", "DEFAULT_API_BASE_URL", "\"${project.findProperty('API_BASE_URL') ?: 'http://10.40.35.181:5050'}\""
```
To build with a custom default URL:
```bash
./gradlew assembleDebug -PAPI_BASE_URL="http://192.168.0.109:5050"
```

### 3. Build Debug APK
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew assembleDebug
```
The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Features & Verification System

- **Safe-Area Top & Bottom**: Layout respects camera cutout, status bar, and gesture navigation bars. Header extends seamlessly behind the status bar.
- **Unified Auth**: Single Auth screen with segmented toggle between Log In and Register. On registration, the user is logged in immediately and sent directly to verification.
- **Roll Number Check**: Enforces KUET format (`2-digit batch` + `2-digit dept code` + `3-digit serial`, e.g. `2307108`).
- **Email OTP Verification**: 6-box numeric code input with 10-minute expiry, 5 wrong attempts limit, and 60-second resend cooldown.
- **Backend Verification Guard**: `POST /rentals` rejects unverified users with HTTP 403 Forbidden.
- **Product Details Page**: Full separate screen (not a dialog) with item details, owner contact information (masked for guests), date selection, and rent action.

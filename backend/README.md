# BorrowBox KUET Backend API & MySQL Database Setup

A Node.js + Express backend with MySQL database integration (`mysql2`) built for the BorrowBox KUET student borrowing/rental marketplace.

---

## 1. Prerequisites

1. **Node.js** (v18 or newer)
2. **MySQL Server** (v8.0+ or MariaDB 10.5+)

---

## 2. Installing MySQL

### macOS (Homebrew)
```bash
brew install mysql
brew services start mysql
```
Set up root password (optional):
```bash
mysql_secure_installation
```

### Windows
1. Download MySQL Community Server or XAMPP / WampServer.
2. Start MySQL via MySQL Workbench or the XAMPP Control Panel.

### Ubuntu / Debian Linux
```bash
sudo apt update
sudo apt install mysql-server
sudo systemctl start mysql
```

---

## 3. Database Setup

You can initialize the database using either the auto-migration script or manual SQL import.

### Option A: Auto-Migration (Recommended)
1. Configure `.env` (see step 4).
2. Run:
```bash
cd backend
npm run db:init
```
This automatically connects to MySQL, creates the `borrowbox` database if it doesn't exist, executes `schema.sql`, and seeds initial sample users and items.

### Option B: Manual Import via MySQL CLI
```bash
mysql -u root -p < schema.sql
```

---

## 4. Environment Configuration (`.env`)

In the `/backend` directory, create or edit `.env` (copied from `.env.example`):

```env
# BorrowBox Backend Configuration
PORT=5050

# MySQL Database Configuration
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=your_mysql_password
DB_NAME=borrowbox

# JWT Authentication
JWT_SECRET=borrowbox_kuet_super_secret_jwt_key_2026_xyz
JWT_EXPIRES_IN=7d

# SMTP Email Configuration (Optional in development)
# If left empty, verification OTP codes will be printed directly to the server console!
SMTP_HOST=
SMTP_PORT=587
SMTP_USER=
SMTP_PASS=
SMTP_SECURE=false
SMTP_FROM="BorrowBox KUET" <no-reply@kuet.ac.bd>

# Environment
NODE_ENV=development
```

> **Development Tip:** If SMTP settings are blank, whenever a verification code is requested, it is logged directly to the server terminal:
> ```
> =======================================================
> [VERIFICATION OTP] For: 2307108@stud.kuet.ac.bd
> [VERIFICATION OTP] Code: 491823 (Expires in 10 minutes)
> =======================================================
> ```

---

## 5. Running the Backend

```bash
cd backend
npm install
npm start
```

For live reloading during development:
```bash
npm run dev
```

The server will start listening at:
`http://localhost:5050`

---

## 6. Pointing the Mobile App to the API URL

The mobile app's API base URL is configured in `app/src/main/java/com/borrowbox/kuet/utils/AppConfig.java`:

```java
public static String API_BASE_URL = "http://10.0.2.2:5050";
```

### Connecting from Android Studio Emulator
The Android emulator cannot reach host `localhost` directly. Android provides special alias IP `10.0.2.2` which routes to your computer's `localhost`:
- Set: `AppConfig.API_BASE_URL = "http://10.0.2.2:5050";`

### Connecting from a Physical Android Phone
1. Connect your phone and PC to the same Wi-Fi network.
2. Find your PC's local IP address:
   - **Windows:** `ipconfig` (look for `IPv4 Address` under your active Wireless LAN adapter)
   - **macOS:** `ipconfig getifaddr en0`
   - **Linux:** `hostname -I`
3. **Allow Inbound Traffic on Port 5050 (Windows Firewall)**:
   In Command Prompt (Run as Administrator):
   ```cmd
   netsh advfirewall firewall add rule name="BorrowBox API" dir=in action=allow protocol=TCP localport=5050
   ```
   Or PowerShell (Run as Administrator):
   ```powershell
   New-NetFirewallRule -DisplayName "BorrowBox API" -Direction Inbound -LocalPort 5050 -Protocol TCP -Action Allow
   ```
4. **Change Server URL on Phone (No Rebuild Needed)**:
   - Long-press the BorrowBox logo on the Login/Welcome screen.
   - Or tap "Change Server URL" on the startup connection banner.
   - Enter `http://<YOUR_PC_LAN_IP>:5050` (or tap the **LAN Wi-Fi** preset).
   - Tap **Test** then **Save**.

### USB Debugging Fallback (No Wi-Fi Needed)
If your Wi-Fi blocks peer-to-peer traffic:
1. Connect phone via USB cable with USB Debugging enabled.
2. Run on your PC:
   ```bash
   adb reverse tcp:5050 tcp:5050
   ```
3. In the app's Server URL setting, set:
   ```
   http://127.0.0.1:5050
   ```

---

## 7. API Endpoints Reference

| Method | Endpoint | Auth Required | Description |
|---|---|---|---|
| `POST` | `/auth/register` | No | Register new student, checks KUET roll format, hashes password, logs in immediately, dispatches first OTP |
| `POST` | `/auth/login` | No | Login with email or roll number + password |
| `POST` | `/auth/send-code` | Optional (Token or Email) | Resends 6-digit OTP code with 60-second cooldown |
| `POST` | `/auth/verify-code` | Optional (Token or Email) | Verifies 6-digit code, marks `is_email_verified = 1`, unlocks renting |
| `GET` | `/auth/me` | Bearer Token | Returns current authenticated user profile |
| `GET` | `/products` | Optional | List products (supports `?search=` and `?category=`) |
| `GET` | `/products/:id` | Optional | Full product details. Owner contact info visible to logged-in users, masked for guests |
| `POST` | `/products` | Bearer Token | Add new product listing |
| `POST` | `/rentals` | Bearer Token | Create rental request. **Rejects unverified users with 403 Forbidden** |
| `GET` | `/rentals/mine` | Bearer Token | Get user's submitted borrow requests & incoming requests for owned items |
| `GET` | `/health` | No | Backend health check |

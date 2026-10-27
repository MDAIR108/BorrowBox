const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });

const express = require('express');
const cors = require('cors');
const os = require('os');

const pool = require('./db/connection');
const initDatabase = require('./db/init');
const { verifySmtpConnection, checkMissingSmtpConfig } = require('./services/emailService');
const authRoutes = require('./routes/authRoutes');
const productRoutes = require('./routes/productRoutes');
const rentalRoutes = require('./routes/rentalRoutes');

const app = express();
const PORT = process.env.PORT || 5050;

// Enable CORS for all origins, methods and headers
app.use(cors({
  origin: '*',
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization']
}));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Request Logger
app.use((req, res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
  next();
});

// Health check endpoint (Step 1.4)
// Returns { ok: true, db: true/false, smtp: true/false }
app.get('/health', async (req, res) => {
  let dbOk = false;
  let smtpOk = false;

  // Check MySQL with simple query
  try {
    dbOk = await pool.checkMysql();
  } catch (err) {
    dbOk = false;
  }

  // Check SMTP with transporter.verify()
  try {
    smtpOk = await verifySmtpConnection();
  } catch (err) {
    smtpOk = false;
  }

  res.json({
    ok: true,
    db: dbOk,
    smtp: smtpOk,
    app: 'BorrowBox KUET Backend API',
    timestamp: new Date().toISOString()
  });
});

app.get('/', (req, res) => {
  res.json({
    message: 'Welcome to BorrowBox KUET API',
    version: '1.0.0',
    endpoints: {
      auth: ['POST /auth/register', 'POST /auth/login', 'POST /auth/send-code', 'POST /auth/verify-code', 'GET /auth/me'],
      products: ['GET /products', 'GET /products/:id', 'POST /products'],
      rentals: ['POST /rentals', 'GET /rentals/mine'],
      health: ['GET /health']
    }
  });
});

// Mount Routes
app.use('/auth', authRoutes);
app.use('/products', productRoutes);
app.use('/rentals', rentalRoutes);

// Error handling middleware
app.use((err, req, res, next) => {
  console.error('[Unhandled Server Error]', err);
  res.status(err.status || 500).json({
    error: err.message || 'Internal Server Error'
  });
});

// Helper: Detect all non-internal IPv4 LAN addresses
function getLanIpv4Addresses() {
  const interfaces = os.networkInterfaces();
  const addresses = [];
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name]) {
      if (net.family === 'IPv4' && !net.internal) {
        addresses.push({ iface: name, address: net.address });
      }
    }
  }
  return addresses;
}

// Auto-run DB init on startup, then listen on 0.0.0.0:PORT
async function startServer() {
  // Check and report missing SMTP variables
  const missingSmtp = checkMissingSmtpConfig();
  if (missingSmtp.length > 0) {
    console.log(`[SMTP Config] ℹ️  Missing SMTP environment variables: ${missingSmtp.join(', ')}`);
    if ((process.env.NODE_ENV || 'development') !== 'production') {
      console.log(`[SMTP Config] ℹ️  Development mode: OTP codes will be printed directly to the console.`);
    } else {
      console.warn(`[SMTP Config] ⚠️  Production warning: Set SMTP credentials in .env to deliver verification emails.`);
    }
  } else {
    console.log(`[SMTP Config] ✅  All SMTP variables present (${process.env.SMTP_HOST}:${process.env.SMTP_PORT})`);
  }

  try {
    await initDatabase();
    console.log('[Server] Database initialized.');
  } catch (dbErr) {
    console.warn('[Server] Could not connect to MySQL at startup:', dbErr.message);
    console.warn('[Server] Continuing startup so healthcheck and mock/fallback routes remain responsive.');
  }

  // Listen on 0.0.0.0 so both localhost, emulator, and LAN devices can connect
  app.listen(PORT, '0.0.0.0', () => {
    const lanAddresses = getLanIpv4Addresses();

    console.log(`=======================================================`);
    console.log(`🚀 BorrowBox Backend running on http://0.0.0.0:${PORT}`);
    console.log(`   - Localhost:          http://localhost:${PORT}`);
    if (lanAddresses.length > 0) {
      lanAddresses.forEach(({ iface, address }) => {
        console.log(`   - LAN (${iface}):        http://${address}:${PORT}`);
      });
    } else {
      console.log(`   - LAN:                (No active external IPv4 interface detected)`);
    }
    console.log(`   - Android Emulator:   http://10.0.2.2:${PORT}`);
    console.log(`   - USB Debug (adb):    http://127.0.0.1:${PORT} (run: adb reverse tcp:${PORT} tcp:${PORT})`);
    console.log(`   - Health check:       http://localhost:${PORT}/health`);
    console.log(`=======================================================`);

    // Verify SMTP after server starts
    verifySmtpConnection();
  });
}

startServer();

module.exports = app;

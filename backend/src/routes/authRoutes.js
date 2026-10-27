const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const pool = require('../db/connection');
const { validateKuetRoll } = require('../config/kuet');
const { generateOtpCode, hashCode, sendVerificationEmail } = require('../services/emailService');
const { requireAuth, JWT_SECRET } = require('../middleware/authMiddleware');

const router = express.Router();

/**
 * POST /auth/register
 * Single registration flow with instant login token & direct OTP dispatch.
 */
router.post('/register', async (req, res) => {
  try {
    const {
      full_name,
      roll_no,
      email,
      phone,
      department,
      hall,
      password,
      confirm_password
    } = req.body;

    // 1. Basic validation
    if (!full_name || !roll_no || !email || !phone || !department || !hall || !password) {
      return res.status(400).json({ error: 'All fields are required.' });
    }

    if (password.length < 6) {
      return res.status(400).json({ error: 'Password must be at least 6 characters long.' });
    }

    if (confirm_password && password !== confirm_password) {
      return res.status(400).json({ error: 'Passwords do not match.' });
    }

    // 2. Roll number format verification (TASK 6)
    const rollCheck = validateKuetRoll(roll_no);
    if (!rollCheck.isValid) {
      return res.status(400).json({ error: rollCheck.error });
    }

    const cleanRoll = roll_no.trim();

    // 3. Flexible email validation (Step 2.1)
    // Accept any valid email; if @stud.kuet.ac.bd, ensure embedded roll digits match roll_no
    const normalizedEmail = email.trim().toLowerCase();
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(normalizedEmail)) {
      return res.status(400).json({ error: 'Please enter a valid email address.' });
    }

    if (normalizedEmail.endsWith('@stud.kuet.ac.bd')) {
      const localPart = normalizedEmail.split('@')[0];
      const digitMatch = localPart.match(/\d+/);
      if (digitMatch) {
        const emailDigits = digitMatch[0];
        if (emailDigits !== cleanRoll) {
          return res.status(400).json({
            error: `Roll number in student email (${emailDigits}) does not match entered roll number (${cleanRoll}).`
          });
        }
      }
    }

    // 4. Check uniqueness
    const [existingUsers] = await pool.query(
      'SELECT id, roll_no, email FROM users WHERE roll_no = ? OR email = ?',
      [cleanRoll, normalizedEmail]
    );

    if (existingUsers.length > 0) {
      const match = existingUsers[0];
      if (match.roll_no === cleanRoll) {
        return res.status(409).json({ error: `Roll number ${cleanRoll} is already registered.` });
      }
      if (match.email.toLowerCase() === normalizedEmail) {
        return res.status(409).json({ error: `Email ${normalizedEmail} is already registered.` });
      }
    }

    // 5. Hash password
    const salt = await bcrypt.genSalt(10);
    const passwordHash = await bcrypt.hash(password, salt);

    // 6. Insert new user (is_roll_verified = 1 because format matches KUET standard)
    const [insertResult] = await pool.query(
      `INSERT INTO users (full_name, roll_no, email, password_hash, phone, department, hall, is_email_verified, is_roll_verified)
       VALUES (?, ?, ?, ?, ?, ?, ?, 0, 1)`,
      [full_name.trim(), cleanRoll, normalizedEmail, passwordHash, phone.trim(), department.trim(), hall.trim()]
    );

    const userId = insertResult.insertId;

    // 7. Generate JWT token for instant login (TASK 4)
    const token = jwt.sign(
      { userId, email: normalizedEmail, rollNo: cleanRoll },
      JWT_SECRET,
      { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
    );

    const user = {
      id: userId,
      full_name: full_name.trim(),
      roll_no: cleanRoll,
      email: normalizedEmail,
      phone: phone.trim(),
      department: department.trim(),
      hall: hall.trim(),
      is_email_verified: 0,
      is_roll_verified: 1
    };

    return res.status(201).json({
      message: 'Registration successful! Proceeding to email verification.',
      token,
      user
    });
  } catch (err) {
    console.error('[Register Error]', err);
    return res.status(500).json({ error: 'Server error during registration: ' + err.message });
  }
});

/**
 * POST /auth/login
 * Log in with email OR roll number + password.
 */
router.post('/login', async (req, res) => {
  try {
    const { identifier, email, roll_no, password } = req.body;
    const loginId = (identifier || email || roll_no || '').trim();

    if (!loginId || !password) {
      return res.status(400).json({ error: 'Email or Roll number and password are required.' });
    }

    // Query user by email OR roll_no
    const [rows] = await pool.query(
      'SELECT * FROM users WHERE email = ? OR roll_no = ?',
      [loginId.toLowerCase(), loginId]
    );

    if (rows.length === 0) {
      return res.status(401).json({ error: 'No account found with that email or roll number.' });
    }

    const userRecord = rows[0];

    const isMatch = await bcrypt.compare(password, userRecord.password_hash);
    if (!isMatch) {
      return res.status(401).json({ error: 'Incorrect password. Please try again.' });
    }

    const token = jwt.sign(
      { userId: userRecord.id, email: userRecord.email, rollNo: userRecord.roll_no },
      JWT_SECRET,
      { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
    );

    const user = {
      id: userRecord.id,
      full_name: userRecord.full_name,
      roll_no: userRecord.roll_no,
      email: userRecord.email,
      phone: userRecord.phone,
      department: userRecord.department,
      hall: userRecord.hall,
      is_email_verified: Boolean(userRecord.is_email_verified),
      is_roll_verified: Boolean(userRecord.is_roll_verified),
      created_at: userRecord.created_at
    };

    return res.json({
      message: 'Login successful',
      token,
      user
    });
  } catch (err) {
    console.error('[Login Error]', err);
    return res.status(500).json({ error: 'Server error during login: ' + err.message });
  }
});

/**
 * POST /auth/send-code
 * Send/resend 6-digit OTP code with 60-second cooldown (TASK 6).
 */
router.post('/send-code', async (req, res) => {
  try {
    let userId = null;
    let targetEmail = null;
    let fullName = 'KUETian';

    // If authenticated via Bearer token
    const authHeader = req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
      try {
        const decoded = jwt.verify(authHeader.split(' ')[1], JWT_SECRET);
        userId = decoded.userId;
      } catch (e) {
        // Fall through to body params
      }
    }

    if (userId) {
      const [u] = await pool.query('SELECT id, email, full_name FROM users WHERE id = ?', [userId]);
      if (u.length > 0) {
        targetEmail = u[0].email;
        fullName = u[0].full_name;
      }
    } else if (req.body.email || req.body.roll_no) {
      const idStr = (req.body.email || req.body.roll_no).trim();
      const [u] = await pool.query('SELECT id, email, full_name FROM users WHERE email = ? OR roll_no = ?', [idStr.toLowerCase(), idStr]);
      if (u.length > 0) {
        userId = u[0].id;
        targetEmail = u[0].email;
        fullName = u[0].full_name;
      }
    }

    if (!userId || !targetEmail) {
      return res.status(400).json({ error: 'User identifier or active session required to send verification code.' });
    }

    // Cooldown check (60 seconds)
    const [lastRecord] = await pool.query(
      'SELECT created_at FROM email_verifications WHERE user_id = ? ORDER BY id DESC LIMIT 1',
      [userId]
    );

    if (lastRecord.length > 0) {
      const lastCreated = new Date(lastRecord[0].created_at).getTime();
      const diffSecs = Math.floor((Date.now() - lastCreated) / 1000);
      if (diffSecs < 60) {
        const remaining = 60 - diffSecs;
        return res.status(429).json({
          error: `Please wait ${remaining}s before requesting a new code.`,
          retryAfter: remaining
        });
      }
    }

    // Generate new OTP
    const otpCode = generateOtpCode();
    const codeHash = hashCode(otpCode);
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes

    await pool.query(
      `INSERT INTO email_verifications (user_id, code_hash, expires_at, attempts, used)
       VALUES (?, ?, ?, 0, 0)`,
      [userId, codeHash, expiresAt]
    );

    const sendResult = await sendVerificationEmail(targetEmail, otpCode, fullName);

    if (!sendResult.success && sendResult.mode !== 'console' && sendResult.mode !== 'fallback_console') {
      // SMTP genuinely failed — tell the client so they know the code wasn't delivered
      return res.status(500).json({
        error: 'Could not send verification email. Please try again or contact support.',
        detail: sendResult.error
      });
    }

    return res.json({
      message: `Verification code sent to ${targetEmail}.`,
      cooldownSeconds: 60
    });
  } catch (err) {
    console.error('[Send Code Error]', err);
    return res.status(500).json({ error: 'Failed to send verification code: ' + err.message });
  }
});

/**
 * POST /auth/verify-code
 * Verify 6-digit OTP code (TASK 6).
 */
router.post('/verify-code', async (req, res) => {
  try {
    const { code, email, roll_no } = req.body;

    if (!code || code.trim().length !== 6) {
      return res.status(400).json({ error: 'Please enter a valid 6-digit verification code.' });
    }

    let userId = null;

    // Check auth header first
    const authHeader = req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
      try {
        const decoded = jwt.verify(authHeader.split(' ')[1], JWT_SECRET);
        userId = decoded.userId;
      } catch (e) {
        // Ignore and check email/roll
      }
    }

    if (!userId && (email || roll_no)) {
      const idStr = (email || roll_no).trim();
      const [u] = await pool.query('SELECT id FROM users WHERE email = ? OR roll_no = ?', [idStr.toLowerCase(), idStr]);
      if (u.length > 0) {
        userId = u[0].id;
      }
    }

    if (!userId) {
      return res.status(400).json({ error: 'User identifier or session required.' });
    }

    // Retrieve most recent unused verification for this user
    const [records] = await pool.query(
      'SELECT * FROM email_verifications WHERE user_id = ? AND used = 0 ORDER BY id DESC LIMIT 1',
      [userId]
    );

    if (records.length === 0) {
      return res.status(400).json({ error: 'No active verification code found. Please request a new code.' });
    }

    const verification = records[0];

    // Check expiration
    if (new Date(verification.expires_at).getTime() < Date.now()) {
      return res.status(400).json({ error: 'This verification code has expired. Please request a new code.' });
    }

    // Check max attempts (limit 5)
    if (verification.attempts >= 5) {
      await pool.query('UPDATE email_verifications SET used = 1 WHERE id = ?', [verification.id]);
      return res.status(400).json({ error: 'Too many incorrect attempts (5/5). Code invalidated. Please request a new code.' });
    }

    // Verify code hash
    const inputHash = hashCode(code);
    if (inputHash !== verification.code_hash) {
      const newAttempts = verification.attempts + 1;
      await pool.query('UPDATE email_verifications SET attempts = ? WHERE id = ?', [newAttempts, verification.id]);
      const remainingAttempts = 5 - newAttempts;
      return res.status(400).json({
        error: `Incorrect code. ${remainingAttempts} attempt${remainingAttempts === 1 ? '' : 's'} remaining.`
      });
    }

    // Mark verification used
    await pool.query('UPDATE email_verifications SET used = 1 WHERE id = ?', [verification.id]);

    // Update user: is_email_verified = 1
    await pool.query('UPDATE users SET is_email_verified = 1 WHERE id = ?', [userId]);

    // Return updated user profile
    const [updatedRows] = await pool.query(
      'SELECT id, full_name, roll_no, email, phone, department, hall, is_email_verified, is_roll_verified, created_at FROM users WHERE id = ?',
      [userId]
    );

    return res.json({
      message: 'Email verified successfully! KUET student privileges and rentals are now unlocked.',
      user: {
        ...updatedRows[0],
        is_email_verified: Boolean(updatedRows[0].is_email_verified),
        is_roll_verified: Boolean(updatedRows[0].is_roll_verified)
      }
    });
  } catch (err) {
    console.error('[Verify Code Error]', err);
    return res.status(500).json({ error: 'Server error during code verification: ' + err.message });
  }
});

/**
 * GET /me
 * Return current logged in user details.
 */
router.get('/me', requireAuth, async (req, res) => {
  return res.json({
    user: {
      ...req.user,
      is_email_verified: Boolean(req.user.is_email_verified),
      is_roll_verified: Boolean(req.user.is_roll_verified)
    }
  });
});

module.exports = router;

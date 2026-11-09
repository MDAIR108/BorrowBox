'use strict';

const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const {
  findUserByRollOrEmail,
  findUserById,
  createUser,
  updateUser,
  saveVerification,
  getLatestVerification,
  updateVerification
} = require('../db/firebase');
const { validateKuetRoll } = require('../config/kuet');
const { generateOtpCode, hashCode, sendVerificationEmail } = require('../services/emailService');
const { requireAuth, JWT_SECRET } = require('../middleware/authMiddleware');

const router = express.Router();

/**
 * POST /auth/register
 * Register a student account with Firestore / Firebase.
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

    // 2. Roll number format verification
    const rollCheck = validateKuetRoll(roll_no);
    if (!rollCheck.isValid) {
      return res.status(400).json({ error: rollCheck.error });
    }

    const cleanRoll = roll_no.trim();

    // 3. Normal email validation (must contain @ and a valid domain)
    const normalizedEmail = email.trim().toLowerCase();
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(normalizedEmail)) {
      return res.status(400).json({ error: 'Please enter a valid email address.' });
    }

    // 4. Check uniqueness in Firestore
    const existingRoll = await findUserByRollOrEmail(cleanRoll);
    if (existingRoll && (existingRoll.roll_no === cleanRoll || existingRoll.rollNumber === cleanRoll)) {
      return res.status(409).json({ error: `Roll number ${cleanRoll} is already registered.` });
    }

    const existingEmail = await findUserByRollOrEmail(normalizedEmail);
    if (existingEmail && existingEmail.email && existingEmail.email.toLowerCase() === normalizedEmail) {
      return res.status(409).json({ error: `Email ${normalizedEmail} is already registered.` });
    }

    // 5. Hash password
    const salt = await bcrypt.genSalt(10);
    const passwordHash = await bcrypt.hash(password, salt);

    // 6. Insert new user into Firestore
    const newUser = await createUser({
      full_name: full_name.trim(),
      fullName: full_name.trim(),
      roll_no: cleanRoll,
      rollNumber: cleanRoll,
      email: normalizedEmail,
      password_hash: passwordHash,
      phone: phone.trim(),
      phoneNumber: phone.trim(),
      department: department.trim(),
      hall: hall.trim(),
      hallName: hall.trim(),
      presentAddress: hall.trim(),
      is_email_verified: false,
      is_roll_verified: true,
      verificationStatus: 'Pending',
      trustScore: 5.0,
      role: 'user'
    });

    const userId = newUser.id || newUser.uid;

    // 7. Generate JWT token for instant login
    const token = jwt.sign(
      { userId, email: normalizedEmail, rollNo: cleanRoll },
      JWT_SECRET,
      { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
    );

    const user = {
      id: userId,
      uid: userId,
      full_name: full_name.trim(),
      fullName: full_name.trim(),
      roll_no: cleanRoll,
      rollNumber: cleanRoll,
      email: normalizedEmail,
      phone: phone.trim(),
      phoneNumber: phone.trim(),
      department: department.trim(),
      hall: hall.trim(),
      hallName: hall.trim(),
      is_email_verified: false,
      is_roll_verified: true
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

    const userRecord = await findUserByRollOrEmail(loginId);
    if (!userRecord) {
      return res.status(401).json({ error: 'No account found with that email or roll number.' });
    }

    const isMatch = await bcrypt.compare(password, userRecord.password_hash);
    if (!isMatch) {
      return res.status(401).json({ error: 'Incorrect password. Please try again.' });
    }

    const userId = userRecord.id || userRecord.uid;

    const token = jwt.sign(
      { userId, email: userRecord.email, rollNo: userRecord.roll_no || userRecord.rollNumber },
      JWT_SECRET,
      { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
    );

    const user = {
      id: userId,
      uid: userId,
      full_name: userRecord.full_name || userRecord.fullName || 'Student',
      fullName: userRecord.fullName || userRecord.full_name || 'Student',
      roll_no: userRecord.roll_no || userRecord.rollNumber || '',
      rollNumber: userRecord.rollNumber || userRecord.roll_no || '',
      email: userRecord.email,
      phone: userRecord.phone || userRecord.phoneNumber || '',
      phoneNumber: userRecord.phoneNumber || userRecord.phone || '',
      department: userRecord.department || 'KUET',
      hall: userRecord.hall || userRecord.hallName || 'Campus',
      hallName: userRecord.hallName || userRecord.hall || 'Campus',
      is_email_verified: Boolean(userRecord.is_email_verified),
      is_roll_verified: Boolean(userRecord.is_roll_verified),
      created_at: userRecord.created_at || userRecord.createdAt
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
 * Send/resend 6-digit OTP code to user's email.
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
        // Fall through
      }
    }

    if (userId) {
      const u = await findUserById(userId);
      if (u) {
        targetEmail = u.email;
        fullName = u.full_name || u.fullName || fullName;
      }
    }

    if (!targetEmail && (req.body.email || req.body.roll_no)) {
      const idStr = (req.body.email || req.body.roll_no).trim();
      const u = await findUserByRollOrEmail(idStr);
      if (u) {
        userId = u.id || u.uid;
        targetEmail = u.email;
        fullName = u.full_name || u.fullName || fullName;
      }
    }

    if (!targetEmail && req.body.email) {
      targetEmail = req.body.email.trim();
    }

    if (!targetEmail) {
      return res.status(400).json({ error: 'User identifier or active session required to send verification code.' });
    }

    // Cooldown check (60 seconds)
    const lastRecord = await getLatestVerification(userId, targetEmail);
    if (lastRecord && lastRecord.createdAt) {
      const lastCreated = new Date(lastRecord.createdAt).getTime();
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
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

    await saveVerification({
      user_id: String(userId || ''),
      email: targetEmail.toLowerCase(),
      code_hash: codeHash,
      expires_at: expiresAt,
      attempts: 0,
      used: false
    });

    const sendResult = await sendVerificationEmail(targetEmail, otpCode, fullName);

    if (!sendResult.success) {
      return res.status(500).json({
        error: sendResult.error || 'Could not send verification email. Please check server SMTP configuration.',
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
 * Verify 6-digit OTP code against the one sent.
 */
router.post('/verify-code', async (req, res) => {
  try {
    const { code, email, roll_no } = req.body;

    if (!code || code.trim().length !== 6) {
      return res.status(400).json({ error: 'Please enter a valid 6-digit verification code.' });
    }

    let userId = null;
    let targetEmail = email ? email.trim().toLowerCase() : null;

    const authHeader = req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
      try {
        const decoded = jwt.verify(authHeader.split(' ')[1], JWT_SECRET);
        userId = decoded.userId;
      } catch (e) {
        // Ignore
      }
    }

    if (!userId && (email || roll_no)) {
      const idStr = (email || roll_no).trim();
      const u = await findUserByRollOrEmail(idStr);
      if (u) {
        userId = u.id || u.uid;
        targetEmail = u.email;
      }
    }

    if (!userId && !targetEmail) {
      return res.status(400).json({ error: 'User identifier or session required.' });
    }

    // Retrieve most recent unused verification
    const verification = await getLatestVerification(userId, targetEmail);
    if (!verification || verification.used) {
      return res.status(400).json({ error: 'No active verification code found. Please request a new code.' });
    }

    // Check expiration (10 min)
    if (new Date(verification.expires_at).getTime() < Date.now()) {
      return res.status(400).json({ error: 'This verification code has expired. Please request a new code.' });
    }

    // Check max attempts (limit 5)
    const attempts = Number(verification.attempts || 0);
    if (attempts >= 5) {
      await updateVerification(verification.id, { used: true });
      return res.status(400).json({ error: 'Too many incorrect attempts (5/5). Code invalidated. Please request a new code.' });
    }

    // Verify code hash
    const inputHash = hashCode(code);
    if (inputHash !== verification.code_hash) {
      const newAttempts = attempts + 1;
      await updateVerification(verification.id, { attempts: newAttempts });
      const remainingAttempts = 5 - newAttempts;
      return res.status(400).json({
        error: `Incorrect code. ${remainingAttempts} attempt${remainingAttempts === 1 ? '' : 's'} remaining.`
      });
    }

    // Mark verification used
    await updateVerification(verification.id, { used: true });

    // Update user in Firestore: is_email_verified = true
    if (userId) {
      await updateUser(userId, {
        is_email_verified: true,
        emailVerified: true,
        verificationStatus: 'Approved'
      });
    }

    const updatedUser = userId ? await findUserById(userId) : null;

    return res.json({
      message: 'Email verified successfully! KUET student privileges and rentals are now unlocked.',
      user: {
        id: userId,
        uid: userId,
        full_name: updatedUser ? (updatedUser.full_name || updatedUser.fullName) : 'Student',
        roll_no: updatedUser ? (updatedUser.roll_no || updatedUser.rollNumber) : '',
        email: updatedUser ? updatedUser.email : targetEmail,
        phone: updatedUser ? (updatedUser.phone || updatedUser.phoneNumber) : '',
        department: updatedUser ? updatedUser.department : '',
        hall: updatedUser ? (updatedUser.hall || updatedUser.hallName) : '',
        is_email_verified: true,
        is_roll_verified: true
      }
    });
  } catch (err) {
    console.error('[Verify Code Error]', err);
    return res.status(500).json({ error: 'Server error during code verification: ' + err.message });
  }
});

/**
 * GET /auth/me
 * Return current logged in user details.
 */
router.get('/me', requireAuth, async (req, res) => {
  try {
    const user = await findUserById(req.user.userId || req.user.id);
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }
    return res.json({
      user: {
        ...user,
        is_email_verified: Boolean(user.is_email_verified),
        is_roll_verified: Boolean(user.is_roll_verified)
      }
    });
  } catch (e) {
    return res.status(500).json({ error: 'Error fetching profile: ' + e.message });
  }
});

module.exports = router;

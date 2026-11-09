'use strict';

/**
 * otpRoutes.js
 * Top-level OTP endpoints: POST /send-otp and POST /verify-otp
 * Stores OTPs in Firestore collection "otps" with doc id = email.
 */

const express = require('express');
const { getDb, isFirestoreAvailable, updateUser, findUserByRollOrEmail } = require('../db/firebase');
const { generateOtpCode, sendVerificationEmail } = require('../services/emailService');

const router = express.Router();

// ─── POST /send-otp ────────────────────────────────────────────────────────────
router.post('/send-otp', async (req, res) => {
  try {
    const { email } = req.body;

    if (!email || !email.trim()) {
      return res.status(400).json({ error: 'Email is required.' });
    }

    const targetEmail = email.trim().toLowerCase();

    // Generate a 6-digit OTP
    const otpCode = generateOtpCode();
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString(); // 10 min

    // Save in Firestore "otps" collection, doc id = email
    if (isFirestoreAvailable()) {
      const db = getDb();
      await db.collection('otps').doc(targetEmail).set({
        otp: String(otpCode).trim(),
        expiresAt,
        createdAt: new Date().toISOString()
      });
      console.log(`[OTP] Saved OTP for ${targetEmail} in collection 'otps'`);
    } else {
      console.warn('[OTP] Firestore not available — OTP not persisted.');
    }

    // Send the email
    const sendResult = await sendVerificationEmail(targetEmail, otpCode, 'KUETian');

    if (!sendResult.success) {
      console.log('[OTP] Email delivery failed. Full error:', sendResult.error);
      return res.status(500).json({
        error: sendResult.error || 'Could not send OTP email. Check server SMTP configuration.'
      });
    }

    return res.json({
      message: `OTP sent to ${targetEmail}.`,
      cooldownSeconds: 60
    });
  } catch (err) {
    console.log('[OTP] /send-otp Exception. Full error:', err);
    return res.status(500).json({ error: 'Server error while sending OTP: ' + err.message });
  }
});

// ─── POST /verify-otp ──────────────────────────────────────────────────────────
router.post('/verify-otp', async (req, res) => {
  try {
    const emailRaw = req.body.email;
    const otpRaw = req.body.otp !== undefined ? req.body.otp : req.body.code;

    if (!emailRaw || !String(emailRaw).trim()) {
      return res.status(400).json({ error: 'Email is required.' });
    }
    if (otpRaw === undefined || otpRaw === null || !String(otpRaw).trim()) {
      return res.status(400).json({ error: 'OTP is required.' });
    }

    const targetEmail = String(emailRaw).trim().toLowerCase();
    const inputOtp = String(otpRaw).trim();

    if (!isFirestoreAvailable()) {
      return res.status(503).json({ error: 'Firestore is not available. Please check server configuration.' });
    }

    const db = getDb();
    const docRef = db.collection('otps').doc(targetEmail);
    const docSnap = await docRef.get();

    if (!docSnap.exists) {
      return res.status(400).json({ error: 'No active OTP found for this email. Please request a new code.' });
    }

    const data = docSnap.data();
    const storedOtp = String(data.otp || '').trim();

    // Check expiry
    if (new Date(data.expiresAt).getTime() < Date.now()) {
      await docRef.delete();
      return res.status(400).json({ error: 'OTP has expired. Please request a new code.' });
    }

    // Compare with the stored one
    if (storedOtp !== inputOtp) {
      return res.status(400).json({ error: 'Incorrect OTP. Please check the code and try again.' });
    }

    // Correct OTP: delete the OTP doc
    await docRef.delete();

    // Set emailVerified = true on that user's Firestore document
    let userRecord = null;
    try {
      const userSnap = await db.collection('users').where('email', '==', targetEmail).limit(1).get();
      if (!userSnap.empty) {
        const uDoc = userSnap.docs[0];
        await uDoc.ref.set({
          emailVerified: true,
          is_email_verified: true,
          verificationStatus: 'Approved'
        }, { merge: true });
        userRecord = { id: uDoc.id, ...uDoc.data(), is_email_verified: true, emailVerified: true };
      }
    } catch (e) {
      console.warn('[OTP] Firestore users query error:', e.message);
    }

    if (!userRecord) {
      userRecord = await findUserByRollOrEmail(targetEmail);
      if (userRecord) {
        const userId = userRecord.id || userRecord.uid;
        await updateUser(userId, {
          emailVerified: true,
          is_email_verified: true,
          verificationStatus: 'Approved'
        });
      }
    }

    return res.json({
      message: 'Email verified successfully!',
      emailVerified: true,
      user: userRecord ? {
        id: userRecord.id || userRecord.uid,
        email: userRecord.email,
        is_email_verified: true,
        emailVerified: true
      } : {
        email: targetEmail,
        is_email_verified: true,
        emailVerified: true
      }
    });
  } catch (err) {
    console.log('[OTP] /verify-otp Exception. Full error:', err);
    return res.status(500).json({ error: 'Server error while verifying OTP: ' + err.message });
  }
});

module.exports = router;

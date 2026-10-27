/**
 * emailService.js
 * Handles OTP generation, hashing, and email delivery (nodemailer / console fallback).
 *
 * Dev behaviour:
 *   - If SMTP is NOT configured → OTP is printed to console (never returned to client).
 *   - If SMTP IS configured but fails → real error is logged + client sees a clear error message.
 *   - In production (NODE_ENV=production) with no SMTP → startup warning, but code is NEVER logged.
 */

'use strict';

const nodemailer = require('nodemailer');
const crypto = require('crypto');
require('dotenv').config();

const isDev = (process.env.NODE_ENV || 'development') !== 'production';

// ─── Build transporter ────────────────────────────────────────────────────────
let transporter = null;

if (process.env.SMTP_HOST && process.env.SMTP_USER && process.env.SMTP_PASS) {
  transporter = nodemailer.createTransport({
    host: process.env.SMTP_HOST,
    port: parseInt(process.env.SMTP_PORT, 10) || 587,
    secure: process.env.SMTP_SECURE === 'true', // true for port 465, false for 587
    auth: {
      user: process.env.SMTP_USER,
      pass: process.env.SMTP_PASS,
    },
    tls: {
      // Allow self-signed certs in dev
      rejectUnauthorized: !isDev,
    },
  });
} else {
  if (!isDev) {
    console.warn('[Email Service] ⚠️  PRODUCTION WARNING: SMTP_HOST/SMTP_USER/SMTP_PASS not set.');
    console.warn('[Email Service]    Emails will NOT be delivered. Set SMTP credentials in .env.');
  } else {
    console.log('[Email Service] ℹ️  SMTP not configured — OTP codes will be printed to console (dev only).');
  }
}

// ─── Startup verification ─────────────────────────────────────────────────────
function checkMissingSmtpConfig() {
  const missing = [];
  if (!process.env.SMTP_HOST) missing.push('SMTP_HOST');
  if (!process.env.SMTP_PORT) missing.push('SMTP_PORT');
  if (!process.env.SMTP_USER) missing.push('SMTP_USER');
  if (!process.env.SMTP_PASS) missing.push('SMTP_PASS');
  return missing;
}

/**
 * Called once at server startup or during healthcheck. Verifies SMTP credentials are valid.
 */
async function verifySmtpConnection() {
  if (!transporter) {
    return false;
  }
  try {
    await transporter.verify();
    return true;
  } catch (err) {
    console.error('[Email Service] ❌  SMTP connection FAILED:', err.message);
    return false;
  }
}

// ─── OTP helpers ──────────────────────────────────────────────────────────────
/**
 * Generate a cryptographically secure 6-digit OTP.
 */
function generateOtpCode() {
  return crypto.randomInt(100000, 1000000).toString();
}

/**
 * Hash a code with SHA-256 for secure DB storage (never store plaintext).
 */
function hashCode(code) {
  return crypto.createHash('sha256').update(code.trim()).digest('hex');
}

// ─── Email sending ────────────────────────────────────────────────────────────
/**
 * Send OTP via email. Falls back to console in development when SMTP is not configured.
 *
 * @param {string} toEmail   - Recipient address
 * @param {string} code      - Plaintext 6-digit OTP (never stored)
 * @param {string} fullName  - Recipient display name
 * @returns {{ success: boolean, mode: 'smtp'|'console'|'fallback_console', error?: string }}
 */
async function sendVerificationEmail(toEmail, code, fullName = 'KUETian') {
  // If SMTP is not configured or in development, print code in console visibly
  if (!transporter && isDev) {
    console.log('');
    console.log('********************************************************************************');
    console.log(`*                                                                              *`);
    console.log(`*  VERIFICATION CODE for ${toEmail}: ${code}  *`);
    console.log(`*  (Valid for 10 minutes)                                                      *`);
    console.log(`*                                                                              *`);
    console.log('********************************************************************************');
    console.log('');
    return { success: true, mode: 'console' };
  }

  if (isDev) {
    console.log('');
    console.log('********************************************************************************');
    console.log(`*  VERIFICATION CODE for ${toEmail}: ${code}  *`);
    console.log('********************************************************************************');
    console.log('');
  }

  if (!transporter) {
    if (!isDev) {
      // Production, no SMTP: return a real error to the client
      return { success: false, error: 'Email service is not configured on the server.', mode: 'unconfigured' };
    }
    return { success: true, mode: 'console' };
  }

  const mailOptions = {
    from: process.env.MAIL_FROM || `"BorrowBox KUET" <no-reply@kuet.ac.bd>`,
    to: toEmail,
    subject: `BorrowBox: Your verification code is ${code}`,

    // ── Plain-text fallback ──────────────────────────────────────────────────
    text: [
      `Hello ${fullName},`,
      '',
      'Your BorrowBox KUET verification code is:',
      '',
      `  ${code}`,
      '',
      'This code expires in 10 minutes.',
      'If you did not request this, please ignore this email.',
      '',
      '– The BorrowBox Team',
    ].join('\n'),

    // ── Branded HTML email ───────────────────────────────────────────────────
    html: `
<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;padding:0;background:#1E1A36;font-family:Arial,Helvetica,sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background:#1E1A36;padding:32px 0;">
    <tr><td align="center">
      <table width="500" cellpadding="0" cellspacing="0"
             style="background:#312C51;border-radius:16px;overflow:hidden;max-width:95vw;">
        <!-- Header -->
        <tr>
          <td style="background:#2A2155;padding:24px 32px;text-align:center;">
            <h1 style="margin:0;font-size:26px;color:#F0C38E;letter-spacing:1px;">📦 BorrowBox</h1>
            <p  style="margin:6px 0 0;font-size:12px;color:#A5A1B8;letter-spacing:2px;text-transform:uppercase;">
              KUET Campus Rental Platform
            </p>
          </td>
        </tr>
        <!-- Body -->
        <tr>
          <td style="padding:32px;">
            <p style="margin:0 0 12px;color:#FFFFFF;font-size:16px;">Hello <strong>${fullName}</strong>,</p>
            <p style="margin:0 0 24px;color:#A5A1B8;font-size:14px;line-height:1.6;">
              Use the 6-digit code below to verify your KUET student email address on BorrowBox.
            </p>
            <!-- OTP Box -->
            <div style="background:#48426D;border-radius:12px;padding:28px;text-align:center;margin-bottom:24px;">
              <span style="font-size:40px;font-weight:bold;letter-spacing:12px;color:#F0C38E;
                           font-family:'Courier New',monospace;">${code}</span>
            </div>
            <!-- Expiry note -->
            <p style="margin:0 0 8px;color:#A5A1B8;font-size:13px;text-align:center;">
              ⏱ This code expires in <strong style="color:#F0C38E;">10 minutes</strong>.
            </p>
            <p style="margin:0;color:#8B86A4;font-size:12px;text-align:center;">
              If you did not create a BorrowBox account, you can safely ignore this email.
            </p>
          </td>
        </tr>
        <!-- Footer -->
        <tr>
          <td style="background:#2A2155;padding:16px 32px;text-align:center;">
            <p style="margin:0;color:#8B86A4;font-size:11px;">
              © BorrowBox KUET &nbsp;|&nbsp; Khulna University of Engineering &amp; Technology
            </p>
          </td>
        </tr>
      </table>
    </td></tr>
  </table>
</body>
</html>`,
  };

  try {
    const info = await transporter.sendMail(mailOptions);
    console.log(`[Email Service] ✅  Email sent to ${toEmail} — messageId: ${info.messageId}`);
    return { success: true, mode: 'smtp', messageId: info.messageId };
  } catch (err) {
    console.error(`[Email Service] ❌  Failed to send email to ${toEmail}:`, err.message);
    if (isDev) {
      // OTP already printed above — don't block development
      console.log('[Email Service]    (Dev mode: OTP was logged to console above — test is unblocked.)');
      return { success: true, mode: 'fallback_console', error: err.message };
    }
    // Production: surface the error so the caller can return 500 to the client
    return { success: false, error: `Could not send email: ${err.message}`, mode: 'smtp_error' };
  }
}

module.exports = {
  generateOtpCode,
  hashCode,
  sendVerificationEmail,
  verifySmtpConnection,
  checkMissingSmtpConfig,
};

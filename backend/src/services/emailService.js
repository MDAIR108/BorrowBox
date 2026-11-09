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

const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../../.env') });
const nodemailer = require('nodemailer');
const crypto = require('crypto');

const isDev = (process.env.NODE_ENV || 'development') !== 'production';

// ─── Dynamic Transporter Builder ──────────────────────────────────────────────
function getTransporter() {
  // ── Path 1: Gmail via EMAIL_USER + EMAIL_APP_PASSWORD (preferred) ──────────
  const gmailUser = process.env.EMAIL_USER;
  const gmailPass = process.env.EMAIL_APP_PASSWORD;
  if (gmailUser && gmailPass) {
    return nodemailer.createTransport({
      service: 'gmail',
      auth: {
        user: gmailUser,
        pass: gmailPass,
      },
    });
  }

  // ── Path 2: Generic SMTP via SMTP_HOST + SMTP_USER + SMTP_PASS ─────────────
  const host = process.env.SMTP_HOST;
  const user = process.env.SMTP_USER;
  const pass = process.env.SMTP_PASS;
  const port = parseInt(process.env.SMTP_PORT, 10) || 587;
  const secure = process.env.SMTP_SECURE === 'true' || port === 465;

  if (host && user && pass) {
    return nodemailer.createTransport({
      host,
      port,
      secure,
      auth: { user, pass },
      tls: { rejectUnauthorized: !isDev },
    });
  }

  if (process.env.SMTP_SERVICE && user && pass) {
    return nodemailer.createTransport({
      service: process.env.SMTP_SERVICE,
      auth: { user, pass },
    });
  }

  return null;
}

// ─── Startup verification ─────────────────────────────────────────────────────
function checkMissingSmtpConfig() {
  // Gmail path
  if (process.env.EMAIL_USER && process.env.EMAIL_APP_PASSWORD) return [];
  // Generic SMTP path
  const missing = [];
  if (!process.env.SMTP_HOST && !process.env.SMTP_SERVICE) missing.push('SMTP_HOST (or EMAIL_USER + EMAIL_APP_PASSWORD for Gmail)');
  if (!process.env.SMTP_USER) missing.push('SMTP_USER');
  if (!process.env.SMTP_PASS) missing.push('SMTP_PASS');
  return missing;
}

/**
 * Called once at server startup or during healthcheck. Verifies SMTP credentials are valid.
 */
async function verifySmtpConnection() {
  const transporter = getTransporter();
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
 * Send OTP via email using configured SMTP transporter so the code actually gets sent to the user's inbox.
 *
 * @param {string} toEmail   - Recipient address
 * @param {string} code      - Plaintext 6-digit OTP
 * @param {string} fullName  - Recipient display name
 * @returns {{ success: boolean, mode: 'smtp'|'console_unconfigured', error?: string, messageId?: string }}
 */
async function sendVerificationEmail(toEmail, code, fullName = 'KUETian') {
  const transporter = getTransporter();

  if (!transporter) {
    if (isDev) {
      console.log(`\n=======================================================`);
      console.log(`[Email Service] 📧 Dev Mode OTP for ${toEmail}: ${code}`);
      console.log(`=======================================================\n`);
      return { success: true, mode: 'console' };
    }
    const errorMsg = 'SMTP email service is not configured. Please set SMTP_HOST, SMTP_USER, and SMTP_PASS in backend/.env.';
    console.error(`[Email Service] ❌  Cannot send email to ${toEmail}: ${errorMsg}`);
    return { success: false, error: errorMsg, mode: 'unconfigured' };
  }

  const senderUser = process.env.EMAIL_USER || process.env.SMTP_USER || 'no-reply@kuet.ac.bd';
  const fromAddress = process.env.SMTP_FROM || process.env.MAIL_FROM || `"BorrowBox KUET" <${senderUser}>`;

  const mailOptions = {
    from: fromAddress,
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
    return { success: false, error: `Could not send email: ${err.message}`, mode: 'smtp_error' };
  }
}

module.exports = {
  generateOtpCode,
  hashCode,
  sendVerificationEmail,
  verifySmtpConnection,
  checkMissingSmtpConfig,
  getTransporter,
};

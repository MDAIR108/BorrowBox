#!/usr/bin/env node
/**
 * scripts/testMail.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Send a test email using the SMTP settings in .env.
 * Usage:  node scripts/testMail.js recipient@example.com
 *         npm run test:mail -- recipient@example.com
 */

'use strict';

const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });

const { verifySmtpConnection, sendVerificationEmail } = require('../src/services/emailService');

const recipient = process.argv[2];
if (!recipient || !recipient.includes('@')) {
  console.error('Usage: npm run test:mail -- your@email.com');
  process.exit(1);
}

(async () => {
  console.log('');
  console.log('┌─────────────────────────────────────────────┐');
  console.log('│  BorrowBox SMTP Test                        │');
  console.log('└─────────────────────────────────────────────┘');
  console.log(`Recipient : ${recipient}`);
  console.log(`SMTP Host : ${process.env.SMTP_HOST || '(not set)'}`);
  console.log(`SMTP Port : ${process.env.SMTP_PORT || '(not set)'}`);
  console.log(`SMTP User : ${process.env.SMTP_USER || '(not set)'}`);
  console.log(`SMTP Secure: ${process.env.SMTP_SECURE || 'false'}`);
  console.log('');

  const verified = await verifySmtpConnection();
  if (!verified) {
    console.error('❌  SMTP verification failed — email NOT sent. Fix credentials in .env first.');
    process.exit(1);
  }

  const testCode = '123456';
  console.log(`Sending test email with dummy code [${testCode}] to ${recipient}...`);
  const result = await sendVerificationEmail(recipient, testCode, 'Test User');

  if (result.success && result.mode === 'smtp') {
    console.log('');
    console.log('✅  Test email delivered successfully!');
    console.log(`   messageId: ${result.messageId}`);
    console.log('   Check your inbox (and Spam folder).');
  } else {
    console.error('❌  Failed to send test email:', result.error || result.mode);
    process.exit(1);
  }
})();

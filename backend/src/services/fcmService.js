'use strict';

const { getMessaging } = require('firebase-admin/messaging');
const { getDb, isFirestoreAvailable } = require('../db/firebase');

/**
 * Send FCM push notification to a user's registered devices.
 * Catches all errors safely so push failure never breaks business logic.
 */
async function sendPushToUser(userId, { title, body, data }) {
  try {
    const db = getDb();
    if (!db) {
      console.warn('[FCM] Firestore unavailable, skipping push notification for user:', userId);
      return;
    }

    if (!userId) return;

    let userDoc = await db.collection('users').doc(String(userId)).get();
    let userDocRef = db.collection('users').doc(String(userId));

    if (!userDoc.exists) {
      const qSnap = await db.collection('users').where('id', '==', String(userId)).limit(1).get();
      if (!qSnap.empty) {
        userDoc = qSnap.docs[0];
        userDocRef = userDoc.ref;
      }
    }

    if (!userDoc.exists) {
      console.warn('[FCM] User document not found for push notification:', userId);
      return;
    }

    const userData = userDoc.data() || {};
    const tokens = Array.isArray(userData.fcmTokens) ? userData.fcmTokens.filter(Boolean) : [];

    if (tokens.length === 0) {
      console.warn(`[FCM] Warning: User ${userId} has 0 registered FCM tokens. Push notification skipped.`);
      return { success: false, count: 0, reason: 'no_tokens' };
    }

    const messaging = getMessaging();
    const dataPayload = Object.fromEntries(
      Object.entries({
        title: title || 'BorrowBox Notification',
        body: body || '',
        ...(data || {})
      }).map(([k, v]) => [k, String(v != null ? v : '')])
    );

    const payload = {
      tokens,
      notification: {
        title: title || 'BorrowBox Notification',
        body: body || ''
      },
      data: dataPayload,
      android: {
        priority: 'high',
        notification: {
          channelId: 'rent_requests_v3',
          sound: 'default',
          defaultSound: true,
          defaultVibrateTimings: true,
          color: '#F0C38E',
          visibility: 'public'
        }
      },
      apns: {
        payload: {
          aps: {
            sound: 'default'
          }
        }
      }
    };

    const response = await messaging.sendEachForMulticast(payload);
    console.log(`[FCM] Sent push to ${tokens.length} token(s) for user ${userId}. Success: ${response.successCount}, Failures: ${response.failureCount}`);

    // Remove invalid/unregistered tokens if FCM reports them
    if (response.failureCount > 0) {
      const invalidTokens = [];
      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          const errCode = resp.error ? (resp.error.code || '') : '';
          const errMsg = resp.error ? (resp.error.message || '') : '';
          if (
            errCode === 'messaging/invalid-registration-token' ||
            errCode === 'messaging/registration-token-not-registered' ||
            errCode === 'messaging/invalid-argument' ||
            errCode.includes('registration-token-not-registered') ||
            errCode.includes('invalid-argument') ||
            errMsg.includes('not a valid FCM registration token')
          ) {
            invalidTokens.push(tokens[idx]);
          }
        }
      });

      if (invalidTokens.length > 0) {
        console.log(`[FCM] Removing ${invalidTokens.length} unregistered/invalid token(s) for user ${userId}`);
        const { FieldValue } = require('firebase-admin/firestore');
        await userDocRef.update({
          fcmTokens: FieldValue.arrayRemove(...invalidTokens)
        }).catch(e => console.warn('[FCM] Token cleanup warning:', e.message));
      }
    }

    return {
      success: response.successCount > 0,
      sentCount: response.successCount,
      failureCount: response.failureCount,
      totalTokens: tokens.length
    };
  } catch (err) {
    console.error('[FCM] Push notification error (non-fatal):', err.message);
    return { success: false, error: err.message };
  }
}

module.exports = {
  sendPushToUser
};

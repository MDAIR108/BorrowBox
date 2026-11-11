'use strict';

const express = require('express');
const { getDb, isFirestoreAvailable } = require('../db/firebase');
const { requireAuth } = require('../middleware/authMiddleware');

const router = express.Router();

/**
 * POST /users/fcm-token
 * Register an FCM push notification token for the logged-in user.
 * Stored in user's Firestore document as `fcmTokens` array (no duplicates).
 */
router.post('/fcm-token', requireAuth, async (req, res) => {
  try {
    const fcmToken = (req.body.token || req.body.fcmToken || req.body.fcm_token || '').trim();
    if (!fcmToken) {
      return res.status(400).json({ error: 'FCM token is required.' });
    }

    const db = getDb();
    if (!db) {
      return res.status(503).json({ error: 'Database unavailable.' });
    }

    const { FieldValue } = require('firebase-admin/firestore');
    const userRef = db.collection('users').doc(String(req.user.id));

    await userRef.set({
      fcmTokens: FieldValue.arrayUnion(fcmToken)
    }, { merge: true });

    console.log(`[User Routes] FCM token registered for user ${req.user.id}: ${fcmToken.substring(0, 15)}...`);
    return res.json({ message: 'FCM token saved successfully.' });
  } catch (err) {
    console.error('[User Routes] Error saving FCM token:', err);
    return res.status(500).json({ error: 'Failed to save FCM token: ' + err.message });
  }
});

/**
 * POST /users/test-push
 * Send a test push notification to the logged-in user's own registered devices.
 */
router.post('/test-push', requireAuth, async (req, res) => {
  try {
    const db = getDb();
    if (!db) return res.status(503).json({ error: 'Database unavailable.' });

    const userId = String(req.user.id);
    let userDoc = await db.collection('users').doc(userId).get();
    if (!userDoc.exists) {
      const q = await db.collection('users').where('id', '==', userId).limit(1).get();
      if (!q.empty) userDoc = q.docs[0];
    }

    const userData = userDoc.exists ? (userDoc.data() || {}) : {};
    const tokens = Array.isArray(userData.fcmTokens) ? userData.fcmTokens.filter(Boolean) : [];

    if (tokens.length === 0) {
      return res.json({
        ok: false,
        count: 0,
        message: 'No device token saved, please log in again'
      });
    }

    const { sendPushToUser } = require('../services/fcmService');
    const result = await sendPushToUser(userId, {
      title: 'Test Notification',
      body: 'This is a test notification from BorrowBox. System banner and sound are working!',
      data: {
        type: 'test_notification',
        title: 'Test Notification',
        body: 'This is a test notification from BorrowBox. System banner and sound are working!'
      }
    });

    const sentCount = result && result.sentCount !== undefined ? result.sentCount : tokens.length;
    return res.json({
      ok: true,
      count: sentCount,
      message: `Sent to ${sentCount} device(s)`
    });
  } catch (err) {
    console.error('[User Routes] Error sending test push:', err);
    return res.status(500).json({ error: 'Failed to send test push: ' + err.message });
  }
});

/**
 * DELETE /users/fcm-token or POST /users/fcm-token/remove
 * Unregister an FCM push notification token on user logout.
 */
const removeTokenHandler = async (req, res) => {
  try {
    const fcmToken = (req.body.token || req.body.fcmToken || req.body.fcm_token || req.query.token || '').trim();
    if (!fcmToken) {
      return res.status(400).json({ error: 'FCM token is required to remove.' });
    }

    const db = getDb();
    if (!db) {
      return res.status(503).json({ error: 'Database unavailable.' });
    }

    const { FieldValue } = require('firebase-admin/firestore');
    const userRef = db.collection('users').doc(String(req.user.id));

    await userRef.update({
      fcmTokens: FieldValue.arrayRemove(fcmToken)
    }).catch(err => {
      console.warn('[User Routes] Token remove notice:', err.message);
    });

    return res.json({ message: 'FCM token removed.' });
  } catch (err) {
    console.error('[User Routes] Error removing FCM token:', err);
    return res.status(500).json({ error: 'Failed to remove FCM token: ' + err.message });
  }
};

router.delete('/fcm-token', requireAuth, removeTokenHandler);
router.post('/fcm-token/remove', requireAuth, removeTokenHandler);

/**
 * POST /users/profile-photo
 * Save/update the user's profile photo in Firestore.
 */
router.post('/profile-photo', requireAuth, async (req, res) => {
  try {
    const photo = (req.body.photo || req.body.profilePhoto || req.body.photoUrl || req.body.image || '').trim();
    if (!photo) {
      return res.status(400).json({ error: 'Profile photo data is required.' });
    }

    const db = getDb();
    if (!db) {
      return res.status(503).json({ error: 'Database unavailable.' });
    }

    const userId = String(req.user.id || req.user.userId);
    const userRef = db.collection('users').doc(userId);

    await userRef.set({
      profilePhoto: photo,
      photoUrl: photo,
      updatedAt: new Date().toISOString()
    }, { merge: true });

    return res.json({
      message: 'Profile photo updated successfully!',
      profilePhoto: photo,
      photoUrl: photo
    });
  } catch (err) {
    console.error('[User Routes] Error saving profile photo:', err);
    return res.status(500).json({ error: 'Failed to save profile photo: ' + err.message });
  }
});

/**
 * PUT /users/profile
 * Update user profile details including Hall Room No.
 */
router.put('/profile', requireAuth, async (req, res) => {
  try {
    const db = getDb();
    if (!db) return res.status(503).json({ error: 'Database unavailable.' });

    const userId = String(req.user.id);
    const updates = {};
    if (req.body.hallRoomNo !== undefined) updates.hallRoomNo = String(req.body.hallRoomNo).trim();
    if (req.body.hall_room_no !== undefined) updates.hallRoomNo = String(req.body.hall_room_no).trim();
    if (req.body.hall !== undefined) updates.hall = String(req.body.hall).trim();
    if (req.body.presentAddress !== undefined) updates.presentAddress = String(req.body.presentAddress).trim();
    if (req.body.phone !== undefined) updates.phone = String(req.body.phone).trim();
    if (req.body.phoneNumber !== undefined) updates.phoneNumber = String(req.body.phoneNumber).trim();
    if (req.body.whatsappNumber !== undefined) updates.whatsappNumber = String(req.body.whatsappNumber).trim();
    if (req.body.fullName !== undefined) updates.fullName = String(req.body.fullName).trim();
    updates.updatedAt = new Date().toISOString();

    await db.collection('users').doc(userId).set(updates, { merge: true });

    return res.json({
      message: 'Profile updated successfully.',
      updates
    });
  } catch (err) {
    console.error('[User Routes] Profile update error:', err);
    return res.status(500).json({ error: 'Failed to update profile: ' + err.message });
  }
});

/**
 * DELETE /users/me
 * Permanently deletes the user's account and all associated data.
 */
router.delete('/me', requireAuth, async (req, res) => {
  try {
    const db = getDb();
    if (!db) return res.status(503).json({ error: 'Database unavailable.' });

    const userId = String(req.user.id);
    const { sendPushToUser } = require('../services/fcmService');

    // 1. Fetch user data before deletion
    let userDoc = await db.collection('users').doc(userId).get();
    if (!userDoc.exists) {
      const q = await db.collection('users').where('id', '==', userId).limit(1).get();
      if (!q.empty) userDoc = q.docs[0];
    }
    const userData = userDoc.exists ? (userDoc.data() || {}) : {};
    const userEmail = req.user.email || userData.email || '';
    const userRoll = req.user.roll_no || userData.rollNumber || userData.roll_no || '';

    console.log(`[User Routes] Initiating account deletion for user ${userId} (${userEmail}, roll: ${userRoll})`);

    // 2. Delete user's products and cancel active/pending rentals on them
    try {
      const productsSnap = await db.collection('products').where('ownerId', '==', userId).get();
      for (const prodDoc of productsSnap.docs) {
        const prodId = prodDoc.id;
        const rentalsSnap = await db.collection('rentals').where('productId', '==', prodId).get();
        for (const rDoc of rentalsSnap.docs) {
          const rData = rDoc.data();
          if (rData.status === 'pending') {
            await rDoc.ref.update({ status: 'cancelled' }).catch(() => {});
            if (rData.renterId) {
              await db.collection('notifications').add({
                userId: rData.renterId,
                type: 'rental_cancelled',
                title: 'Rental cancelled',
                message: `The product "${rData.productName || 'item'}" was removed by the owner.`,
                rentalId: rDoc.id,
                read: false,
                createdAt: new Date().toISOString()
              }).catch(() => {});
              sendPushToUser(rData.renterId, {
                title: 'Rental cancelled',
                body: `The product "${rData.productName || 'item'}" was removed by the owner.`,
                data: { type: 'rental_cancelled', rentalId: rDoc.id }
              }).catch(() => {});
            }
          }
        }
        await prodDoc.ref.delete().catch(e => console.warn(`[Delete Account] Product ${prodId} delete notice:`, e.message));
      }
    } catch (prodErr) {
      console.warn('[Delete Account] Products deletion warning:', prodErr.message);
    }

    // 3. Cancel/delete rentals where user is renter
    try {
      const renterSnap = await db.collection('rentals').where('renterId', '==', userId).get();
      for (const rDoc of renterSnap.docs) {
        const rData = rDoc.data();
        if (rData.status === 'pending' && rData.ownerId) {
          await db.collection('notifications').add({
            userId: rData.ownerId,
            type: 'rental_cancelled',
            title: 'Rental request cancelled',
            message: `${rData.renterName || 'The renter'} has cancelled their request.`,
            rentalId: rDoc.id,
            read: false,
            createdAt: new Date().toISOString()
          }).catch(() => {});
          sendPushToUser(rData.ownerId, {
            title: 'Rental request cancelled',
            body: `${rData.renterName || 'The renter'} has cancelled their request.`,
            data: { type: 'rental_cancelled', rentalId: rDoc.id }
          }).catch(() => {});
        }
        await rDoc.ref.delete().catch(() => {});
      }
    } catch (rentErr) {
      console.warn('[Delete Account] Rentals deletion warning:', rentErr.message);
    }

    // 4. Delete user's notifications
    try {
      const notifsSnap = await db.collection('notifications').where('userId', '==', userId).get();
      for (const nDoc of notifsSnap.docs) {
        await nDoc.ref.delete().catch(() => {});
      }
    } catch (nErr) {
      console.warn('[Delete Account] Notifications deletion warning:', nErr.message);
    }

    // 5. Delete OTP documents
    try {
      if (userEmail) {
        const oSnap = await db.collection('otps').where('email', '==', userEmail.toLowerCase()).get();
        for (const oDoc of oSnap.docs) await oDoc.ref.delete().catch(() => {});
      }
      if (userRoll) {
        const rSnap = await db.collection('otps').where('roll', '==', userRoll).get();
        for (const oDoc of rSnap.docs) await oDoc.ref.delete().catch(() => {});
      }
    } catch (otpErr) {
      console.warn('[Delete Account] OTP deletion warning:', otpErr.message);
    }

    // 6. Delete user's Firestore document
    await db.collection('users').doc(userId).delete().catch(e => console.warn('[Delete Account] User doc delete warning:', e.message));

    // 7. Delete from Firebase Authentication
    try {
      const { getAuth } = require('firebase-admin/auth');
      const auth = getAuth();
      if (userEmail) {
        try {
          const fbUser = await auth.getUserByEmail(userEmail);
          if (fbUser) await auth.deleteUser(fbUser.uid);
        } catch (_) {}
      }
      try {
        await auth.deleteUser(userId);
      } catch (_) {}
    } catch (authErr) {
      console.warn('[Delete Account] Firebase Auth deletion notice:', authErr.message);
    }

    // 8. Delete presence & unread from Realtime Database
    try {
      const { getRtdb } = require('../db/firebase');
      const rtdb = getRtdb();
      if (rtdb) {
        await rtdb.ref('presence').child(userId).remove().catch(() => {});
        await rtdb.ref('unread').child(userId).remove().catch(() => {});
      }
    } catch (rtdbErr) {
      console.warn('[Delete Account] RTDB cleanup notice:', rtdbErr.message);
    }

    console.log(`[User Routes] Account for user ${userId} deleted successfully.`);
    return res.json({
      message: 'Your account has been deleted successfully.'
    });
  } catch (err) {
    console.error('[User Routes] Delete account error:', err);
    return res.status(500).json({ error: 'Failed to delete account: ' + err.message });
  }
});

module.exports = router;

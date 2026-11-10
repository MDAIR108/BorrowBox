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

    return res.json({ message: 'FCM token saved successfully.' });
  } catch (err) {
    console.error('[User Routes] Error saving FCM token:', err);
    return res.status(500).json({ error: 'Failed to save FCM token: ' + err.message });
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

module.exports = router;

'use strict';

const express = require('express');
const { getDb, isFirestoreAvailable, resetUnreadCounter } = require('../db/firebase');
const { requireAuth } = require('../middleware/authMiddleware');

const router = express.Router();

// GET /notifications — user's notifications, newest first
router.get('/', requireAuth, async (req, res) => {
  try {
    const db = getDb();
    if (!db) return res.status(503).json({ error: 'Database unavailable.' });

    const snap = await db.collection('notifications')
      .where('userId', '==', req.user.id)
      .get();

    const notifications = [];
    snap.forEach(doc => {
      notifications.push({ id: doc.id, ...doc.data() });
    });

    // Sort newest first
    notifications.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));

    // Reset live Realtime Database counter when viewing notifications
    resetUnreadCounter(req.user.id);

    return res.json({ notifications });
  } catch (err) {
    console.error('[Notifications GET]', err);
    return res.status(500).json({ error: err.message });
  }
});

// GET /notifications/unread-count
router.get('/unread-count', requireAuth, async (req, res) => {
  try {
    const db = getDb();
    if (!db) return res.json({ count: 0 });

    const snap = await db.collection('notifications')
      .where('userId', '==', req.user.id)
      .where('read', '==', false)
      .get();

    return res.json({ count: snap.size });
  } catch (err) {
    console.error('[Notifications unread-count]', err);
    return res.json({ count: 0 });
  }
});

// POST /notifications/:id/read
router.post('/:id/read', requireAuth, async (req, res) => {
  try {
    const db = getDb();
    if (!db) return res.status(503).json({ error: 'Database unavailable.' });

    await db.collection('notifications').doc(req.params.id).update({ read: true });
    return res.json({ message: 'Marked as read.' });
  } catch (err) {
    console.error('[Notifications mark-read]', err);
    return res.status(500).json({ error: err.message });
  }
});

module.exports = router;

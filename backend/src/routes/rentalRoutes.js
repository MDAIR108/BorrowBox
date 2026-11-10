'use strict';

const express = require('express');
const { getDb, isFirestoreAvailable } = require('../db/firebase');
const { requireAuth } = require('../middleware/authMiddleware');
const { sendPushToUser } = require('../services/fcmService');

const router = express.Router();

/**
 * Safely parse date value to millisecond timestamp for sorting
 */
function parseDateToMs(dateVal) {
  if (!dateVal) return 0;
  if (typeof dateVal === 'number') return dateVal;
  if (typeof dateVal === 'object') {
    if (typeof dateVal.toMillis === 'function') return dateVal.toMillis();
    if (typeof dateVal.toDate === 'function') return dateVal.toDate().getTime();
  }
  const ms = new Date(dateVal).getTime();
  return isNaN(ms) ? 0 : ms;
}

/**
 * Enriches a rental object with all required fields, falling back to product document data
 */
function buildEnrichedRental(id, rentalData, product = {}) {
  const days = parseInt(rentalData.days, 10) || 1;

  let rentPerDay = rentalData.rentPerDay !== undefined && rentalData.rentPerDay !== null
    ? parseFloat(rentalData.rentPerDay)
    : (rentalData.totalPrice !== undefined && rentalData.totalPrice !== null && days > 0
        ? parseFloat((parseFloat(rentalData.totalPrice) / days).toFixed(2))
        : (product.rentPerDay !== undefined && product.rentPerDay !== null
            ? parseFloat(product.rentPerDay)
            : (parseFloat(product.price_per_day || product.pricePerDay) || 0)));
  if (isNaN(rentPerDay)) rentPerDay = 0;

  let totalPrice = rentalData.totalPrice !== undefined && rentalData.totalPrice !== null
    ? parseFloat(rentalData.totalPrice)
    : parseFloat((rentPerDay * days).toFixed(2));
  if (isNaN(totalPrice)) totalPrice = 0;

  const productImage = rentalData.productImage
    || rentalData.imageUrl
    || rentalData.image_url
    || rentalData.photoUrl
    || product.imageUrl
    || product.image_url
    || product.photoUrl
    || product.productImage
    || product.image
    || '';

  let features = rentalData.features;
  if (!features && product.features) {
    features = product.features;
  }
  if (!Array.isArray(features)) {
    if (typeof features === 'string' && features.trim().length > 0) {
      features = features.split(',').map(s => s.trim());
    } else {
      features = [];
    }
  }

  const rawStatus = (rentalData.status || 'pending').toString().trim();

  return {
    id: id,
    productId: rentalData.productId || rentalData.product_id || product.id || '',
    productName: rentalData.productName || product.name || product.title || 'Product',
    productImage: productImage,
    imageUrl: productImage,
    image_url: productImage,
    photoUrl: productImage,
    rentPerDay: rentPerDay,
    days: days,
    totalPrice: totalPrice,
    status: rawStatus,
    createdAt: rentalData.createdAt || new Date().toISOString(),
    ownerName: rentalData.ownerName || product.ownerName || product.owner_name || 'BorrowBox Member',
    renterName: rentalData.renterName || 'User',
    ownerId: rentalData.ownerId || product.ownerId || product.owner_id || '',
    renterId: rentalData.renterId || '',
    model: rentalData.model || product.model || '',
    description: rentalData.description || product.description || '',
    features: features,
    category: rentalData.category || product.category || 'General',
    condition: rentalData.condition || product.condition || 'Good'
  };
}

/**
 * POST /rentals
 * Submit a rental request using Firestore.
 */
router.post('/', requireAuth, async (req, res) => {
  try {
    const db = getDb();
    if (!db) return res.status(503).json({ error: 'Database unavailable.' });

    // Accept both productId and product_id for compat
    const productId = req.body.productId || req.body.product_id;
    if (!productId) {
      return res.status(400).json({ error: 'Product ID is required.' });
    }

    // Resolve days: direct or computed from start_date/end_date
    let days = req.body.days !== undefined ? parseInt(req.body.days, 10) : undefined;
    if ((days === undefined || isNaN(days)) && req.body.start_date && req.body.end_date) {
      const startMs = new Date(req.body.start_date).getTime();
      const endMs = new Date(req.body.end_date).getTime();
      if (!isNaN(startMs) && !isNaN(endMs) && endMs > startMs) {
        days = Math.max(1, Math.ceil((endMs - startMs) / (1000 * 60 * 60 * 24)));
      }
    }

    if (!days || isNaN(days)) {
      return res.status(400).json({ error: 'Valid number of days is required.' });
    }

    // Fetch product from Firestore
    const productDoc = await db.collection('products').doc(productId).get();
    if (!productDoc.exists) {
      return res.status(404).json({ error: 'Product not found.' });
    }

    const product = productDoc.data();

    // Self-rent prevention
    if ((product.ownerId || product.owner_id) === req.user.id) {
      return res.status(400).json({ error: 'You cannot rent your own item.' });
    }

    // Availability check
    if (product.available === false) {
      return res.status(400).json({ error: 'This item is currently unavailable.' });
    }

    // Days range validation
    const maxDays = parseInt(product.maxRentDays, 10) || 7;
    if (days < 1 || days > maxDays) {
      return res.status(400).json({ error: `Days must be between 1 and ${maxDays}.` });
    }

    // Pricing
    const rentPerDay = parseFloat(product.rentPerDay || product.pricePerDay || product.price_per_day) || 0;
    const totalPrice = rentPerDay * days;

    const productName = product.name || product.title || 'Product';
    const ownerId = product.ownerId || product.owner_id;
    const ownerName = product.ownerName || product.owner_name || 'BorrowBox Member';
    const renterName = req.user.full_name || req.user.fullName || 'User';
    const createdAt = new Date().toISOString();
    const productImage = product.imageUrl || product.image_url || product.photoUrl || product.productImage || product.image || '';

    // Save rental to Firestore
    const rentalData = {
      productId,
      productName,
      productImage,
      rentPerDay,
      renterId: req.user.id,
      renterName,
      ownerId,
      ownerName,
      days,
      totalPrice,
      status: 'pending',
      model: product.model || '',
      description: product.description || '',
      features: Array.isArray(product.features) ? product.features : [],
      category: product.category || 'General',
      condition: product.condition || 'Good',
      createdAt
    };

    const rentalRef = await db.collection('rentals').add(rentalData);
    const rentalId = rentalRef.id;

    // Save notification for owner
    await db.collection('notifications').add({
      userId: ownerId,
      type: 'rent_request',
      title: 'New rental request',
      message: `${renterName} wants to rent ${productName} for ${days} day(s)`,
      rentalId,
      productId,
      read: false,
      createdAt
    });

    // Send push notification to owner via FCM (never breaks rental request on error)
    sendPushToUser(ownerId, {
      title: 'New rental request',
      body: `${renterName} wants to rent ${productName} for ${days} days`,
      data: {
        type: 'rent_request',
        rentalId: String(rentalId),
        productId: String(productId)
      }
    });

    const enriched = buildEnrichedRental(rentalId, rentalData, product);

    return res.status(201).json({
      message: 'Rental request submitted successfully!',
      rental: enriched,
      ...enriched
    });
  } catch (err) {
    if (!isFirestoreAvailable()) {
      return res.status(503).json({ error: 'Database unavailable.' });
    }
    console.error('[Create Rental Error]', err);
    return res.status(500).json({ error: 'Failed to create rental request: ' + err.message });
  }
});

/**
 * GET /rentals/mine
 * Returns every rental from the Firestore "rentals" collection where renterId equals the logged-in user,
 * newest first, regardless of status (pending, accepted, rejected, cancelled).
 */
router.get('/mine', requireAuth, async (req, res) => {
  try {
    const db = getDb();
    if (!db) return res.status(503).json({ error: 'Database unavailable.' });

    const userId = req.user.id;

    const [mySnap, incomingSnap] = await Promise.all([
      db.collection('rentals').where('renterId', '==', userId).get(),
      db.collection('rentals').where('ownerId', '==', userId).get()
    ]);

    const rawMyRentals = [];
    mySnap.forEach(doc => rawMyRentals.push({ id: doc.id, ...doc.data() }));

    const rawIncoming = [];
    incomingSnap.forEach(doc => rawIncoming.push({ id: doc.id, ...doc.data() }));

    // Gather unique product IDs to fetch product docs
    const allProductIds = new Set();
    rawMyRentals.forEach(r => {
      const pid = r.productId || r.product_id;
      if (pid) allProductIds.add(pid);
    });
    rawIncoming.forEach(r => {
      const pid = r.productId || r.product_id;
      if (pid) allProductIds.add(pid);
    });

    const productMap = new Map();
    await Promise.all(
      Array.from(allProductIds).map(async (pid) => {
        try {
          const pDoc = await db.collection('products').doc(pid).get();
          if (pDoc.exists) {
            productMap.set(pid, { id: pDoc.id, ...pDoc.data() });
          }
        } catch (e) {
          console.warn(`[rentals/mine] Failed to fetch product ${pid}:`, e.message);
        }
      })
    );

    const myRentals = rawMyRentals.map(r => {
      const pid = r.productId || r.product_id;
      return buildEnrichedRental(r.id, r, (pid ? productMap.get(pid) : null) || {});
    });
    // Sort in code: newest first, regardless of status
    myRentals.sort((a, b) => parseDateToMs(b.createdAt) - parseDateToMs(a.createdAt));

    const incomingRequests = rawIncoming.map(r => {
      const pid = r.productId || r.product_id;
      return buildEnrichedRental(r.id, r, (pid ? productMap.get(pid) : null) || {});
    });
    incomingRequests.sort((a, b) => parseDateToMs(b.createdAt) - parseDateToMs(a.createdAt));

    return res.json({
      rentals: myRentals,
      myRentals: myRentals,
      incomingRequests: incomingRequests
    });
  } catch (err) {
    console.error('[Get My Rentals Error]', err);
    return res.status(500).json({ error: 'Failed to retrieve rentals: ' + err.message });
  }
});

/**
 * GET /rentals/:id
 * Return one rental with full details. Only the renter or the product owner may read it, otherwise return 403.
 */
router.get('/:id', requireAuth, async (req, res) => {
  try {
    const db = getDb();
    if (!db) return res.status(503).json({ error: 'Database unavailable.' });

    const doc = await db.collection('rentals').doc(req.params.id).get();
    if (!doc.exists) {
      return res.status(404).json({ error: 'Rental not found.' });
    }

    const rentalData = doc.data();
    const userId = req.user.id;

    // Check authorization: Only the renter or the product owner may read it, otherwise return 403
    if (rentalData.renterId !== userId && rentalData.ownerId !== userId) {
      return res.status(403).json({ error: 'Access denied: You are not authorized to view this rental.' });
    }

    let product = {};
    const pid = rentalData.productId || rentalData.product_id;
    if (pid) {
      try {
        const prodDoc = await db.collection('products').doc(pid).get();
        if (prodDoc.exists) {
          product = { id: prodDoc.id, ...prodDoc.data() };
        }
      } catch (pErr) {
        console.warn(`[getRental/:id] Failed to fetch product ${pid}:`, pErr.message);
      }
    }

    const enriched = buildEnrichedRental(doc.id, rentalData, product);

    // If caller is the product owner, attach requester info from the renter's user document
    if (rentalData.ownerId === userId) {
      let requester = null;
      if (rentalData.renterId) {
        try {
          const renterDoc = await db.collection('users').doc(String(rentalData.renterId)).get();
          if (renterDoc.exists) {
            const u = renterDoc.data() || {};
            requester = {
              id: renterDoc.id,
              fullName: u.fullName || u.full_name || rentalData.renterName || 'KUETian',
              rollNumber: u.rollNumber || u.roll_no || '',
              department: u.department || '',
              hall: u.hall || u.hallName || u.presentAddress || '',
              phone: u.phone || u.phoneNumber || '',
              email: u.email || '',
              profilePhoto: u.profilePhoto || u.photoUrl || u.imageUrl || ''
            };
          }
        } catch (rErr) {
          console.warn(`[getRental/:id] Failed to fetch requester doc ${rentalData.renterId}:`, rErr.message);
        }
      }

      if (!requester) {
        requester = {
          id: rentalData.renterId || '',
          fullName: rentalData.renterName || 'KUETian',
          rollNumber: '',
          department: '',
          hall: '',
          phone: '',
          email: '',
          profilePhoto: ''
        };
      }

      enriched.requester = requester;
    }

    return res.json({
      rental: enriched,
      ...enriched
    });
  } catch (err) {
    console.error('[Get Rental Error]', err);
    return res.status(500).json({ error: 'Failed to retrieve rental: ' + err.message });
  }
});

/**
 * POST /rentals/:id/accept
 * Owner accepts a rental request.
 */
router.post('/:id/accept', requireAuth, async (req, res) => {
  try {
    const db = getDb();
    if (!db) return res.status(503).json({ error: 'Database unavailable.' });

    const doc = await db.collection('rentals').doc(req.params.id).get();
    if (!doc.exists) return res.status(404).json({ error: 'Rental not found.' });

    const rental = doc.data();
    if (rental.ownerId !== req.user.id) {
      return res.status(403).json({ error: 'Not authorized.' });
    }

    await db.collection('rentals').doc(req.params.id).update({ status: 'accepted' });

    await db.collection('notifications').add({
      userId: rental.renterId,
      type: 'rental_accepted',
      title: 'Rental request accepted!',
      message: `Your request to rent ${rental.productName} has been accepted.`,
      rentalId: req.params.id,
      read: false,
      createdAt: new Date().toISOString()
    });

    // Send push notification to renter via FCM
    sendPushToUser(rental.renterId, {
      title: 'Rental request accepted!',
      body: `Your request for ${rental.productName} was accepted`,
      data: {
        type: 'rental_accepted',
        rentalId: String(req.params.id),
        productId: String(rental.productId || '')
      }
    });

    return res.json({ message: 'Rental accepted.' });
  } catch (err) {
    console.error('[Accept Rental Error]', err);
    return res.status(500).json({ error: 'Failed to accept rental: ' + err.message });
  }
});

/**
 * POST /rentals/:id/reject
 * Owner rejects a rental request.
 */
router.post('/:id/reject', requireAuth, async (req, res) => {
  try {
    const db = getDb();
    if (!db) return res.status(503).json({ error: 'Database unavailable.' });

    const doc = await db.collection('rentals').doc(req.params.id).get();
    if (!doc.exists) return res.status(404).json({ error: 'Rental not found.' });

    const rental = doc.data();
    if (rental.ownerId !== req.user.id) {
      return res.status(403).json({ error: 'Not authorized.' });
    }

    await db.collection('rentals').doc(req.params.id).update({ status: 'rejected' });

    await db.collection('notifications').add({
      userId: rental.renterId,
      type: 'rental_rejected',
      title: 'Rental request declined',
      message: `Your request to rent ${rental.productName} was declined.`,
      rentalId: req.params.id,
      read: false,
      createdAt: new Date().toISOString()
    });

    // Send push notification to renter via FCM
    sendPushToUser(rental.renterId, {
      title: 'Rental request declined',
      body: `Your request for ${rental.productName} was declined`,
      data: {
        type: 'rental_rejected',
        rentalId: String(req.params.id),
        productId: String(rental.productId || '')
      }
    });

    return res.json({ message: 'Rental rejected.' });
  } catch (err) {
    console.error('[Reject Rental Error]', err);
    return res.status(500).json({ error: 'Failed to reject rental: ' + err.message });
  }
});

module.exports = router;

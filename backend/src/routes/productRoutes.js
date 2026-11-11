'use strict';

const express = require('express');
const { getDb, isFirestoreAvailable, getProducts, getProductById } = require('../db/firebase');
const { requireAuth, optionalAuth } = require('../middleware/authMiddleware');

const router = express.Router();

function getCategoryEmoji(category) {
  const map = {
    'Electronics': '💻',
    'Books': '📚',
    'Tools': '📐',
    'Sports': '⚽',
    'Others': '📦'
  };
  return map[category] || '📦';
}

/**
 * GET /products
 * Return all available products.
 * Queries Firestore "products" collection (with memory fallback).
 */
router.get('/', optionalAuth, async (req, res) => {
  const { search, q, category } = req.query;
  const searchTerm = (search || q || '').trim().toLowerCase();

  try {
    let products = [];

    // 1. Fetch from Firestore "products" collection
    if (isFirestoreAvailable() && getDb()) {
      try {
        const db = getDb();
        const snap = await db.collection('products').get();
        snap.forEach(doc => {
          const d = doc.data();
          const isAvail = d.available !== false && d.status !== 'rented' && !d.deleted;
          if (isAvail) {
            const rentVal = d.rentPerDay !== undefined
              ? parseFloat(d.rentPerDay)
              : (parseFloat(d.price_per_day || d.pricePerDay) || 0.0);

            products.push({
              id: doc.id,
              name: d.name || d.title || 'Product',
              title: d.name || d.title || 'Product',
              rentPerDay: rentVal,
              pricePerDay: rentVal,
              price_per_day: rentVal,
              maxRentDays: parseInt(d.maxRentDays, 10) || 7,
              model: d.model || '',
              description: d.description || '',
              features: Array.isArray(d.features) ? d.features : [],
              category: d.category || 'Others',
              condition: d.condition || 'Good',
              ownerId: String(d.ownerId || d.owner_id || ''),
              ownerName: d.ownerName || d.owner_name || 'BorrowBox Member',
              createdAt: d.createdAt || d.created_at || new Date().toISOString(),
              available: true,
              isAvailable: true,
              status: 'available',
              imageUrl: d.imageUrl || d.image_url || d.photoUrl || d.productImage || d.image || null,
              image_url: d.imageUrl || d.image_url || d.photoUrl || d.productImage || d.image || null,
              photoUrl: d.imageUrl || d.image_url || d.photoUrl || d.productImage || d.image || null,
              emoji: d.emoji || getCategoryEmoji(d.category),
              department: d.department || 'KUET',
              hallLocation: d.hallLocation || d.hall || 'Campus',
              whatsappNumber: d.whatsappNumber || '',
              isFree: rentVal === 0
            });
          }
        });
      } catch (fbReadErr) {
        console.warn('[Products] Firestore read error:', fbReadErr.message);
      }
    }

    // 2. Only append starter sample items if Firestore is NOT available
    if (!isFirestoreAvailable() || !getDb()) {
      try {
        const fallbackItems = await getProducts(searchTerm, category);
        const seenIds = new Set(products.map(p => p.id));
        for (const item of fallbackItems) {
          if (!seenIds.has(item.id.toString())) {
            const rentVal = parseFloat(item.price_per_day) || 0.0;
            products.push({
              id: item.id.toString(),
              name: item.title,
              title: item.title,
              rentPerDay: rentVal,
              pricePerDay: rentVal,
              price_per_day: rentVal,
              maxRentDays: 7,
              model: '',
              description: item.description,
              features: [],
              category: item.category || 'Others',
              condition: 'Good',
              isFree: rentVal === 0,
              isAvailable: item.status === 'available',
              available: item.status === 'available',
              status: item.status || 'available',
              imageUrl: item.image_url,
              emoji: item.emoji || getCategoryEmoji(item.category),
              department: item.department,
              hallLocation: item.hall,
              whatsappNumber: item.whatsappNumber || '',
              ownerId: (item.owner_id || '').toString(),
              ownerName: item.owner_name || 'BorrowBox Member',
              createdAt: item.created_at || new Date().toISOString()
            });
          }
        }
      } catch (e) {
        console.warn('[Products] Fallback items error:', e.message);
      }
    }

    // Sort newest first
    products.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));

    // Optional search filter
    if (searchTerm) {
      products = products.filter(p =>
        (p.name && p.name.toLowerCase().includes(searchTerm)) ||
        (p.description && p.description.toLowerCase().includes(searchTerm)) ||
        (p.category && p.category.toLowerCase().includes(searchTerm)) ||
        (p.model && p.model.toLowerCase().includes(searchTerm))
      );
    }

    // Optional category filter
    if (category && category !== 'All') {
      products = products.filter(p => p.category.toLowerCase() === category.toLowerCase());
    }

    return res.json({
      count: products.length,
      products
    });
  } catch (err) {
    console.error('[Get Products Error]', err);
    return res.status(500).json({ error: 'Failed to retrieve products: ' + err.message });
  }
});

/**
 * GET /products/:id
 * Retrieve details of a single product.
 */
router.get('/:id', optionalAuth, async (req, res) => {
  const productId = req.params.id;

  try {
    let item = null;

    if (isFirestoreAvailable() && getDb()) {
      try {
        const db = getDb();
        const doc = await db.collection('products').doc(productId).get();
        if (doc.exists) {
          item = { id: doc.id, ...doc.data() };
        }
      } catch (e) {
        console.warn('[Products] Firestore doc error:', e.message);
      }
    }

    if (!item) {
      item = await getProductById(productId);
    }

    if (!item || item.deleted) {
      return res.status(404).json({ error: 'Product not found.' });
    }

    const isGuest = !req.user;
    const rentVal = item.rentPerDay !== undefined
      ? parseFloat(item.rentPerDay)
      : (parseFloat(item.price_per_day || item.pricePerDay) || 0.0);

    const product = {
      id: item.id.toString(),
      name: item.name || item.title,
      title: item.name || item.title,
      description: item.description || '',
      category: item.category || 'Others',
      condition: item.condition || 'Good',
      model: item.model || '',
      features: Array.isArray(item.features) ? item.features : [],
      maxRentDays: parseInt(item.maxRentDays, 10) || 7,
      pricePerDay: rentVal,
      rentPerDay: rentVal,
      price_per_day: rentVal,
      isFree: rentVal === 0,
      isAvailable: item.available !== false && item.status !== 'rented',
      available: item.available !== false && item.status !== 'rented',
      imageUrl: item.imageUrl || item.image_url || item.photoUrl || item.productImage || item.image || null,
      image_url: item.imageUrl || item.image_url || item.photoUrl || item.productImage || item.image || null,
      photoUrl: item.imageUrl || item.image_url || item.photoUrl || item.productImage || item.image || null,
      emoji: item.emoji || getCategoryEmoji(item.category),
      department: item.department || 'KUET',
      hallLocation: item.hallLocation || item.hall || 'Campus',
      whatsappNumber: item.whatsappNumber || '',
      createdAt: item.createdAt || item.created_at || new Date().toISOString(),
      owner: {
        id: (item.ownerId || item.owner_id || '').toString(),
        name: item.ownerName || item.owner_name || 'BorrowBox Member',
        roll: isGuest ? '•••••••' : (item.owner_roll || 'N/A'),
        department: item.owner_department || item.department || 'KUET',
        hall: item.owner_hall || item.hall || 'Campus',
        phone: isGuest ? 'Login to view' : (item.owner_phone || '+8801700000000'),
        email: isGuest ? 'Login to view' : (item.owner_email || 'student@kuet.ac.bd'),
        isVerified: true,
        canContact: !isGuest
      }
    };

    return res.json({ product });
  } catch (err) {
    console.error('[Get Product by ID Error]', err);
    return res.status(500).json({ error: 'Failed to retrieve product: ' + err.message });
  }
});

/**
 * POST /products
 * Add a new product listing into Firestore collection "products".
 * Requires name, rentPerDay (>0), maxRentDays (>=1).
 */
router.post('/', optionalAuth, async (req, res) => {
  try {
    const {
      name,
      title,
      rentPerDay,
      pricePerDay,
      price_per_day,
      maxRentDays,
      model,
      description,
      features,
      category,
      condition,
      photoUrl,
      imageUrl,
      image_url,
      whatsappNumber,
      department,
      hallLocation,
      hall
    } = req.body;

    const productName = (name || title || '').trim();
    if (!productName) {
      return res.status(400).json({ error: 'Product name is required.' });
    }

    const rentRaw = rentPerDay !== undefined ? rentPerDay : (pricePerDay !== undefined ? pricePerDay : price_per_day);
    const rentPrice = parseFloat(rentRaw);
    if (isNaN(rentPrice) || rentPrice <= 0) {
      return res.status(400).json({ error: 'Rent per day must be a number greater than 0.' });
    }

    const maxDays = parseInt(maxRentDays, 10);
    if (isNaN(maxDays) || maxDays < 1) {
      return res.status(400).json({ error: 'Maximum rent days must be a whole number >= 1.' });
    }

    const productCategory = (category || 'Others').trim();
    const productCondition = (condition || 'Good').trim();
    const productModel = (model || '').trim();
    const productDescription = (description || '').trim();

    // Features: split string lines or use array
    let featureList = [];
    if (Array.isArray(features)) {
      featureList = features.map(f => String(f).trim()).filter(Boolean);
    } else if (typeof features === 'string') {
      featureList = features.split('\n').map(f => f.trim()).filter(Boolean);
    }

    const ownerId = (req.user && (req.user.id || req.user.uid))
      ? String(req.user.id || req.user.uid)
      : String(req.body.ownerId || 'user_1');

    const ownerName = (req.user && (req.user.full_name || req.user.fullName))
      ? String(req.user.full_name || req.user.fullName)
      : String(req.body.ownerName || 'BorrowBox Member');

    const img = photoUrl || imageUrl || image_url || null;
    const createdAt = new Date().toISOString();
    const emoji = getCategoryEmoji(productCategory);
    const productWhatsapp = typeof whatsappNumber === 'string' && /^01\d{9}$/.test(whatsappNumber.trim())
      ? whatsappNumber.trim()
      : (typeof whatsappNumber === 'string' ? whatsappNumber.trim() : '');

    const newProductData = {
      name: productName,
      rentPerDay: rentPrice,
      maxRentDays: maxDays,
      model: productModel,
      description: productDescription,
      features: featureList,
      category: productCategory,
      condition: productCondition,
      whatsappNumber: productWhatsapp,
      ownerId: ownerId,
      ownerName: ownerName,
      createdAt: createdAt,
      available: true,

      // Compatibility fields for existing Android views
      title: productName,
      pricePerDay: rentPrice,
      price_per_day: rentPrice,
      isAvailable: true,
      status: 'available',
      imageUrl: img,
      image_url: img,
      emoji: emoji,
      department: department || (req.user && req.user.department) || 'KUET',
      hallLocation: hallLocation || hall || (req.user && req.user.hall) || 'Campus'
    };

    let savedId = null;
    if (isFirestoreAvailable() && getDb()) {
      const db = getDb();
      const docRef = await db.collection('products').add(newProductData);
      savedId = docRef.id;
      console.log(`[Products] Successfully saved product '${productName}' to Firestore with ID: ${savedId}`);
    } else {
      savedId = 'prod_' + Date.now();
      console.warn('[Products] Firestore not available, product assigned temporary ID:', savedId);
    }

    const savedProduct = {
      id: savedId,
      ...newProductData
    };

    return res.status(201).json({
      message: 'Product listed successfully!',
      product: savedProduct
    });
  } catch (err) {
    console.error('[Create Product Error]', err);
    return res.status(500).json({ error: 'Failed to create product: ' + err.message });
  }
});

/**
 * GET /products/mine
 * Returns all products where ownerId equals the logged-in user, newest first.
 */
router.get('/mine', requireAuth, async (req, res) => {
  try {
    const db = getDb();
    if (!db) return res.status(503).json({ error: 'Database unavailable.' });

    const userId = String(req.user.id);
    const snap = await db.collection('products')
      .where('ownerId', '==', userId)
      .get();

    const products = [];
    snap.forEach(doc => {
      const d = doc.data();
      if (d.deleted) return;

      const rentVal = d.rentPerDay !== undefined
        ? parseFloat(d.rentPerDay)
        : (parseFloat(d.price_per_day || d.pricePerDay) || 0.0);

      const isAvail = d.available !== false && d.status !== 'rented';

      products.push({
        id: doc.id,
        name: d.name || d.title || 'Product',
        title: d.name || d.title || 'Product',
        rentPerDay: rentVal,
        pricePerDay: rentVal,
        price_per_day: rentVal,
        maxRentDays: parseInt(d.maxRentDays, 10) || 7,
        model: d.model || '',
        description: d.description || '',
        features: Array.isArray(d.features) ? d.features : [],
        category: d.category || 'Others',
        condition: d.condition || 'Good',
        ownerId: userId,
        ownerName: d.ownerName || req.user.full_name || 'BorrowBox Member',
        createdAt: d.createdAt || d.created_at || new Date().toISOString(),
        available: isAvail,
        isAvailable: isAvail,
        status: isAvail ? 'available' : 'unavailable',
        imageUrl: d.imageUrl || d.image_url || d.photoUrl || d.productImage || null,
        image_url: d.imageUrl || d.image_url || d.photoUrl || d.productImage || null,
        photoUrl: d.imageUrl || d.image_url || d.photoUrl || d.productImage || null,
        emoji: d.emoji || getCategoryEmoji(d.category),
        whatsappNumber: d.whatsappNumber || ''
      });
    });

    // Sort newest first in memory
    products.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

    return res.json({ products });
  } catch (err) {
    console.error('[Get My Products Error]', err);
    return res.status(500).json({ error: 'Failed to retrieve your products: ' + err.message });
  }
});

/**
 * PUT /products/:id
 * Edit product listing. Owner only.
 */
router.put('/:id', requireAuth, async (req, res) => {
  try {
    const db = getDb();
    if (!db) return res.status(503).json({ error: 'Database unavailable.' });

    const productId = req.params.id;
    const docRef = db.collection('products').doc(productId);
    const doc = await docRef.get();

    if (!doc.exists) {
      return res.status(404).json({ error: 'Product not found.' });
    }

    const existingData = doc.data();
    if (String(existingData.ownerId) !== String(req.user.id)) {
      return res.status(403).json({ error: 'You are not authorized to edit this product.' });
    }

    const {
      name,
      title,
      rentPerDay,
      pricePerDay,
      price_per_day,
      maxRentDays,
      model,
      description,
      features,
      category,
      condition,
      photoUrl,
      imageUrl,
      image_url,
      whatsappNumber
    } = req.body;

    const productName = (name || title || existingData.name || existingData.title || '').trim();
    if (!productName) {
      return res.status(400).json({ error: 'Product name is required.' });
    }

    const rentRaw = rentPerDay !== undefined ? rentPerDay : (pricePerDay !== undefined ? pricePerDay : price_per_day);
    const rentPrice = rentRaw !== undefined ? parseFloat(rentRaw) : existingData.rentPerDay;
    if (isNaN(rentPrice) || rentPrice <= 0) {
      return res.status(400).json({ error: 'Rent per day must be a number greater than 0.' });
    }

    const maxDays = maxRentDays !== undefined ? parseInt(maxRentDays, 10) : existingData.maxRentDays;
    if (isNaN(maxDays) || maxDays < 1) {
      return res.status(400).json({ error: 'Maximum rent days must be a whole number >= 1.' });
    }

    let featureList = existingData.features || [];
    if (Array.isArray(features)) {
      featureList = features.map(f => String(f).trim()).filter(Boolean);
    } else if (typeof features === 'string') {
      featureList = features.split('\n').map(f => f.trim()).filter(Boolean);
    }

    const img = photoUrl || imageUrl || image_url || existingData.imageUrl || existingData.photoUrl || null;
    const productCategory = (category || existingData.category || 'Others').trim();
    const productCondition = (condition || existingData.condition || 'Good').trim();
    const productModel = model !== undefined ? model.trim() : (existingData.model || '');
    const productDescription = description !== undefined ? description.trim() : (existingData.description || '');
    const productWhatsapp = typeof whatsappNumber === 'string' && /^01\d{9}$/.test(whatsappNumber.trim())
      ? whatsappNumber.trim()
      : (typeof whatsappNumber === 'string' ? whatsappNumber.trim() : (existingData.whatsappNumber || ''));

    const updatedData = {
      name: productName,
      title: productName,
      rentPerDay: rentPrice,
      pricePerDay: rentPrice,
      price_per_day: rentPrice,
      maxRentDays: maxDays,
      model: productModel,
      description: productDescription,
      features: featureList,
      category: productCategory,
      condition: productCondition,
      whatsappNumber: productWhatsapp,
      imageUrl: img,
      image_url: img,
      photoUrl: img,
      emoji: getCategoryEmoji(productCategory),
      updatedAt: new Date().toISOString()
    };

    await docRef.update(updatedData);

    return res.json({
      message: 'Product updated successfully!',
      product: {
        id: productId,
        ...existingData,
        ...updatedData
      }
    });
  } catch (err) {
    console.error('[Update Product Error]', err);
    return res.status(500).json({ error: 'Failed to update product: ' + err.message });
  }
});

/**
 * DELETE /products/:id
 * Delete product listing. Owner only.
 * Cancels pending rentals on this product and notifies renters.
 */
router.delete('/:id', requireAuth, async (req, res) => {
  try {
    const db = getDb();
    if (!db) return res.status(503).json({ error: 'Database unavailable.' });

    const productId = req.params.id;
    const docRef = db.collection('products').doc(productId);
    const doc = await docRef.get();

    if (!doc.exists) {
      return res.status(404).json({ error: 'Product not found.' });
    }

    const prodData = doc.data();
    if (String(prodData.ownerId) !== String(req.user.id)) {
      return res.status(403).json({ error: 'You are not authorized to delete this product.' });
    }

    const { sendPushToUser } = require('../services/fcmService');

    // 1. Find any rentals for this product
    try {
      const rentalsSnap = await db.collection('rentals').where('productId', '==', productId).get();
      for (const rDoc of rentalsSnap.docs) {
        const rData = rDoc.data();
        if (rData.status === 'pending') {
          await rDoc.ref.update({ status: 'cancelled' }).catch(() => {});
          if (rData.renterId) {
            await db.collection('notifications').add({
              userId: rData.renterId,
              type: 'rental_cancelled',
              title: 'Rental request cancelled',
              message: `The product "${prodData.name || 'item'}" was deleted by the owner.`,
              rentalId: rDoc.id,
              productId: productId,
              read: false,
              createdAt: new Date().toISOString()
            }).catch(() => {});

            sendPushToUser(rData.renterId, {
              title: 'Rental request cancelled',
              body: `The product "${prodData.name || 'item'}" was deleted by the owner.`,
              data: {
                type: 'rental_cancelled',
                rentalId: rDoc.id,
                productId: productId
              }
            }).catch(() => {});
          }
        }
      }
    } catch (rErr) {
      console.warn('[Delete Product] Warning while cancelling rentals:', rErr.message);
    }

    // 2. Delete product document (and mark deleted for memory fallbacks)
    await docRef.delete();

    console.log(`[Products] Product ${productId} successfully deleted by owner ${req.user.id}`);
    return res.json({
      message: 'Product deleted successfully.',
      id: productId
    });
  } catch (err) {
    console.error('[Delete Product Error]', err);
    return res.status(500).json({ error: 'Failed to delete product: ' + err.message });
  }
});

module.exports = router;

const express = require('express');
const pool = require('../db/connection');
const { requireAuth } = require('../middleware/authMiddleware');

const router = express.Router();

/**
 * POST /rentals
 * Submit a rental request.
 * Enforces email verification on backend: rejects unverified users with 403 (TASK 6).
 */
router.post('/', requireAuth, async (req, res) => {
  try {
    const user = req.user;

    // Backend verification enforcement
    if (!user.is_email_verified) {
      return res.status(403).json({
        error: 'Verification required: You must verify your KUET student email before renting items.',
        code: 'UNVERIFIED_STUDENT'
      });
    }

    const { product_id, start_date, end_date } = req.body;

    if (!product_id || !start_date || !end_date) {
      return res.status(400).json({ error: 'Product ID, start date, and end date are required.' });
    }

    // Check product
    const [pRows] = await pool.query('SELECT * FROM products WHERE id = ?', [product_id]);
    if (pRows.length === 0) {
      return res.status(404).json({ error: 'Product not found.' });
    }

    const product = pRows[0];

    // Self-rent prevention
    if (product.owner_id === user.id) {
      return res.status(400).json({ error: 'You cannot submit a rental request for your own item.' });
    }

    // Check availability
    if (product.status !== 'available') {
      return res.status(400).json({ error: 'This item is currently unavailable or already rented.' });
    }

    // Date validation
    const startDateObj = new Date(start_date);
    const endDateObj = new Date(end_date);

    if (isNaN(startDateObj.getTime()) || isNaN(endDateObj.getTime())) {
      return res.status(400).json({ error: 'Invalid date format. Use YYYY-MM-DD.' });
    }

    if (endDateObj <= startDateObj) {
      return res.status(400).json({ error: 'End date must be after start date.' });
    }

    // Calculate days and total price
    const diffTime = Math.abs(endDateObj - startDateObj);
    const diffDays = Math.max(1, Math.ceil(diffTime / (1000 * 60 * 60 * 24)));
    const dailyPrice = parseFloat(product.price_per_day) || 0.0;
    const totalPrice = dailyPrice * diffDays;

    // Insert rental record
    const [insertResult] = await pool.query(
      `INSERT INTO rentals (product_id, renter_id, start_date, end_date, status, total_price)
       VALUES (?, ?, ?, ?, 'pending', ?)`,
      [product_id, user.id, start_date, end_date, totalPrice]
    );

    const rentalId = insertResult.insertId;

    return res.status(201).json({
      message: 'Rental request submitted successfully!',
      rental: {
        id: rentalId,
        productId: product.id,
        productTitle: product.title,
        renterId: user.id,
        renterName: user.full_name,
        startDate: start_date,
        endDate: end_date,
        durationDays: diffDays,
        totalPrice,
        status: 'pending'
      }
    });
  } catch (err) {
    console.error('[Create Rental Error]', err);
    return res.status(500).json({ error: 'Failed to create rental request: ' + err.message });
  }
});

/**
 * GET /rentals/mine
 * Retrieve all rentals associated with current user (both as renter and as owner).
 */
router.get('/mine', requireAuth, async (req, res) => {
  try {
    const userId = req.user.id;

    // Rentals where current user is renter
    const [myBorrowRequests] = await pool.query(
      `SELECT
        r.id,
        r.product_id,
        r.start_date,
        r.end_date,
        r.status,
        r.total_price,
        r.created_at,
        p.title AS product_name,
        p.emoji AS product_emoji,
        p.price_per_day,
        p.department AS product_department,
        p.hall AS product_hall,
        u.full_name AS owner_name,
        u.phone AS owner_phone
       FROM rentals r
       JOIN products p ON r.product_id = p.id
       JOIN users u ON p.owner_id = u.id
       WHERE r.renter_id = ?
       ORDER BY r.id DESC`,
      [userId]
    );

    // Incoming rental requests for items current user owns
    const [incomingRequests] = await pool.query(
      `SELECT
        r.id,
        r.product_id,
        r.start_date,
        r.end_date,
        r.status,
        r.total_price,
        r.created_at,
        p.title AS product_name,
        p.emoji AS product_emoji,
        u.full_name AS renter_name,
        u.roll_no AS renter_roll,
        u.phone AS renter_phone,
        u.email AS renter_email,
        u.department AS renter_department,
        u.hall AS renter_hall,
        u.is_email_verified AS renter_verified
       FROM rentals r
       JOIN products p ON r.product_id = p.id
       JOIN users u ON r.renter_id = u.id
       WHERE p.owner_id = ?
       ORDER BY r.id DESC`,
      [userId]
    );

    return res.json({
      myRentals: myBorrowRequests.map(r => ({
        id: r.id.toString(),
        productId: r.product_id.toString(),
        productName: r.product_name,
        productEmoji: r.product_emoji || '📦',
        pricePerDay: parseFloat(r.price_per_day) || 0.0,
        startDate: r.start_date,
        endDate: r.end_date,
        status: r.status,
        totalPrice: parseFloat(r.total_price) || 0.0,
        ownerName: r.owner_name,
        ownerPhone: r.owner_phone,
        hallLocation: r.product_hall,
        createdAt: r.created_at
      })),
      incomingRequests: incomingRequests.map(r => ({
        id: r.id.toString(),
        productId: r.product_id.toString(),
        productName: r.product_name,
        productEmoji: r.product_emoji || '📦',
        startDate: r.start_date,
        endDate: r.end_date,
        status: r.status,
        totalPrice: parseFloat(r.total_price) || 0.0,
        renterName: r.renter_name,
        renterRoll: r.renter_roll,
        renterPhone: r.renter_phone,
        renterEmail: r.renter_email,
        renterDepartment: r.renter_department,
        renterHall: r.renter_hall,
        renterVerified: Boolean(r.renter_verified),
        createdAt: r.created_at
      }))
    });
  } catch (err) {
    console.error('[Get My Rentals Error]', err);
    return res.status(500).json({ error: 'Failed to retrieve rentals: ' + err.message });
  }
});

module.exports = router;

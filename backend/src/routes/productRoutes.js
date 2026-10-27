const express = require('express');
const pool = require('../db/connection');
const { requireAuth, optionalAuth } = require('../middleware/authMiddleware');

const router = express.Router();

/**
 * GET /products
 * List products with optional search query & category filter.
 */
router.get('/', optionalAuth, async (req, res) => {
  try {
    const { search, q, category } = req.query;
    const searchTerm = (search || q || '').trim();

    let sql = `
      SELECT
        p.id,
        p.owner_id,
        p.title,
        p.description,
        p.category,
        p.price_per_day,
        p.image_url,
        p.emoji,
        p.department,
        p.hall,
        p.status,
        p.created_at,
        u.full_name AS owner_name,
        u.department AS owner_department,
        u.hall AS owner_hall
      FROM products p
      JOIN users u ON p.owner_id = u.id
      WHERE 1=1
    `;
    const params = [];

    if (searchTerm) {
      sql += ` AND (
        p.title LIKE ? OR
        p.description LIKE ? OR
        p.category LIKE ? OR
        p.department LIKE ? OR
        p.hall LIKE ?
      )`;
      const wild = `%${searchTerm}%`;
      params.push(wild, wild, wild, wild, wild);
    }

    if (category && category !== 'All') {
      sql += ` AND p.category = ?`;
      params.push(category);
    }

    sql += ` ORDER BY p.id DESC`;

    const [rows] = await pool.query(sql, params);

    // Format products for response
    const products = rows.map(item => ({
      id: item.id.toString(),
      name: item.title,
      description: item.description,
      category: item.category,
      pricePerDay: parseFloat(item.price_per_day) || 0.0,
      isFree: parseFloat(item.price_per_day) === 0,
      isAvailable: item.status === 'available',
      imageUrl: item.image_url,
      emoji: item.emoji || '📦',
      department: item.department,
      hallLocation: item.hall,
      ownerId: item.owner_id.toString(),
      ownerName: item.owner_name,
      createdAt: item.created_at
    }));

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
 * Retrieve full product details including owner contact details (TASK 5).
 * Owner contact info is visible to authenticated users, masked for guests.
 */
router.get('/:id', optionalAuth, async (req, res) => {
  try {
    const productId = req.params.id;

    const [rows] = await pool.query(
      `SELECT
        p.id,
        p.owner_id,
        p.title,
        p.description,
        p.category,
        p.price_per_day,
        p.image_url,
        p.emoji,
        p.department,
        p.hall,
        p.status,
        p.created_at,
        u.full_name AS owner_name,
        u.roll_no AS owner_roll,
        u.phone AS owner_phone,
        u.email AS owner_email,
        u.department AS owner_department,
        u.hall AS owner_hall,
        u.is_email_verified AS owner_verified
       FROM products p
       JOIN users u ON p.owner_id = u.id
       WHERE p.id = ?`,
      [productId]
    );

    if (rows.length === 0) {
      return res.status(404).json({ error: 'Product not found.' });
    }

    const item = rows[0];
    const isGuest = !req.user;

    const product = {
      id: item.id.toString(),
      name: item.title,
      description: item.description,
      category: item.category,
      pricePerDay: parseFloat(item.price_per_day) || 0.0,
      isFree: parseFloat(item.price_per_day) === 0,
      isAvailable: item.status === 'available',
      imageUrl: item.image_url,
      emoji: item.emoji || '📦',
      department: item.department,
      hallLocation: item.hall,
      createdAt: item.created_at,
      owner: {
        id: item.owner_id.toString(),
        name: item.owner_name,
        roll: isGuest ? '•••••••' : item.owner_roll,
        department: item.owner_department,
        hall: item.owner_hall,
        phone: isGuest ? 'Login to view' : item.owner_phone,
        email: isGuest ? 'Login to view' : item.owner_email,
        isVerified: Boolean(item.owner_verified),
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
 * Add a new product listing (requires auth).
 */
router.post('/', requireAuth, async (req, res) => {
  try {
    const {
      title,
      description,
      category,
      price_per_day,
      emoji,
      department,
      hall
    } = req.body;

    if (!title || !description || !category) {
      return res.status(400).json({ error: 'Title, description, and category are required.' });
    }

    const price = price_per_day ? parseFloat(price_per_day) : 0.00;
    const dept = department || req.user.department;
    const hallLoc = hall || req.user.hall;
    const itemEmoji = emoji || '📦';

    const [result] = await pool.query(
      `INSERT INTO products (owner_id, title, description, category, price_per_day, emoji, department, hall, status)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'available')`,
      [req.user.id, title.trim(), description.trim(), category.trim(), price, itemEmoji, dept, hallLoc]
    );

    const [newRow] = await pool.query('SELECT * FROM products WHERE id = ?', [result.insertId]);

    return res.status(201).json({
      message: 'Product listed successfully!',
      product: newRow[0]
    });
  } catch (err) {
    console.error('[Create Product Error]', err);
    return res.status(500).json({ error: 'Failed to create product: ' + err.message });
  }
});

module.exports = router;

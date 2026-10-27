const jwt = require('jsonwebtoken');
const pool = require('../db/connection');
require('dotenv').config();

const JWT_SECRET = process.env.JWT_SECRET || 'borrowbox_kuet_super_secret_jwt_key_2026_xyz';

/**
 * Mandatory authentication middleware. Rejects with 401 if missing/invalid token.
 */
async function requireAuth(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Authentication required. Please log in.' });
  }

  const token = authHeader.split(' ')[1];
  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    const [rows] = await pool.query(
      'SELECT id, full_name, roll_no, email, phone, department, hall, is_email_verified, is_roll_verified, created_at FROM users WHERE id = ?',
      [decoded.userId]
    );

    if (rows.length === 0) {
      return res.status(401).json({ error: 'User account not found.' });
    }

    req.user = rows[0];
    next();
  } catch (err) {
    return res.status(401).json({ error: 'Invalid or expired session token. Please log in again.' });
  }
}

/**
 * Optional authentication middleware. Populates req.user if valid token provided, but doesn't block guests.
 */
async function optionalAuth(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    req.user = null;
    return next();
  }

  const token = authHeader.split(' ')[1];
  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    const [rows] = await pool.query(
      'SELECT id, full_name, roll_no, email, phone, department, hall, is_email_verified, is_roll_verified, created_at FROM users WHERE id = ?',
      [decoded.userId]
    );
    req.user = rows.length > 0 ? rows[0] : null;
  } catch (err) {
    req.user = null;
  }
  next();
}

module.exports = {
  requireAuth,
  optionalAuth,
  JWT_SECRET
};

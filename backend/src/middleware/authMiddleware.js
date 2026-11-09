const jwt = require('jsonwebtoken');
const pool = require('../db/connection');
const { findUserById } = require('../db/firebase');
require('dotenv').config();

const JWT_SECRET = process.env.JWT_SECRET || 'borrowbox_kuet_super_secret_jwt_key_2026_xyz';

async function getUserById(id) {
  // First try Firebase / in-memory store
  try {
    const user = await findUserById(id);
    if (user) {
      return {
        id: user.id,
        full_name: user.full_name || user.fullName,
        roll_no: user.roll_no || user.roll,
        email: user.email,
        phone: user.phone,
        department: user.department,
        hall: user.hall,
        is_email_verified: Boolean(user.is_email_verified || user.isEmailVerified),
        is_roll_verified: Boolean(user.is_roll_verified || user.isRollVerified),
        created_at: user.created_at
      };
    }
  } catch (e) {
    // ignore
  }

  // Fallback to MySQL if configured
  try {
    const [rows] = await pool.query(
      'SELECT id, full_name, roll_no, email, phone, department, hall, is_email_verified, is_roll_verified, created_at FROM users WHERE id = ?',
      [id]
    );
    if (rows && rows.length > 0) return rows[0];
  } catch (err) {
    // ignore
  }

  return null;
}

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
    const user = await getUserById(decoded.userId);

    if (!user) {
      return res.status(401).json({ error: 'User account not found.' });
    }

    req.user = user;
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
    const user = await getUserById(decoded.userId);
    req.user = user || null;
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

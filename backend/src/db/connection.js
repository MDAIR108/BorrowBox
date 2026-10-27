const mysql = require('mysql2/promise');
const bcrypt = require('bcryptjs');
require('dotenv').config();

const realPool = mysql.createPool({
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT, 10) || 3306,
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '',
  database: process.env.DB_NAME || 'borrowbox',
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0,
  decimalNumbers: true
});

// In-Memory fallback store for development when local MySQL is offline
const memoryStore = {
  users: [
    {
      id: 1,
      full_name: 'Tanvir Ahmed',
      roll_no: '2307001',
      email: '2307001@stud.kuet.ac.bd',
      password_hash: bcrypt.hashSync('Password123!', 10),
      phone: '+8801700000001',
      department: 'Computer Science & Engineering (CSE)',
      hall: 'Amar Ekushey Hall',
      is_email_verified: 1,
      is_roll_verified: 1,
      created_at: new Date()
    },
    {
      id: 2,
      full_name: 'Shadman Sakib',
      roll_no: '2305012',
      email: '2305012@stud.kuet.ac.bd',
      password_hash: bcrypt.hashSync('Password123!', 10),
      phone: '+8801700000002',
      department: 'Mechanical Engineering (ME)',
      hall: 'Fazlul Haque Hall',
      is_email_verified: 1,
      is_roll_verified: 1,
      created_at: new Date()
    }
  ],
  verifications: [],
  products: [
    {
      id: 1,
      owner_id: 1,
      title: 'Casio fx-991EX ClassWiz',
      description: 'Original scientific calculator with natural textbook display. Essential for calculus and exam hall.',
      category: 'Tools',
      price_per_day: 20.00,
      image_url: null,
      emoji: '🧮',
      department: 'CSE Dept',
      hall: 'Amar Ekushey Hall',
      status: 'available',
      created_at: new Date()
    },
    {
      id: 2,
      owner_id: 2,
      title: 'Rotary Mini Drafter + Sheet Clamps',
      description: 'Full precision engineering drawing drafter set with scale and clamp holder.',
      category: 'Tools',
      price_per_day: 15.00,
      image_url: null,
      emoji: '📐',
      department: 'ME Dept',
      hall: 'Fazlul Haque Hall',
      status: 'available',
      created_at: new Date()
    },
    {
      id: 3,
      owner_id: 1,
      title: 'Cotton White Lab Coat (Clean, Size L)',
      description: 'Pure cotton protective lab coat. Freshly cleaned, suitable for chemistry and workshop labs.',
      category: 'Labs',
      price_per_day: 0.00,
      image_url: null,
      emoji: '🥼',
      department: 'ChE Dept',
      hall: 'Lalan Shah Hall',
      status: 'available',
      created_at: new Date()
    },
    {
      id: 4,
      owner_id: 2,
      title: 'Arduino Mega 2560 & Sensor Kit',
      description: 'Microcontroller board complete with breadboard, jumper wires, ultrasonic and temperature sensors.',
      category: 'Gadgets',
      price_per_day: 35.00,
      image_url: null,
      emoji: '⚡',
      department: 'EEE Dept',
      hall: 'Bangabandhu Hall',
      status: 'available',
      created_at: new Date()
    },
    {
      id: 5,
      owner_id: 1,
      title: 'Higher Engineering Mathematics - B.S. Grewal',
      description: '44th edition, clean textbook with all solved university question sets. Currently on loan until Friday.',
      category: 'Books',
      price_per_day: 10.00,
      image_url: null,
      emoji: '📚',
      department: 'Math Dept',
      hall: 'Khan Jahan Ali Hall',
      status: 'rented',
      created_at: new Date()
    },
    {
      id: 6,
      owner_id: 2,
      title: 'Phoenix Hero 26" Campus Bicycle',
      description: 'Smooth city ride bicycle with front basket and lock.',
      category: 'Cycles',
      price_per_day: 50.00,
      image_url: null,
      emoji: '🚴',
      department: 'CE Dept',
      hall: 'Rokeya Hall',
      status: 'available',
      created_at: new Date()
    }
  ],
  rentals: [],
  userIdSeq: 10,
  verifIdSeq: 10,
  rentalIdSeq: 10
};

function executeMemoryQuery(sql, params = []) {
  const cleanSql = sql.trim().replace(/\s+/g, ' ');

  // 1. SELECT 1 (Health check)
  if (cleanSql === 'SELECT 1') {
    return [[{ '1': 1 }]];
  }

  // 2. Uniqueness check in users
  if (cleanSql.includes('SELECT id, roll_no, email FROM users WHERE roll_no = ? OR email = ?')) {
    const [roll, email] = params;
    const found = memoryStore.users.filter(u => u.roll_no === roll || u.email.toLowerCase() === email.toLowerCase());
    return [found.map(u => ({ id: u.id, roll_no: u.roll_no, email: u.email }))];
  }

  // 3. User login lookup
  if (cleanSql.includes('SELECT * FROM users WHERE email = ? OR roll_no = ?')) {
    const [email, roll] = params;
    const found = memoryStore.users.filter(u => u.email.toLowerCase() === email.toLowerCase() || u.roll_no === roll);
    return [found];
  }

  // 4. User by ID
  if (cleanSql.includes('FROM users WHERE id = ?')) {
    const [id] = params;
    const found = memoryStore.users.filter(u => u.id === Number(id));
    return [found];
  }

  // 5. User by email or roll_no for code dispatch
  if (cleanSql.includes('SELECT id, email, full_name FROM users WHERE email = ? OR roll_no = ?')) {
    const [email, roll] = params;
    const found = memoryStore.users.filter(u => u.email.toLowerCase() === email.toLowerCase() || u.roll_no === roll);
    return [found.map(u => ({ id: u.id, email: u.email, full_name: u.full_name }))];
  }

  // 6. User insert (Registration)
  if (cleanSql.startsWith('INSERT INTO users')) {
    const [full_name, roll_no, email, password_hash, phone, department, hall] = params;
    const newId = ++memoryStore.userIdSeq;
    const newUser = {
      id: newId,
      full_name,
      roll_no,
      email,
      password_hash,
      phone,
      department,
      hall,
      is_email_verified: 0,
      is_roll_verified: 1,
      created_at: new Date()
    };
    memoryStore.users.push(newUser);
    return [{ insertId: newId }];
  }

  // 7. Last verification record (cooldown)
  if (cleanSql.includes('SELECT created_at FROM email_verifications WHERE user_id = ? ORDER BY id DESC LIMIT 1')) {
    const [userId] = params;
    const userVerifs = memoryStore.verifications.filter(v => v.user_id === Number(userId));
    if (userVerifs.length === 0) return [[]];
    const last = userVerifs[userVerifs.length - 1];
    return [[{ created_at: last.created_at }]];
  }

  // 8. Insert verification OTP
  if (cleanSql.startsWith('INSERT INTO email_verifications')) {
    const [user_id, code_hash, expires_at] = params;
    const newId = ++memoryStore.verifIdSeq;
    const newVerif = {
      id: newId,
      user_id: Number(user_id),
      code_hash,
      expires_at: new Date(expires_at),
      attempts: 0,
      used: 0,
      created_at: new Date()
    };
    memoryStore.verifications.push(newVerif);
    return [{ insertId: newId }];
  }

  // 9. Unused verification lookup for verify-code
  if (cleanSql.includes('SELECT * FROM email_verifications WHERE user_id = ? AND used = 0 ORDER BY id DESC LIMIT 1')) {
    const [userId] = params;
    const userVerifs = memoryStore.verifications.filter(v => v.user_id === Number(userId) && v.used === 0);
    if (userVerifs.length === 0) return [[]];
    const last = userVerifs[userVerifs.length - 1];
    return [[last]];
  }

  // 10. Update verification attempts
  if (cleanSql.includes('UPDATE email_verifications SET attempts = ? WHERE id = ?')) {
    const [attempts, id] = params;
    const v = memoryStore.verifications.find(item => item.id === Number(id));
    if (v) v.attempts = Number(attempts);
    return [{ affectedRows: v ? 1 : 0 }];
  }

  // 11. Mark verification used
  if (cleanSql.includes('UPDATE email_verifications SET used = 1 WHERE id = ?')) {
    const [id] = params;
    const v = memoryStore.verifications.find(item => item.id === Number(id));
    if (v) v.used = 1;
    return [{ affectedRows: v ? 1 : 0 }];
  }

  // 12. Mark user verified
  if (cleanSql.includes('UPDATE users SET is_email_verified = 1 WHERE id = ?')) {
    const [id] = params;
    const u = memoryStore.users.find(item => item.id === Number(id));
    if (u) u.is_email_verified = 1;
    return [{ affectedRows: u ? 1 : 0 }];
  }

  // 13. Products list
  if (cleanSql.includes('FROM products p JOIN users u ON p.owner_id = u.id')) {
    const rows = memoryStore.products.map(p => {
      const owner = memoryStore.users.find(u => u.id === p.owner_id) || {};
      return {
        id: p.id,
        owner_id: p.owner_id,
        title: p.title,
        description: p.description,
        category: p.category,
        price_per_day: p.price_per_day,
        image_url: p.image_url,
        emoji: p.emoji,
        department: p.department,
        hall: p.hall,
        status: p.status,
        created_at: p.created_at,
        owner_name: owner.full_name || 'KUETian',
        owner_department: owner.department || 'KUET',
        owner_hall: owner.hall || 'Campus'
      };
    });
    return [rows];
  }

  // 14. Product by ID
  if (cleanSql.includes('FROM products WHERE id = ?')) {
    const [id] = params;
    const p = memoryStore.products.find(item => item.id === Number(id));
    return [p ? [p] : []];
  }

  // 15. Product details with owner
  if (cleanSql.includes('FROM products p JOIN users u ON p.owner_id = u.id WHERE p.id = ?')) {
    const [id] = params;
    const p = memoryStore.products.find(item => item.id === Number(id));
    if (!p) return [[]];
    const owner = memoryStore.users.find(u => u.id === p.owner_id) || {};
    return [[{
      ...p,
      owner_name: owner.full_name || 'KUETian',
      owner_roll: owner.roll_no || '',
      owner_phone: owner.phone || '',
      owner_email: owner.email || '',
      owner_department: owner.department || 'KUET',
      owner_hall: owner.hall || 'Campus',
      owner_verified: owner.is_email_verified || 0
    }]];
  }

  // 16. Rental insert
  if (cleanSql.startsWith('INSERT INTO rentals')) {
    const [product_id, renter_id, start_date, end_date, total_price] = params;
    const newId = ++memoryStore.rentalIdSeq;
    const newRental = {
      id: newId,
      product_id: Number(product_id),
      renter_id: Number(renter_id),
      start_date,
      end_date,
      status: 'pending',
      total_price: Number(total_price),
      created_at: new Date()
    };
    memoryStore.rentals.push(newRental);
    return [{ insertId: newId }];
  }

  // 17. Rentals mine
  if (cleanSql.includes('FROM rentals r') && cleanSql.includes('WHERE r.renter_id = ?')) {
    const [userId] = params;
    const myBorrow = memoryStore.rentals
      .filter(r => r.renter_id === Number(userId))
      .map(r => {
        const p = memoryStore.products.find(item => item.id === r.product_id) || {};
        const owner = memoryStore.users.find(u => u.id === p.owner_id) || {};
        return {
          id: r.id,
          product_id: r.product_id,
          start_date: r.start_date,
          end_date: r.end_date,
          status: r.status,
          total_price: r.total_price,
          created_at: r.created_at,
          product_name: p.title || 'Product',
          product_emoji: p.emoji || '📦',
          price_per_day: p.price_per_day || 0,
          product_department: p.department || '',
          product_hall: p.hall || '',
          owner_name: owner.full_name || 'KUETian',
          owner_phone: owner.phone || ''
        };
      });
    return [myBorrow];
  }

  if (cleanSql.includes('FROM rentals r') && cleanSql.includes('WHERE p.owner_id = ?')) {
    const [ownerId] = params;
    const incoming = memoryStore.rentals
      .filter(r => {
        const p = memoryStore.products.find(item => item.id === r.product_id);
        return p && p.owner_id === Number(ownerId);
      })
      .map(r => {
        const p = memoryStore.products.find(item => item.id === r.product_id) || {};
        const renter = memoryStore.users.find(u => u.id === r.renter_id) || {};
        return {
          id: r.id,
          product_id: r.product_id,
          start_date: r.start_date,
          end_date: r.end_date,
          status: r.status,
          total_price: r.total_price,
          created_at: r.created_at,
          product_name: p.title || 'Product',
          product_emoji: p.emoji || '📦',
          renter_name: renter.full_name || 'Student',
          renter_roll: renter.roll_no || '',
          renter_phone: renter.phone || '',
          renter_email: renter.email || '',
          renter_department: renter.department || '',
          renter_hall: renter.hall || '',
          renter_verified: renter.is_email_verified || 0
        };
      });
    return [incoming];
  }

  return [[]];
}

const pool = {
  async query(sql, params) {
    try {
      return await realPool.query(sql, params);
    } catch (err) {
      if ((process.env.NODE_ENV || 'development') !== 'production') {
        return executeMemoryQuery(sql, params);
      }
      throw err;
    }
  },

  async checkMysql() {
    try {
      await realPool.query('SELECT 1');
      return true;
    } catch (e) {
      return false;
    }
  },

  realPool
};

module.exports = pool;

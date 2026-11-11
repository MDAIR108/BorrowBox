'use strict';

const path = require('path');
const fs = require('fs');
const bcrypt = require('bcryptjs');

let admin = null;
let db = null;
let rtdb = null;
let isFirestoreAvailable = false;

// In-Memory store fallback when serviceAccountKey.json is not yet provided
const memoryStore = {
  users: [
    {
      id: '1',
      uid: '1',
      full_name: 'Tanvir Ahmed',
      roll_no: '2307001',
      email: '2307001@stud.kuet.ac.bd',
      password_hash: bcrypt.hashSync('Password123!', 10),
      phone: '+8801700000001',
      department: 'Computer Science & Engineering (CSE)',
      hall: 'Amar Ekushey Hall',
      is_email_verified: true,
      is_roll_verified: true,
      created_at: new Date().toISOString()
    }
  ],
  verifications: [],
  products: [
    {
      id: '1',
      owner_id: '1',
      title: 'Casio fx-991EX ClassWiz',
      description: 'Original scientific calculator with natural textbook display. Essential for calculus and exam hall.',
      category: 'Tools',
      price_per_day: 20.00,
      image_url: null,
      emoji: '🧮',
      department: 'CSE Dept',
      hall: 'Amar Ekushey Hall',
      status: 'available',
      created_at: new Date().toISOString()
    },
    {
      id: '2',
      owner_id: '1',
      title: 'Rotary Mini Drafter + Sheet Clamps',
      description: 'Full precision engineering drawing drafter set with scale and clamp holder.',
      category: 'Tools',
      price_per_day: 15.00,
      image_url: null,
      emoji: '📐',
      department: 'ME Dept',
      hall: 'Fazlul Haque Hall',
      status: 'available',
      created_at: new Date().toISOString()
    },
    {
      id: '3',
      owner_id: '1',
      title: 'Cotton White Lab Coat (Clean, Size L)',
      description: 'Pure cotton protective lab coat. Freshly cleaned, suitable for chemistry and workshop labs.',
      category: 'Labs',
      price_per_day: 0.00,
      image_url: null,
      emoji: '🥼',
      department: 'ChE Dept',
      hall: 'Lalan Shah Hall',
      status: 'available',
      created_at: new Date().toISOString()
    },
    {
      id: '4',
      owner_id: '1',
      title: 'Arduino Mega 2560 & Sensor Kit',
      description: 'Microcontroller board complete with breadboard, jumper wires, ultrasonic and temperature sensors.',
      category: 'Gadgets',
      price_per_day: 35.00,
      image_url: null,
      emoji: '⚡',
      department: 'EEE Dept',
      hall: 'Bangabandhu Hall',
      status: 'available',
      created_at: new Date().toISOString()
    },
    {
      id: '5',
      owner_id: '1',
      title: 'Higher Engineering Mathematics - B.S. Grewal',
      description: '44th edition, clean textbook with all solved university question sets. Currently on loan until Friday.',
      category: 'Books',
      price_per_day: 10.00,
      image_url: null,
      emoji: '📚',
      department: 'Math Dept',
      hall: 'Khan Jahan Ali Hall',
      status: 'rented',
      created_at: new Date().toISOString()
    },
    {
      id: '6',
      owner_id: '1',
      title: 'Phoenix Hero 26" Campus Bicycle',
      description: 'Smooth city ride bicycle with front basket and lock.',
      category: 'Cycles',
      price_per_day: 50.00,
      image_url: null,
      emoji: '🚴',
      department: 'CE Dept',
      hall: 'Rokeya Hall',
      status: 'available',
      created_at: new Date().toISOString()
    }
  ],
  rentals: []
};

function initFirebase() {
  if (db) return db;

  let initializeApp, cert, getApps, getFirestore;
  try {
    const appModule = require('firebase-admin/app');
    const firestoreModule = require('firebase-admin/firestore');
    initializeApp = appModule.initializeApp;
    cert = appModule.cert;
    getApps = appModule.getApps;
    getFirestore = firestoreModule.getFirestore;
  } catch (e) {
    try {
      const adminModule = require('firebase-admin');
      initializeApp = adminModule.initializeApp;
      cert = adminModule.credential ? adminModule.credential.cert : adminModule.cert;
      getApps = adminModule.getApps || (() => adminModule.apps || []);
      getFirestore = adminModule.firestore || (() => null);
    } catch (e2) {
      console.warn('[Firebase] firebase-admin package not loaded:', e2.message);
      return null;
    }
  }

  const keyCandidates = [
    process.env.FIREBASE_SERVICE_ACCOUNT_KEY,
    process.env.GOOGLE_APPLICATION_CREDENTIALS,
    path.join(__dirname, '../../serviceAccountKey.json'),
    path.join(__dirname, '../../service-account.json'),
    path.join(__dirname, '../../../serviceAccountKey.json'),
    path.join(__dirname, '../../../service-account.json'),
  ].filter(Boolean);

  const foundKey = keyCandidates.find(p => fs.existsSync(p));

  if (foundKey) {
    try {
      const serviceAccount = JSON.parse(fs.readFileSync(foundKey, 'utf8'));
      const databaseURL = process.env.FIREBASE_DATABASE_URL ||
        `https://${serviceAccount.project_id || 'borrowbox-13c4a'}-default-rtdb.firebaseio.com`;

      if (getApps().length === 0) {
        initializeApp({
          credential: cert(serviceAccount),
          databaseURL: databaseURL
        });
      }
      db = getFirestore();
      isFirestoreAvailable = true;

      try {
        const { getDatabase } = require('firebase-admin/database');
        rtdb = getDatabase();
      } catch (rErr) {
        try {
          const adminModule = require('firebase-admin');
          if (typeof adminModule.database === 'function') {
            rtdb = adminModule.database();
          }
        } catch (ignored) {}
      }

      console.log(`[Firebase] ✅ Initialized Firebase Admin SDK with service account: ${foundKey}`);
      return db;
    } catch (e) {
      console.error(`[Firebase] ❌ Failed to initialize with service account ${foundKey}:`, e.message);
    }
  }

  if (process.env.FIREBASE_PROJECT_ID) {
    try {
      if (getApps().length === 0) {
        initializeApp({ projectId: process.env.FIREBASE_PROJECT_ID });
      }
      db = getFirestore();
      isFirestoreAvailable = true;
      console.log(`[Firebase] ✅ Initialized Firebase Admin SDK with projectId: ${process.env.FIREBASE_PROJECT_ID}`);
      return db;
    } catch (e) {
      console.error('[Firebase] ❌ Failed to initialize with FIREBASE_PROJECT_ID:', e.message);
    }
  }

  console.log('[Firebase] ℹ️  serviceAccountKey.json not found in backend/. Running with in-memory store until key is placed.');
  return null;
}

// User helper methods
async function findUserByRollOrEmail(rollOrEmail) {
  const norm = (rollOrEmail || '').trim().toLowerCase();
  const raw = (rollOrEmail || '').trim();

  if (isFirestoreAvailable && db) {
    try {
      const emailSnap = await db.collection('users').where('email', '==', norm).limit(1).get();
      if (!emailSnap.empty) {
        const doc = emailSnap.docs[0];
        return { id: doc.id, ...doc.data() };
      }
      const rollSnap = await db.collection('users').where('roll_no', '==', raw).limit(1).get();
      if (!rollSnap.empty) {
        const doc = rollSnap.docs[0];
        return { id: doc.id, ...doc.data() };
      }
      const rollNumSnap = await db.collection('users').where('rollNumber', '==', raw).limit(1).get();
      if (!rollNumSnap.empty) {
        const doc = rollNumSnap.docs[0];
        return { id: doc.id, ...doc.data() };
      }
    } catch (e) {
      console.warn('[Firebase] Firestore query error, falling back to memory store:', e.message);
    }
  }

  return memoryStore.users.find(u =>
    u.email.toLowerCase() === norm ||
    u.roll_no === raw ||
    (u.rollNumber && u.rollNumber === raw)
  ) || null;
}

async function findUserById(id) {
  const strId = String(id);
  if (isFirestoreAvailable && db) {
    try {
      const doc = await db.collection('users').doc(strId).get();
      if (doc.exists) {
        return { id: doc.id, ...doc.data() };
      }
    } catch (e) {
      console.warn('[Firebase] Firestore doc get error, falling back to memory store:', e.message);
    }
  }

  return memoryStore.users.find(u => String(u.id) === strId || String(u.uid) === strId) || null;
}

async function createUser(user) {
  if (isFirestoreAvailable && db) {
    try {
      const docRef = db.collection('users').doc();
      const userData = {
        ...user,
        id: docRef.id,
        uid: docRef.id,
        createdAt: new Date().toISOString()
      };
      await docRef.set(userData);
      return userData;
    } catch (e) {
      console.warn('[Firebase] Firestore create error, using memory store:', e.message);
    }
  }

  const newId = String(memoryStore.users.length + 1);
  const newUser = {
    ...user,
    id: newId,
    uid: newId,
    created_at: new Date().toISOString()
  };
  memoryStore.users.push(newUser);
  return newUser;
}

async function updateUser(id, updates) {
  const strId = String(id);
  if (isFirestoreAvailable && db) {
    try {
      await db.collection('users').doc(strId).set(updates, { merge: true });
    } catch (e) {
      console.warn('[Firebase] Firestore update error:', e.message);
    }
  }

  const u = memoryStore.users.find(x => String(x.id) === strId || String(x.uid) === strId);
  if (u) {
    Object.assign(u, updates);
  }
}

// Verification helper methods
async function saveVerification(data) {
  if (isFirestoreAvailable && db) {
    try {
      const ref = db.collection('email_verifications').doc();
      const verif = {
        ...data,
        id: ref.id,
        createdAt: new Date().toISOString()
      };
      await ref.set(verif);
      return verif;
    } catch (e) {
      console.warn('[Firebase] Firestore verification save error:', e.message);
    }
  }

  const newId = String(memoryStore.verifications.length + 1);
  const v = {
    ...data,
    id: newId,
    created_at: new Date().toISOString()
  };
  memoryStore.verifications.push(v);
  return v;
}

async function getLatestVerification(userId, email) {
  if (isFirestoreAvailable && db) {
    try {
      let q = db.collection('email_verifications');
      if (userId) {
        q = q.where('user_id', '==', String(userId));
      } else if (email) {
        q = q.where('email', '==', email.trim().toLowerCase());
      }
      const snap = await q.get();
      if (!snap.empty) {
        const sorted = snap.docs.sort((a, b) => new Date(b.data().createdAt || 0) - new Date(a.data().createdAt || 0));
        const doc = sorted[0];
        return { id: doc.id, ...doc.data() };
      }
    } catch (e) {
      console.warn('[Firebase] Firestore getLatestVerification error:', e.message);
    }
  }

  const matches = memoryStore.verifications.filter(v =>
    (userId && String(v.user_id) === String(userId)) ||
    (email && v.email && v.email.toLowerCase() === email.trim().toLowerCase())
  );
  return matches.length > 0 ? matches[matches.length - 1] : null;
}

async function updateVerification(id, updates) {
  const strId = String(id);
  if (isFirestoreAvailable && db) {
    try {
      await db.collection('email_verifications').doc(strId).set(updates, { merge: true });
    } catch (e) {
      console.warn('[Firebase] Firestore updateVerification error:', e.message);
    }
  }

  const v = memoryStore.verifications.find(x => String(x.id) === strId);
  if (v) {
    Object.assign(v, updates);
  }
}

// Product helper methods
async function getProducts(search, category) {
  let list = [];
  if (isFirestoreAvailable && db) {
    try {
      const snap = await db.collection('products').get();
      list = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    } catch (e) {
      console.warn('[Firebase] Firestore products error, falling back to memory store:', e.message);
      list = [...memoryStore.products];
    }
  } else {
    list = [...memoryStore.products];
  }

  if (search) {
    const s = search.toLowerCase();
    list = list.filter(p =>
      (p.title && p.title.toLowerCase().includes(s)) ||
      (p.description && p.description.toLowerCase().includes(s)) ||
      (p.category && p.category.toLowerCase().includes(s)) ||
      (p.department && p.department.toLowerCase().includes(s))
    );
  }

  if (category && category !== 'All') {
    list = list.filter(p => p.category === category);
  }

  return list;
}

async function getProductById(id) {
  const strId = String(id);
  if (isFirestoreAvailable && db) {
    try {
      const doc = await db.collection('products').doc(strId).get();
      if (doc.exists) {
        return { id: doc.id, ...doc.data() };
      }
    } catch (e) {
      console.warn('[Firebase] Firestore product by id error:', e.message);
    }
  }

  return memoryStore.products.find(p => String(p.id) === strId) || null;
}

// Rental helper methods
async function createRental(rental) {
  if (isFirestoreAvailable && db) {
    try {
      const ref = db.collection('rentals').doc();
      const docData = {
        ...rental,
        id: ref.id,
        createdAt: new Date().toISOString()
      };
      await ref.set(docData);
      return docData;
    } catch (e) {
      console.warn('[Firebase] Firestore rental create error:', e.message);
    }
  }

  const newId = String(memoryStore.rentals.length + 1);
  const r = {
    ...rental,
    id: newId,
    created_at: new Date().toISOString()
  };
  memoryStore.rentals.push(r);
  return r;
}

async function getUserRentals(userId) {
  const strId = String(userId);
  let list = [];
  if (isFirestoreAvailable && db) {
    try {
      const snap = await db.collection('rentals').where('renter_id', '==', strId).get();
      list = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    } catch (e) {
      console.warn('[Firebase] Firestore rentals get error:', e.message);
    }
  }

  if (list.length === 0) {
    list = memoryStore.rentals.filter(r => String(r.renter_id) === strId);
  }

  return list;
}

function withTimeout(promise, ms = 2000) {
  return Promise.race([
    promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error('RTDB timeout')), ms))
  ]);
}

async function incrementUnreadCounter(userId) {
  if (!userId || !rtdb) return;
  try {
    const ref = rtdb.ref(`unread/${userId}`);
    await withTimeout(ref.transaction(current => (current || 0) + 1), 2000);
  } catch (err) {
    console.warn(`[RTDB increment warning for user ${userId}]:`, err.message);
  }
}

async function resetUnreadCounter(userId) {
  if (!userId || !rtdb) return;
  try {
    const ref = rtdb.ref(`unread/${userId}`);
    await withTimeout(ref.set(0), 2000);
  } catch (err) {
    console.warn(`[RTDB reset warning for user ${userId}]:`, err.message);
  }
}

module.exports = {
  initFirebase,
  getDb: () => db,
  getRtdb: () => rtdb,
  isFirestoreAvailable: () => isFirestoreAvailable,
  incrementUnreadCounter,
  resetUnreadCounter,
  findUserByRollOrEmail,
  findUserById,
  createUser,
  updateUser,
  saveVerification,
  getLatestVerification,
  updateVerification,
  getProducts,
  getProductById,
  createRental,
  getUserRentals
};

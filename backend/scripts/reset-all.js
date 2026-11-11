#!/usr/bin/env node
'use strict';

/**
 * backend/scripts/reset-all.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Complete reset of test data in BorrowBox Firebase project:
 *  1. Exports full backup of all Firestore collections to backend/backups/
 *  2. Deletes all Firestore documents (all top-level collections found via listCollections(),
 *     including subcollections) using db.recursiveDelete.
 *  3. Deletes all Firebase Authentication users (paginated in batches of 1000)
 *  4. Cleans Realtime Database (/presence, /unread, root) only if database is used
 *  5. Skips Firebase Storage safely (Free Spark plan)
 *  6. Re-checks and verifies that all collections are empty
 */

const path = require('path');
const fs = require('fs');
require('dotenv').config({ path: path.join(__dirname, '../.env') });

const admin = require('firebase-admin');
const { getFirestore } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');
const { getDatabase } = require('firebase-admin/database');

// Find service account key
const keyCandidates = [
  path.join(__dirname, '../serviceAccountKey.json'),
  path.join(__dirname, '../../serviceAccountKey.json'),
  process.env.FIREBASE_SERVICE_ACCOUNT_KEY,
  process.env.GOOGLE_APPLICATION_CREDENTIALS
].filter(Boolean);

const foundKey = keyCandidates.find(p => fs.existsSync(p));
if (!foundKey) {
  console.error('❌ Service account key not found in backend/serviceAccountKey.json');
  process.exit(1);
}

const serviceAccount = JSON.parse(fs.readFileSync(foundKey, 'utf8'));

// Initialize Firebase Admin
if (admin.getApps().length === 0) {
  admin.initializeApp({
    credential: admin.cert(serviceAccount),
    databaseURL: process.env.FIREBASE_DATABASE_URL ||
      `https://${serviceAccount.project_id || 'borrowbox-13c4a'}-default-rtdb.firebaseio.com`
  });
}

const db = getFirestore();
const auth = getAuth();

/**
 * Format timestamp for backup filename: YYYY-MM-DD_HH-mm-ss
 */
function getTimestamp() {
  const now = new Date();
  return now.toISOString().replace(/T/, '_').replace(/:/g, '-').replace(/\..+/, '');
}

/**
 * Safely serialize Firestore document data for JSON backup
 */
function serializeDocData(data) {
  if (data === null || data === undefined) return data;
  if (typeof data.toDate === 'function') {
    return { _type: 'timestamp', value: data.toDate().toISOString() };
  }
  if (data instanceof Date) {
    return { _type: 'timestamp', value: data.toISOString() };
  }
  if (Array.isArray(data)) {
    return data.map(serializeDocData);
  }
  if (typeof data === 'object') {
    const res = {};
    for (const [key, val] of Object.entries(data)) {
      res[key] = serializeDocData(val);
    }
    return res;
  }
  return data;
}

async function runReset() {
  console.log('=======================================================');
  console.log('🗑️  BorrowBox Firebase Full Data Reset');
  console.log(`📦 Project ID: ${serviceAccount.project_id}`);
  console.log('=======================================================\n');

  // ─────────────────────────────────────────────────────────────
  // STEP 1: DISCOVER COLLECTIONS & EXPORT BACKUP
  // ─────────────────────────────────────────────────────────────
  console.log('📦 Step 1: Discovering collections & exporting backup...');
  const backupDir = path.join(__dirname, '../backups');
  if (!fs.existsSync(backupDir)) {
    fs.mkdirSync(backupDir, { recursive: true });
  }

  const timestamp = getTimestamp();
  const backupFile = path.join(backupDir, `backup-${timestamp}.json`);
  const backupData = {
    metadata: {
      projectId: serviceAccount.project_id,
      timestamp: new Date().toISOString(),
      backupFile: path.basename(backupFile)
    },
    collections: {}
  };

  const topCollections = await db.listCollections();
  // Ensure we check all dynamically listed collections plus standard collections
  const standardCollections = ['users', 'products', 'rentals', 'notifications', 'otps', 'email_verifications'];
  const collectionNames = Array.from(new Set([...topCollections.map(c => c.id), ...standardCollections]));

  let totalBackedUpDocs = 0;
  for (const colName of collectionNames) {
    const colRef = db.collection(colName);
    const snap = await colRef.get();
    backupData.collections[colName] = [];
    snap.forEach(doc => {
      backupData.collections[colName].push({
        id: doc.id,
        data: serializeDocData(doc.data())
      });
      totalBackedUpDocs++;
    });
    console.log(`   - ${colName}: ${snap.size} documents backed up`);
  }

  fs.writeFileSync(backupFile, JSON.stringify(backupData, null, 2), 'utf8');
  console.log(`✅ Backup saved successfully to: ${backupFile} (${totalBackedUpDocs} total documents)\n`);

  // ─────────────────────────────────────────────────────────────
  // STEP 2: DELETE FIRESTORE DOCUMENTS VIA recursiveDelete
  // ─────────────────────────────────────────────────────────────
  console.log('🔥 Step 2: Deleting all Firestore documents with db.recursiveDelete...');
  const deletedCounts = {};

  for (const colName of collectionNames) {
    const colRef = db.collection(colName);
    const snap = await colRef.get();
    const count = snap.size;
    deletedCounts[colName] = count;

    if (count === 0) {
      console.log(`   - ${colName}: 0 documents (already empty)`);
      continue;
    }

    console.log(`   - ${colName}: deleting ${count} documents (and subcollections)...`);
    await db.recursiveDelete(colRef);
  }
  console.log('✅ Firestore documents deletion complete.\n');

  // ─────────────────────────────────────────────────────────────
  // STEP 3: DELETE FIREBASE AUTH USERS (BATCHES OF 1000)
  // ─────────────────────────────────────────────────────────────
  console.log('👤 Step 3: Deleting Firebase Authentication users...');
  let totalAuthUsersDeleted = 0;
  let authStatus = 'OK';

  try {
    let pageToken;
    do {
      const listResult = await auth.listUsers(1000, pageToken);
      const uids = listResult.users.map(u => u.uid);
      if (uids.length > 0) {
        console.log(`   - Deleting batch of ${uids.length} Auth users...`);
        const deleteResult = await auth.deleteUsers(uids);
        totalAuthUsersDeleted += deleteResult.successCount;
        if (deleteResult.failureCount > 0) {
          console.warn(`   ⚠️ Failed to delete ${deleteResult.failureCount} users:`, deleteResult.errors);
        }
      }
      pageToken = listResult.pageToken;
    } while (pageToken);
    console.log(`✅ Firebase Auth users deleted: ${totalAuthUsersDeleted}`);
  } catch (authErr) {
    if (authErr.code === 'auth/configuration-not-found') {
      authStatus = 'Not configured in Firebase Console (0 users)';
      console.log(`   ℹ️ Firebase Auth: ${authStatus}`);
    } else {
      authStatus = `Notice: ${authErr.message}`;
      console.warn(`   ⚠️ Firebase Auth notice: ${authErr.message}`);
    }
  }
  console.log('');

  // ─────────────────────────────────────────────────────────────
  // STEP 4: CLEAN REALTIME DATABASE (ONLY IF USED)
  // ─────────────────────────────────────────────────────────────
  console.log('⚡ Step 4: Cleaning Realtime Database...');
  let rtdbStatus = 'Cleaned';
  try {
    const rtdb = getDatabase();
    const rtdbPromise = (async () => {
      await rtdb.ref('/presence').remove();
      await rtdb.ref('/unread').remove();
      await rtdb.ref().remove();
    })();

    const timeoutPromise = new Promise((_, reject) =>
      setTimeout(() => reject(new Error('RTDB connection timeout (not provisioned or unreachable)')), 3000)
    );

    await Promise.race([rtdbPromise, timeoutPromise]);
    console.log('✅ Realtime Database root and nodes (/presence, /unread) removed.');
  } catch (rtdbErr) {
    rtdbStatus = `Not provisioned or skipped (${rtdbErr.message})`;
    console.log(`   ℹ️ Realtime Database: ${rtdbStatus}`);
  }
  console.log('');

  // ─────────────────────────────────────────────────────────────
  // STEP 5: FIREBASE STORAGE (SKIPPED SAFELY ON SPARK PLAN)
  // ─────────────────────────────────────────────────────────────
  console.log('📁 Step 5: Firebase Storage: Skipped safely (Free Spark plan)\n');

  // ─────────────────────────────────────────────────────────────
  // STEP 6: VERIFY ALL COLLECTIONS ARE EMPTY
  // ─────────────────────────────────────────────────────────────
  console.log('🔍 Step 6: Verifying collections are completely empty...');
  const remainingCounts = {};
  let allClean = true;

  for (const colName of collectionNames) {
    const colRef = db.collection(colName);
    const snap = await colRef.get();
    remainingCounts[colName] = snap.size;
    if (snap.size > 0) {
      allClean = false;
      console.error(`   ❌ ${colName} still has ${snap.size} documents!`);
    } else {
      console.log(`   ✅ ${colName}: 0 documents (empty)`);
    }
  }

  console.log('\n=======================================================');
  console.log('📊 FINAL RESET SUMMARY');
  console.log('=======================================================');
  console.log(`📁 Backup File: ${backupFile}`);
  console.log('📋 Collections Cleared:');
  for (const [col, count] of Object.entries(deletedCounts)) {
    console.log(`   - ${col.padEnd(20)}: ${count} deleted → ${remainingCounts[col]} remaining`);
  }
  console.log(`👤 Auth Users Deleted : ${totalAuthUsersDeleted} (${authStatus})`);
  console.log(`⚡ Realtime Database   : ${rtdbStatus}`);
  console.log(`📁 Firebase Storage   : Skipped safely (Free Spark plan)`);
  console.log(`🏁 All Collections Empty: ${allClean ? 'YES ✅' : 'NO ❌'}`);
  console.log('=======================================================\n');

  if (!allClean) {
    process.exit(1);
  }
}

runReset()
  .then(() => {
    console.log('✨ Data reset completed successfully!');
    process.exit(0);
  })
  .catch(err => {
    console.error('💥 Fatal error during reset:', err);
    process.exit(1);
  });

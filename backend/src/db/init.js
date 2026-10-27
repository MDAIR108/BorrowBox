const mysql = require('mysql2/promise');
const fs = require('fs');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../../.env') });

async function initDatabase() {
  const dbName = process.env.DB_NAME || 'borrowbox';
  console.log(`[DB Init] Checking MySQL connection to host: ${process.env.DB_HOST || 'localhost'}:${process.env.DB_PORT || 3306}...`);

  // First connect without specifying database to create database if it doesn't exist
  let connection;
  try {
    connection = await mysql.createConnection({
      host: process.env.DB_HOST || 'localhost',
      port: parseInt(process.env.DB_PORT, 10) || 3306,
      user: process.env.DB_USER || 'root',
      password: process.env.DB_PASSWORD || '',
      multipleStatements: true
    });

    console.log(`[DB Init] Connected to MySQL. Ensuring database "${dbName}" exists...`);
    await connection.query(`CREATE DATABASE IF NOT EXISTS \`${dbName}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`);
    await connection.changeUser({ database: dbName });

    // Read schema.sql
    const schemaPath = path.join(__dirname, '../../schema.sql');
    if (fs.existsSync(schemaPath)) {
      const sql = fs.readFileSync(schemaPath, 'utf8');
      console.log(`[DB Init] Executing schema.sql migrations...`);
      await connection.query(sql);
      console.log(`[DB Init] Database "${dbName}" initialized successfully!`);
    } else {
      console.warn(`[DB Init] schema.sql not found at ${schemaPath}`);
    }
  } catch (err) {
    console.error(`[DB Init Error] Failed to initialize database:`, err.message);
    throw err;
  } finally {
    if (connection) {
      await connection.end();
    }
  }
}

if (require.main === module) {
  initDatabase()
    .then(() => process.exit(0))
    .catch((err) => {
      console.error(err);
      process.exit(1);
    });
}

module.exports = initDatabase;

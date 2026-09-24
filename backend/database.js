const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const bcrypt = require('bcrypt');
const crypto = require('crypto');

const dbPath = path.resolve(__dirname, 'munandy.db');
const db = new sqlite3.Database(dbPath, (err) => {
    if (err) {
        console.error('Error opening database', err.message);
    } else {
        console.log('Connected to the SQLite database.');
        // WAL mode allows concurrent reads during writes — safer under multi-user load
        db.run('PRAGMA journal_mode = WAL');
        db.run('PRAGMA busy_timeout = 5000');
        db.run('PRAGMA foreign_keys = ON');
        initSchema();
    }
});

function initSchema() {
    db.serialize(() => {
        // Territories
        db.run(`CREATE TABLE IF NOT EXISTS territories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            supervisor_id INTEGER
        )`);

        // Users
        db.run(`CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            email TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            role TEXT NOT NULL CHECK(role IN ('Admin', 'Supervisor', 'Salesman')),
            territory_id INTEGER,
            is_active INTEGER DEFAULT 1,
            FOREIGN KEY (territory_id) REFERENCES territories(id)
        )`);

        // Customers
        db.run(`CREATE TABLE IF NOT EXISTS customers (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            address TEXT,
            lat REAL,
            lng REAL,
            opening_time TEXT,
            closing_time TEXT,
            contact TEXT,
            preferred_products TEXT,
            notes TEXT,
            territory_id INTEGER,
            created_by INTEGER,
            FOREIGN KEY (territory_id) REFERENCES territories(id),
            FOREIGN KEY (created_by) REFERENCES users(id)
        )`);

        // Routes
        db.run(`CREATE TABLE IF NOT EXISTS routes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            date TEXT NOT NULL,
            created_by INTEGER,
            assigned_to INTEGER,
            status TEXT DEFAULT 'Draft',
            FOREIGN KEY (created_by) REFERENCES users(id),
            FOREIGN KEY (assigned_to) REFERENCES users(id)
        )`);

        // Route Stops
        db.run(`CREATE TABLE IF NOT EXISTS route_stops (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            route_id INTEGER,
            customer_id INTEGER,
            stop_order INTEGER,
            status TEXT DEFAULT 'Pending',
            arrived_at TEXT,
            completed_at TEXT,
            notes TEXT,
            skipped_reason TEXT,
            FOREIGN KEY (route_id) REFERENCES routes(id),
            FOREIGN KEY (customer_id) REFERENCES customers(id)
        )`);

        // Route deletion audit log
        db.run(`CREATE TABLE IF NOT EXISTS route_audit_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            route_id INTEGER NOT NULL,
            route_name TEXT,
            deleted_by INTEGER NOT NULL,
            deleted_at TEXT NOT NULL,
            FOREIGN KEY (deleted_by) REFERENCES users(id)
        )`);

        // Migrate: add pod_photo_url to route_stops if missing
        db.run(`ALTER TABLE route_stops ADD COLUMN pod_photo_url TEXT`, () => {
            // Silently ignore "duplicate column" error — means it already exists
        });

        // Driver Locations
        db.run(`CREATE TABLE IF NOT EXISTS driver_locations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER,
            lat REAL,
            lng REAL,
            timestamp TEXT,
            route_id INTEGER,
            FOREIGN KEY (user_id) REFERENCES users(id),
            FOREIGN KEY (route_id) REFERENCES routes(id)
        )`);

        // Create default admin if no users exist
        db.get("SELECT COUNT(*) as count FROM users", async (err, row) => {
            if (row && row.count === 0) {
                // Use env var for the initial password, or generate a random one
                const initialPassword = process.env.ADMIN_INITIAL_PASSWORD || crypto.randomBytes(8).toString('hex');
                const hash = await bcrypt.hash(initialPassword, 10);
                db.run(
                    `INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, ?)`,
                    ['Default Admin', 'admin@munandy.com', hash, 'Admin']
                );
                console.log('='.repeat(60));
                console.log('DEFAULT ADMIN ACCOUNT CREATED');
                console.log('  Email:    admin@munandy.com');
                console.log(`  Password: ${initialPassword}`);
                console.log('CHANGE THIS PASSWORD IMMEDIATELY AFTER FIRST LOGIN');
                console.log('Set ADMIN_INITIAL_PASSWORD env var to override this.');
                console.log('='.repeat(60));
            }
        });
    });
}

module.exports = db;

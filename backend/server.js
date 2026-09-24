require('dotenv').config({ path: require('path').resolve(__dirname, '.env'), override: true });
const express = require('express');
const cors = require('cors');
const rateLimit = require('express-rate-limit');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const fs = require('fs');
const path = require('path');
const https = require('https');
const multer = require('multer');
const db = require('./database');
const { authenticateToken, requireRole, SECRET_KEY } = require('./middleware/auth');

const app = express();

// CORS — restrict to configured origin in production, allow all in dev
const allowedOrigin = process.env.CORS_ORIGIN || '*';
app.use(cors({ origin: allowedOrigin }));

app.use(express.json({ limit: '10mb' })); // larger limit for base64 photos

// ── RATE LIMITING ─────────────────────────────────────────────────────────────
const loginLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,   // 15-minute window
    max: 20,                     // max 20 attempts per window
    standardHeaders: true,
    legacyHeaders: false,
    message: { error: 'Too many login attempts. Try again in 15 minutes.' }
});

const PORT = process.env.PORT || 3000;

// --- VALIDATION HELPERS ---
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
function isValidDate(s) {
    if (!DATE_RE.test(s)) return false;
    const d = new Date(s);
    return d instanceof Date && !isNaN(d);
}
function isValidLat(v) { return v == null || (typeof v === 'number' && v >= -90  && v <= 90); }
function isValidLng(v) { return v == null || (typeof v === 'number' && v >= -180 && v <= 180); }

// Serve uploaded POD photos
const uploadsDir = path.join(__dirname, 'uploads', 'pod');
if (!fs.existsSync(uploadsDir)) fs.mkdirSync(uploadsDir, { recursive: true });
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));

const podStorage = multer.diskStorage({
    destination: (req, file, cb) => cb(null, uploadsDir),
    filename: (req, file, cb) => {
        const ext = path.extname(file.originalname) || '.jpg';
        cb(null, `pod_${req.params.routeId}_${req.params.stopId}_${Date.now()}${ext}`);
    }
});
const uploadPod = multer({
    storage: podStorage,
    limits: { fileSize: 5 * 1024 * 1024 }, // 5 MB
    fileFilter: (req, file, cb) => {
        if (file.mimetype.startsWith('image/')) cb(null, true);
        else cb(new Error('Only image files allowed'));
    }
});

// ── ROUTE OPTIMIZATION ───────────────────────────────────────────────────────

function haversineKm(lat1, lon1, lat2, lon2) {
    const R = 6371;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) ** 2 +
        Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon / 2) ** 2;
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function nearestNeighbor(stops) {
    // stops = [{customer_id, lat, lng}]
    if (stops.length <= 1) return stops;
    const hasCoords = s => s.lat != null && s.lng != null && !(s.lat === 0 && s.lng === 0);
    const withCoords = stops.filter(hasCoords);
    const noCoords   = stops.filter(s => !hasCoords(s));
    if (withCoords.length <= 1) return [...withCoords, ...noCoords];

    const remaining = [...withCoords];
    const ordered   = [remaining.splice(0, 1)[0]];
    while (remaining.length > 0) {
        const last = ordered[ordered.length - 1];
        let nearestIdx = 0, nearestDist = Infinity;
        remaining.forEach((s, i) => {
            const d = haversineKm(last.lat, last.lng, s.lat, s.lng);
            if (d < nearestDist) { nearestDist = d; nearestIdx = i; }
        });
        ordered.push(remaining.splice(nearestIdx, 1)[0]);
    }
    return [...ordered, ...noCoords];
}

// ── WHATSAPP NOTIFICATIONS ───────────────────────────────────────────────────
// Requires environment variables: TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN
// Optional: TWILIO_WHATSAPP_FROM (defaults to Twilio sandbox number)

function sendWhatsAppNotification(toRaw, message) {
    const accountSid = process.env.TWILIO_ACCOUNT_SID;
    const authToken  = process.env.TWILIO_AUTH_TOKEN;
    if (!accountSid || !authToken || !toRaw) return;

    // Normalise Zimbabwe numbers to E.164
    let to = toRaw.replace(/[\s\-()]/g, '');
    if (to.startsWith('07'))      to = '+263' + to.slice(1);
    else if (to.startsWith('7'))  to = '+263' + to;
    else if (!to.startsWith('+')) to = '+' + to;

    const from    = process.env.TWILIO_WHATSAPP_FROM || '+14155238886';
    const body    = new URLSearchParams({ From: `whatsapp:${from}`, To: `whatsapp:${to}`, Body: message }).toString();
    const options = {
        hostname: 'api.twilio.com',
        path:     `/2010-04-01/Accounts/${accountSid}/Messages.json`,
        method:   'POST',
        headers:  {
            'Content-Type':  'application/x-www-form-urlencoded',
            'Authorization': 'Basic ' + Buffer.from(`${accountSid}:${authToken}`).toString('base64')
        }
    };
    const req = https.request(options, () => {});
    req.on('error', e => console.error('WhatsApp send failed:', e.message));
    req.write(body);
    req.end();
}

// --- AUTHENTICATION ---
app.post('/api/auth/login', loginLimiter, (req, res) => {
    const { email, password } = req.body;
    db.get(`SELECT * FROM users WHERE email = ?`, [email], async (err, user) => {
        if (err) return res.status(500).json({ error: 'Database error' });
        if (!user || user.is_active === 0) return res.status(401).json({ error: 'Invalid credentials or inactive account' });

        const validPassword = await bcrypt.compare(password, user.password_hash);
        if (!validPassword) return res.status(401).json({ error: 'Invalid credentials' });

        const token = jwt.sign({ id: user.id, role: user.role, name: user.name }, SECRET_KEY, { expiresIn: '24h' });
        res.json({ token, user: { id: user.id, name: user.name, role: user.role, email: user.email } });
    });
});

// --- AUTH: verify token ---
app.get('/api/me', authenticateToken, (req, res) => {
    db.get(`SELECT id, name, email, role FROM users WHERE id = ? AND is_active = 1`,
        [req.user.id], (err, user) => {
            if (err || !user) return res.status(401).json({ error: 'User not found or inactive' });
            res.json(user);
        });
});

// --- ADMIN / USERS ---
app.get('/api/users', authenticateToken, requireRole(['Admin', 'Supervisor']), (req, res) => {
    db.all(`SELECT id, name, email, role, territory_id, is_active FROM users`, [], (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });
        res.json(rows.map(r => ({ ...r, is_active: r.is_active === 1 })));
    });
});

app.post('/api/users', authenticateToken, requireRole(['Admin']), async (req, res) => {
    const { name, email, password, role, territory_id } = req.body;
    const validRoles = ['Admin', 'Supervisor', 'Salesman'];
    if (!name || !email || !password || !role) {
        return res.status(400).json({ error: 'name, email, password and role are required' });
    }
    if (!validRoles.includes(role)) {
        return res.status(400).json({ error: 'role must be one of: ' + validRoles.join(', ') });
    }
    try {
        const hash = await bcrypt.hash(password, 10);
        db.run(`INSERT INTO users (name, email, password_hash, role, territory_id) VALUES (?, ?, ?, ?, ?)`,
            [name, email, hash, role, territory_id], function(err) {
                if (err) return res.status(500).json({ error: err.message });
                res.json({ id: this.lastID, name, email, role });
        });
    } catch(err) {
        res.status(500).json({ error: 'Failed to hash password' });
    }
});

app.put('/api/users/:id/status', authenticateToken, requireRole(['Admin']), (req, res) => {
    const { is_active } = req.body;
    db.run(`UPDATE users SET is_active = ? WHERE id = ?`, [is_active ? 1 : 0, req.params.id], function(err) {
        if (err) return res.status(500).json({ error: err.message });
        res.json({ success: true });
    });
});

// --- SUPERVISOR / CUSTOMERS ---
app.get('/api/customers', authenticateToken, requireRole(['Admin', 'Supervisor', 'Salesman']), (req, res) => {
    if (req.user.role === 'Admin') {
        db.all(`SELECT * FROM customers`, [], (err, rows) => {
            if (err) return res.status(500).json({ error: err.message });
            res.json(rows);
        });
    } else {
        db.get(`SELECT territory_id FROM users WHERE id = ?`, [req.user.id], (err, user) => {
            if (err) return res.status(500).json({ error: err.message });
            const query = (!user || !user.territory_id)
                ? { sql: `SELECT * FROM customers`, params: [] }
                : { sql: `SELECT * FROM customers WHERE territory_id = ?`, params: [user.territory_id] };
            db.all(query.sql, query.params, (err, rows) => {
                if (err) return res.status(500).json({ error: err.message });
                res.json(rows);
            });
        });
    }
});

app.post('/api/customers', authenticateToken, requireRole(['Admin', 'Supervisor']), (req, res) => {
    const { name, address, lat, lng, opening_time, closing_time, contact, preferred_products, notes, territory_id } = req.body;
    if (!name || !name.trim()) return res.status(400).json({ error: 'name is required' });
    if (!isValidLat(lat)) return res.status(400).json({ error: 'lat must be between -90 and 90' });
    if (!isValidLng(lng)) return res.status(400).json({ error: 'lng must be between -180 and 180' });
    db.run(`INSERT INTO customers (name, address, lat, lng, opening_time, closing_time, contact, preferred_products, notes, territory_id, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [name, address, lat, lng, opening_time, closing_time, contact, preferred_products, notes, territory_id, req.user.id], function(err) {
            if (err) return res.status(500).json({ error: err.message });
            res.json({ id: this.lastID, name });
    });
});

// --- STATS ---
app.get('/api/stats', authenticateToken, requireRole(['Admin', 'Supervisor']), (req, res) => {
    db.get(`
        SELECT
            (SELECT COUNT(*) FROM routes WHERE status = 'Active') AS active_routes,
            (SELECT COUNT(DISTINCT user_id) FROM driver_locations dl
             JOIN routes r ON dl.route_id = r.id WHERE r.status = 'Active') AS active_drivers,
            (SELECT COUNT(*) FROM route_stops WHERE status = 'Completed') AS stops_completed,
            (SELECT COUNT(*) FROM route_stops WHERE status = 'Pending')   AS stops_pending
    `, [], (err, row) => {
        if (err) return res.status(500).json({ error: err.message });
        res.json(row);
    });
});

// --- SUPERVISOR / ROUTES ---
app.get('/api/routes', authenticateToken, (req, res) => {
    let query = `
        SELECT r.*, u.name AS assigned_to_name
        FROM routes r
        LEFT JOIN users u ON r.assigned_to = u.id
    `;
    let params = [];
    if (req.user.role === 'Salesman') {
        query += ` WHERE r.assigned_to = ?`;
        params.push(req.user.id);
    } else if (req.user.role === 'Supervisor') {
        query += ` WHERE r.created_by = ?`;
        params.push(req.user.id);
    }
    db.all(query, params, (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });
        res.json(rows);
    });
});

app.post('/api/routes', authenticateToken, requireRole(['Admin', 'Supervisor']), (req, res) => {
    const { name, date, assigned_to } = req.body;
    if (!name || !name.trim()) return res.status(400).json({ error: 'name is required' });
    if (!date) return res.status(400).json({ error: 'date is required' });
    if (!isValidDate(date.trim())) return res.status(400).json({ error: 'date must be YYYY-MM-DD' });
    db.run(`INSERT INTO routes (name, date, created_by, assigned_to, status) VALUES (?, ?, ?, ?, 'Draft')`,
        [name, date, req.user.id, assigned_to], function(err) {
            if (err) return res.status(500).json({ error: err.message });
            res.json({ id: this.lastID, name });
    });
});

// POST /api/routes/:id/stops — saves stops in nearest-neighbor optimized order
app.post('/api/routes/:id/stops', authenticateToken, requireRole(['Admin', 'Supervisor']), (req, res) => {
    const { stops } = req.body;
    const route_id = req.params.id;

    if (!stops || !Array.isArray(stops) || stops.length === 0) {
        return res.status(400).json({ error: 'stops must be a non-empty array' });
    }

    const customerIds = stops.map(s => s.customer_id);
    const placeholders = customerIds.map(() => '?').join(',');

    // Fetch lat/lng for all customers to apply nearest-neighbour optimisation
    db.all(`SELECT id, lat, lng FROM customers WHERE id IN (${placeholders})`, customerIds, (err, customers) => {
        if (err) return res.status(500).json({ error: err.message });

        const stopsWithCoords = stops.map(s => {
            const c = customers.find(c => c.id === s.customer_id);
            return { customer_id: s.customer_id, lat: c ? c.lat : null, lng: c ? c.lng : null };
        });

        const optimized = nearestNeighbor(stopsWithCoords);

        db.run('BEGIN', (err) => {
            if (err) return res.status(500).json({ error: err.message });

            db.run(`DELETE FROM route_stops WHERE route_id = ?`, [route_id], (err) => {
                if (err) { db.run('ROLLBACK'); return res.status(500).json({ error: err.message }); }

                let insertError = null;
                const stmt = db.prepare(`INSERT INTO route_stops (route_id, customer_id, stop_order) VALUES (?, ?, ?)`);
                optimized.forEach((stop, idx) => {
                    stmt.run([route_id, stop.customer_id, idx + 1], (e) => { if (e) insertError = e; });
                });
                stmt.finalize((err) => {
                    if (err || insertError) {
                        db.run('ROLLBACK');
                        return res.status(500).json({ error: (err || insertError).message });
                    }
                    db.run('COMMIT', (err) => {
                        if (err) return res.status(500).json({ error: err.message });
                        res.json({ success: true, optimized: true, stop_count: optimized.length });
                    });
                });
            });
        });
    });
});

// --- ROUTE STATUS UPDATE ---
app.patch('/api/routes/:id/status', authenticateToken, (req, res) => {
    const { status } = req.body;
    const validStatuses = ['Draft', 'Active', 'Complete', 'Inactive'];
    if (!status || !validStatuses.includes(status)) {
        return res.status(400).json({ error: 'Invalid status. Must be one of: ' + validStatuses.join(', ') });
    }

    const role = req.user.role;

    if (role === 'Salesman') {
        // Salesmen may only mark their own assigned route as Complete
        if (status !== 'Complete') {
            return res.status(403).json({ error: 'Salesmen can only mark a route as Complete' });
        }
        db.get(`SELECT id FROM routes WHERE id = ? AND assigned_to = ?`,
            [req.params.id, req.user.id], (err, route) => {
                if (err) return res.status(500).json({ error: err.message });
                if (!route) return res.status(403).json({ error: 'You are not assigned to this route' });
                db.run(`UPDATE routes SET status = ? WHERE id = ?`, [status, req.params.id], function(err) {
                    if (err) return res.status(500).json({ error: err.message });
                    res.json({ id: req.params.id, status });
                });
            }
        );
    } else {
        // Admin / Supervisor: full access
        db.run(`UPDATE routes SET status = ? WHERE id = ?`, [status, req.params.id], function(err) {
            if (err) return res.status(500).json({ error: err.message });
            if (this.changes === 0) return res.status(404).json({ error: 'Route not found' });
            res.json({ id: req.params.id, status });
        });
    }
});

// --- ROUTE ASSIGN ---
app.patch('/api/routes/:id/assign', authenticateToken, requireRole(['Admin', 'Supervisor']), (req, res) => {
    const { assigned_to } = req.body;

    const doAssign = (userId) => db.run(`UPDATE routes SET assigned_to = ? WHERE id = ?`,
        [userId, req.params.id], function(err) {
            if (err) return res.status(500).json({ error: err.message });
            if (this.changes === 0) return res.status(404).json({ error: 'Route not found' });
            res.json({ success: true });
        }
    );

    if (assigned_to == null) {
        doAssign(null);
    } else {
        // Ensure assigned_to is an active Salesman
        db.get(`SELECT id FROM users WHERE id = ? AND role = 'Salesman' AND is_active = 1`,
            [assigned_to], (err, user) => {
                if (err) return res.status(500).json({ error: err.message });
                if (!user) return res.status(400).json({ error: 'assigned_to must be an active Salesman' });
                doAssign(assigned_to);
            }
        );
    }
});

// --- DELETE ROUTE ---
app.delete('/api/routes/:id', authenticateToken, requireRole(['Admin', 'Supervisor']), (req, res) => {
    const routeId = req.params.id;
    console.log(`[DELETE] Route ${routeId} by user ${req.user.id} (${req.user.role})`);
    // Fetch route for audit log + ownership check before deleting
    db.get(`SELECT name, created_by FROM routes WHERE id = ?`, [routeId], (err, route) => {
        if (err || !route) return res.status(404).json({ error: 'Route not found' });
        // Supervisors may only delete routes they created
        if (req.user.role === 'Supervisor' && route.created_by !== req.user.id) {
            return res.status(403).json({ error: 'Supervisors can only delete routes they created' });
        }
        const now = new Date().toISOString();
        db.run(`INSERT INTO route_audit_log (route_id, route_name, deleted_by, deleted_at) VALUES (?, ?, ?, ?)`,
            [routeId, route.name, req.user.id, now]);
        db.run(`DELETE FROM route_stops WHERE route_id = ?`, [routeId], (err) => {
            if (err) return res.status(500).json({ error: err.message });
            db.run(`DELETE FROM routes WHERE id = ?`, [routeId], function(err) {
                if (err) return res.status(500).json({ error: err.message });
                if (this.changes === 0) return res.status(404).json({ error: 'Route not found' });
                console.log(`[DELETE] Route "${route.name}" (${routeId}) deleted by user ${req.user.id}`);
                res.json({ success: true });
            });
        });
    });
});

// --- SALESMAN / OFFLINE SYNC ---
app.get('/api/sync/routes/:id', authenticateToken, (req, res) => {
    const route_id = req.params.id;

    const doSync = () => db.all(`
        SELECT rs.*,
               c.name,
               COALESCE(c.address, '') AS address,
               COALESCE(c.lat, 0)     AS lat,
               COALESCE(c.lng, 0)     AS lng,
               c.opening_time, c.closing_time, c.contact, c.preferred_products
        FROM route_stops rs
        JOIN customers c ON rs.customer_id = c.id
        WHERE rs.route_id = ? ORDER BY rs.stop_order ASC
    `, [route_id], (err, rows) => {
        if (err) return res.status(500).json({ error: 'Database error' });
        res.json({ route_id, stops: rows });
    });

    if (req.user.role === 'Salesman') {
        // Salesmen may only sync routes assigned to them
        db.get(`SELECT id FROM routes WHERE id = ? AND assigned_to = ?`,
            [route_id, req.user.id], (err, route) => {
                if (err) return res.status(500).json({ error: 'Database error' });
                if (!route) return res.status(403).json({ error: 'Access denied' });
                doSync();
            }
        );
    } else {
        doSync();
    }
});

// --- BACKGROUND TRACKING ---
app.post('/api/tracking/locations', authenticateToken, requireRole(['Salesman']), (req, res) => {
    const { lat, lng, timestamp, route_id } = req.body;
    if (lat === undefined || lng === undefined || !timestamp || !route_id) {
        return res.status(400).json({ error: 'lat, lng, timestamp and route_id are required' });
    }
    // Verify the route is actually assigned to this salesman
    db.get(`SELECT id FROM routes WHERE id = ? AND assigned_to = ?`, [route_id, req.user.id], (err, route) => {
        if (err) return res.status(500).json({ error: 'Database error' });
        if (!route) return res.status(403).json({ error: 'Access denied' });
        db.run(`INSERT INTO driver_locations (user_id, lat, lng, timestamp, route_id) VALUES (?, ?, ?, ?, ?)`,
            [req.user.id, lat, lng, timestamp, route_id], function(err) {
                if (err) return res.status(500).json({ error: 'Database error' });
                res.json({ success: true });
        });
    });
});

// --- LIVE TRACKING: latest location per active driver ---
app.get('/api/tracking/locations/latest', authenticateToken, requireRole(['Admin', 'Supervisor']), (req, res) => {
    db.all(`
        SELECT dl.user_id, u.name AS user_name, dl.lat, dl.lng, dl.timestamp, dl.route_id, r.name AS route_name
        FROM driver_locations dl
        JOIN users u ON dl.user_id = u.id
        JOIN routes r ON dl.route_id = r.id
        WHERE r.status = 'Active'
          AND dl.id = (
              SELECT MAX(dl2.id) FROM driver_locations dl2
              WHERE dl2.user_id = dl.user_id
          )
        ORDER BY u.name ASC
    `, [], (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });
        res.json(rows);
    });
});

// --- DELETE INDIVIDUAL STOP ---
app.delete('/api/routes/:routeId/stops/:stopId', authenticateToken, requireRole(['Admin', 'Supervisor']), (req, res) => {
    const { routeId, stopId } = req.params;
    db.run(`DELETE FROM route_stops WHERE id = ? AND route_id = ?`, [stopId, routeId], function(err) {
        if (err) return res.status(500).json({ error: err.message });
        if (this.changes === 0) return res.status(404).json({ error: 'Stop not found' });
        res.json({ success: true });
    });
});

// --- STOP STATUS UPDATE ---
app.patch('/api/routes/:routeId/stops/:stopId/status', authenticateToken, (req, res) => {
    const { status, notes, skipped_reason, arrived_at, completed_at } = req.body;
    const validStatuses = ['Pending', 'Arrived', 'Completed', 'Skipped'];
    if (!status || !validStatuses.includes(status)) {
        return res.status(400).json({ error: 'Invalid status. Must be one of: ' + validStatuses.join(', ') });
    }

    const doUpdate = () => db.run(`UPDATE route_stops
            SET status = ?, notes = ?, skipped_reason = ?, arrived_at = ?, completed_at = ?
            WHERE id = ? AND route_id = ?`,
        [status, notes || null, skipped_reason || null, arrived_at || null, completed_at || null,
         req.params.stopId, req.params.routeId],
        function(err) {
            if (err) return res.status(500).json({ error: 'Database error' });
            if (this.changes === 0) return res.status(404).json({ error: 'Stop not found' });

            // Fire WhatsApp notification on Arrived or Completed
            if (status === 'Arrived' || status === 'Completed') {
                db.get(`SELECT c.contact, c.name FROM route_stops rs
                        JOIN customers c ON rs.customer_id = c.id
                        WHERE rs.id = ? AND rs.route_id = ?`,
                    [req.params.stopId, req.params.routeId], (err, row) => {
                        if (!err && row && row.contact) {
                            const msg = status === 'Arrived'
                                ? `Hello ${row.name}, the Munandy Foods delivery team has arrived at your location.`
                                : `Hello ${row.name}, your Munandy Foods delivery has been completed. Thank you!`;
                            sendWhatsAppNotification(row.contact, msg);
                        }
                    }
                );
            }

            res.json({ success: true });
        }
    );

    if (req.user.role === 'Salesman') {
        // Salesmen may only update stops on routes assigned to them
        db.get(`SELECT r.id FROM routes r
                JOIN route_stops rs ON rs.route_id = r.id
                WHERE rs.id = ? AND rs.route_id = ? AND r.assigned_to = ?`,
            [req.params.stopId, req.params.routeId, req.user.id], (err, result) => {
                if (err) return res.status(500).json({ error: 'Database error' });
                if (!result) return res.status(403).json({ error: 'Access denied' });
                doUpdate();
            }
        );
    } else {
        doUpdate();
    }
});

// --- EDIT CUSTOMER ---
app.put('/api/customers/:id', authenticateToken, requireRole(['Admin', 'Supervisor']), (req, res) => {
    const { name, address, lat, lng, opening_time, closing_time, contact, preferred_products, notes } = req.body;
    if (!name || !name.trim()) return res.status(400).json({ error: 'name is required' });
    if (!isValidLat(lat)) return res.status(400).json({ error: 'lat must be between -90 and 90' });
    if (!isValidLng(lng)) return res.status(400).json({ error: 'lng must be between -180 and 180' });
    db.run(`UPDATE customers SET name=?, address=?, lat=?, lng=?, opening_time=?, closing_time=?, contact=?, preferred_products=?, notes=? WHERE id=?`,
        [name, address ?? null, lat ?? null, lng ?? null, opening_time ?? null, closing_time ?? null,
         contact ?? null, preferred_products ?? null, notes ?? null, req.params.id],
        function(err) {
            if (err) return res.status(500).json({ error: err.message });
            if (this.changes === 0) return res.status(404).json({ error: 'Customer not found' });
            res.json({ success: true });
        }
    );
});

// --- DELETE CUSTOMER ---
app.delete('/api/customers/:id', authenticateToken, requireRole(['Admin', 'Supervisor']), (req, res) => {
    db.run(`DELETE FROM customers WHERE id = ?`, [req.params.id], function(err) {
        if (err) return res.status(500).json({ error: err.message });
        if (this.changes === 0) return res.status(404).json({ error: 'Customer not found' });
        res.json({ success: true });
    });
});

// --- CHANGE OWN PASSWORD ---
app.patch('/api/users/me/password', authenticateToken, async (req, res) => {
    const { current_password, new_password } = req.body;
    if (!current_password || !new_password) {
        return res.status(400).json({ error: 'current_password and new_password are required' });
    }
    if (new_password.length < 6) {
        return res.status(400).json({ error: 'new_password must be at least 6 characters' });
    }
    db.get('SELECT * FROM users WHERE id = ?', [req.user.id], async (err, user) => {
        if (err || !user) return res.status(500).json({ error: 'User not found' });
        const valid = await bcrypt.compare(current_password, user.password_hash);
        if (!valid) return res.status(401).json({ error: 'Current password is incorrect' });
        try {
            const newHash = await bcrypt.hash(new_password, 10);
            db.run('UPDATE users SET password_hash = ? WHERE id = ?', [newHash, req.user.id], function(err) {
                if (err) return res.status(500).json({ error: err.message });
                res.json({ success: true });
            });
        } catch(e) { res.status(500).json({ error: 'Failed to hash password' }); }
    });
});

// --- ADMIN: RESET USER PASSWORD ---
app.patch('/api/users/:id/reset-password', authenticateToken, requireRole(['Admin']), async (req, res) => {
    const { new_password } = req.body;
    if (!new_password) return res.status(400).json({ error: 'new_password is required' });
    if (new_password.length < 6) return res.status(400).json({ error: 'new_password must be at least 6 characters' });
    try {
        const hash = await bcrypt.hash(new_password, 10);
        db.run('UPDATE users SET password_hash = ? WHERE id = ?', [hash, req.params.id], function(err) {
            if (err) return res.status(500).json({ error: err.message });
            if (this.changes === 0) return res.status(404).json({ error: 'User not found' });
            res.json({ success: true });
        });
    } catch(e) { res.status(500).json({ error: 'Failed to hash password' }); }
});

// --- PROOF OF DELIVERY (multipart/form-data) ---
app.post('/api/routes/:routeId/stops/:stopId/pod', authenticateToken,
    (req, res, next) => uploadPod.single('photo')(req, res, (err) => {
        if (err) return res.status(400).json({ error: err.message });
        next();
    }),
    (req, res) => {
        if (!req.file) return res.status(400).json({ error: 'photo file is required' });
        const photoUrl = `/uploads/pod/${req.file.filename}`;
        db.run(`UPDATE route_stops SET pod_photo_url = ? WHERE id = ? AND route_id = ?`,
            [photoUrl, req.params.stopId, req.params.routeId],
            function(err) {
                if (err) return res.status(500).json({ error: err.message });
                res.json({ success: true, photo_url: photoUrl });
            }
        );
    }
);

// --- REPORTS ---
app.get('/api/reports/summary', authenticateToken, requireRole(['Admin', 'Supervisor']), (req, res) => {
    const result = {};

    db.all(`SELECT status, COUNT(*) as count FROM routes GROUP BY status`, [], (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });
        rows.forEach(r => { result[r.status.toLowerCase() + '_routes'] = r.count; });

        db.all(`SELECT status, COUNT(*) as count FROM route_stops GROUP BY status`, [], (err, rows) => {
            if (err) return res.status(500).json({ error: err.message });
            rows.forEach(r => { result[r.status.toLowerCase() + '_stops'] = r.count; });

            db.get(`
                SELECT ROUND(AVG(
                    (JULIANDAY(completed_at) - JULIANDAY(arrived_at)) * 24 * 60
                )) AS avg_minutes
                FROM route_stops
                WHERE status = 'Completed'
                  AND arrived_at IS NOT NULL
                  AND completed_at IS NOT NULL
            `, [], (err, row) => {
                if (err) return res.status(500).json({ error: err.message });
                result.avg_stop_duration_minutes = row ? row.avg_minutes : null;

                db.all(`
                    SELECT u.name,
                        COUNT(DISTINCT r.id)                                          AS routes_assigned,
                        SUM(CASE WHEN rs.status = 'Completed' THEN 1 ELSE 0 END)     AS stops_completed,
                        SUM(CASE WHEN rs.status = 'Skipped'   THEN 1 ELSE 0 END)     AS stops_skipped,
                        COUNT(rs.id)                                                  AS stops_total
                    FROM users u
                    LEFT JOIN routes r ON r.assigned_to = u.id
                    LEFT JOIN route_stops rs ON rs.route_id = r.id
                    WHERE u.role = 'Salesman'
                    GROUP BY u.id, u.name
                    ORDER BY u.name
                `, [], (err, rows) => {
                    if (err) return res.status(500).json({ error: err.message });
                    result.salesmen = rows || [];
                    res.json(result);
                });
            });
        });
    });
});

// ── STARTUP CLEANUP ───────────────────────────────────────────────────────────
// driver_locations would grow forever without periodic pruning; trim on boot.
db.run(`DELETE FROM driver_locations WHERE timestamp < datetime('now', '-7 days')`, (err) => {
    if (err) console.error('Location cleanup error:', err.message);
    else console.log('Old driver locations pruned (>7 days)');
});

app.listen(PORT, () => {
    console.log(`Munandy Backend running on port ${PORT}`);
});

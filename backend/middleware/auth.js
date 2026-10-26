const jwt = require('jsonwebtoken');

const SECRET_KEY = process.env.JWT_SECRET || (() => {
    if (process.env.NODE_ENV === 'production') {
        console.error('FATAL: JWT_SECRET environment variable must be set in production.');
        process.exit(1);
    }
    console.warn('[DEV] JWT_SECRET not set — using insecure default. Set JWT_SECRET env var before deploying.');
    return 'munandy_super_secret_key_2026';
})();

function authenticateToken(req, res, next) {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];

    if (!token) return res.sendStatus(401);

    jwt.verify(token, SECRET_KEY, (err, user) => {
        // Expired or invalid token = not authenticated (401), so the app sends the user back
        // to the login screen. 403 is reserved for "logged in but not allowed" (requireRole).
        if (err) return res.status(401).json({ error: 'Session expired. Please log in again.' });
        req.user = user;
        next();
    });
}

function requireRole(roles) {
    return (req, res, next) => {
        if (!req.user || !roles.includes(req.user.role)) {
            return res.status(403).json({ error: 'Access denied: insufficient permissions' });
        }
        next();
    }
}

module.exports = { authenticateToken, requireRole, SECRET_KEY };

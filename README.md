# Munandy Reach

A logistics / route-management app for Munandy Foods: an Android app for drivers and managers, backed by a Node.js API. Admins and supervisors build delivery routes and assign them to salesmen, who navigate stops, capture proof-of-delivery, and share live location.

## Architecture

| Layer | Stack |
|-------|-------|
| **Android app** (`app/`) | Kotlin, Jetpack Compose, Material 3, MVVM, Navigation Compose, Retrofit + kotlinx.serialization, Room, DataStore, Google Maps Compose |
| **Backend** (`backend/`) | Node.js, Express, SQLite (WAL), JWT auth, bcrypt, PM2, express-rate-limit |
| **Mockups** (`screens/`) | Static HTML prototypes of each screen |

### Roles
- **Admin / Supervisor** — build & assign routes, manage customers and users, view all routes and live tracking
- **Salesman** — see assigned routes, navigate stops, update stop status, capture proof-of-delivery, share GPS

## Backend setup

```bash
cd backend
npm install
cp .env.example .env      # then edit .env with real values
node server.js            # or: pm2 start ecosystem.config.js
```

The API listens on `http://<host>:3000/api/`. SQLite data lives in `backend/munandy.db` (created on first run; not committed).

To host on a VPS behind HTTPS (nginx + Let's Encrypt + PM2), follow **[backend/DEPLOYMENT.md](backend/DEPLOYMENT.md)**.

## Android setup

1. Open the project root in Android Studio.
2. Create `local.properties` in the project root with your SDK path, Maps key,
   and the API base URLs:
   ```
   sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   MAPS_API_KEY=your_google_maps_api_key
   # Debug builds -> your local dev server (update the IP when it changes)
   LOCAL_API_BASE=http://192.168.1.103:3000/api/
   # Release builds -> your VPS over HTTPS (optional here; falls back to the placeholder in build.gradle.kts)
   # PROD_API_BASE=https://api.munandyfoods.co.zw/api/
   ```
3. Build & run a debug build against the local server:
   ```bash
   ./gradlew installDebug
   ```

**API endpoint per build type** (wired up in `app/build.gradle.kts`):

| Build | Points at | Cleartext HTTP |
|-------|-----------|----------------|
| `debug` | `LOCAL_API_BASE` (local dev server) | allowed |
| `release` | `PROD_API_BASE` / VPS (HTTPS) | blocked |

Release builds enforce HTTPS via the network security config, so the VPS must
serve TLS — see [backend/DEPLOYMENT.md](backend/DEPLOYMENT.md).

## Not committed (create locally)

- `backend/.env` — secrets (JWT secret, Twilio). Template: `backend/.env.example`
- `backend/munandy.db*` — SQLite database (contains user data & password hashes)
- `backend/uploads/` — proof-of-delivery photos
- `local.properties` — SDK path + Maps API key

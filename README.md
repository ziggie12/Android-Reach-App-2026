# Munandy Drive

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

## Android setup

1. Open the project root in Android Studio.
2. Create `local.properties` in the project root with your SDK path and Maps key:
   ```
   sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   MAPS_API_KEY=your_google_maps_api_key
   ```
3. Set the backend URL in `app/build.gradle.kts` (`BASE_URL` under `buildTypes`) to your server's address.
4. Build & run:
   ```bash
   ./gradlew installDebug
   ```

## Not committed (create locally)

- `backend/.env` — secrets (JWT secret, Twilio). Template: `backend/.env.example`
- `backend/munandy.db*` — SQLite database (contains user data & password hashes)
- `backend/uploads/` — proof-of-delivery photos
- `local.properties` — SDK path + Maps API key

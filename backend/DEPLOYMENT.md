# Munandy Reach — VPS Deployment

How to host the backend on a VPS (Ubuntu 22.04+ assumed) behind HTTPS. This
replaces the old "run it on a PC on the LAN" setup. The Android **release**
build points at `https://<your-domain>/api/`.

> **Why HTTPS matters:** the app sends login passwords and JWT tokens on every
> request. On the public internet these must be encrypted. The steps below put
> nginx in front of Node and get a free Let's Encrypt certificate.

---

## 0. Before you start

- A VPS with a public IP and SSH access.
- A domain (or subdomain) you can add a DNS record to, e.g. `api.munandyfoods.co.zw`.
  Point an **A record** at the VPS's IP and wait for it to resolve.
- The repo pushed to GitHub (already done).

## 1. Base packages

```bash
sudo apt update && sudo apt upgrade -y
# Node.js LTS
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs git nginx
sudo npm install -g pm2
```

## 2. Get the code + install deps

```bash
sudo mkdir -p /opt/munandy && sudo chown $USER:$USER /opt/munandy
git clone https://github.com/ziggie12/Android-Reach-App-2026.git /opt/munandy
cd /opt/munandy/backend
npm ci --omit=dev
```

## 3. Configure environment

```bash
cp .env.example .env
nano .env
```

Set, at minimum:

```
NODE_ENV=production
PORT=3000
# Generate a strong secret, e.g.:  node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"
JWT_SECRET=<long-random-string>
# The app is native (no browser), so CORS can stay * unless you add a web dashboard.
CORS_ORIGIN=*
```

> The SQLite database (`munandy.db`) is created automatically on first run and
> lives in this folder. It is **not** in git — it holds real user data. Back it up
> (see §7).

## 4. Start with PM2 (auto-restart + boot on reboot)

```bash
pm2 start ecosystem.config.js
pm2 save
pm2 startup    # run the command it prints, then `pm2 save` again
```

Node now listens on `127.0.0.1:3000`. Do **not** open port 3000 to the world —
nginx will be the only public entry point.

## 5. nginx reverse proxy

```bash
sudo nano /etc/nginx/sites-available/munandy
```

```nginx
server {
    listen 80;
    server_name api.munandyfoods.co.zw;   # <-- your domain

    client_max_body_size 12m;             # POD photos are base64 / multipart

    location / {
        proxy_pass         http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/munandy /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

## 6. HTTPS with Let's Encrypt

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.munandyfoods.co.zw
```

Certbot edits the nginx config to add TLS and sets up auto-renewal. Your API is
now at `https://api.munandyfoods.co.zw/api/`.

## 7. Firewall + backups

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'   # 80 + 443 only; 3000 stays private
sudo ufw enable
```

Back up the database on a schedule (SQLite is a single file):

```bash
# safe hot copy
sqlite3 /opt/munandy/backend/munandy.db ".backup '/opt/munandy/backups/munandy-$(date +\%F).db'"
```

## 8. Point the app at the VPS

In `local.properties` on your build machine (or the release build config):

```
PROD_API_BASE=https://api.munandyfoods.co.zw/api/
```

Then build the release: `./gradlew assembleRelease` (or `bundleRelease` for Play).

## Updating later

```bash
cd /opt/munandy && git pull
cd backend && npm ci --omit=dev
pm2 restart munandy-backend
```

## Health check

```bash
curl -i https://api.munandyfoods.co.zw/api/   # should reach the Node app via TLS
pm2 logs munandy-backend                       # tail server logs
```

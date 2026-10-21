# Munandy Reach — Server Deployment

The backend runs in production at **`https://reach.munandy.co.zw/api/`**. The
Android **release** build points there by default.

## The live setup

| Item | Value |
|------|-------|
| Server | AWS Lightsail, Bitnami stack (Debian 12), `35.178.95.155` |
| SSH user | `bitnami` (use `sudo` for admin commands; there is no root login) |
| Web server | Apache (Bitnami), TLS via Let's Encrypt |
| App folder | `~/munandy-reach/backend` (a clone of this repo) |
| Process | PM2 app **`munandy-backend`** |
| Port | **3100**, bound to `127.0.0.1` (not reachable from the internet) |
| Database | SQLite file `~/munandy-reach/backend/munandy.db` (not in git) |
| Apache config | `/opt/bitnami/apache/conf/bitnami/bitnami-ssl.conf`, `reach.munandy.co.zw` section |

> **This server is shared.** It also runs `wa_munandy` (port 3000), `wa_leon`
> (3001), `munandy-flow-reverb` (8080) and the `flow.munandy.co.zw` site.
> Never run `pm2 restart all`, `pm2 delete all`, `pm2 kill`, or a full Apache
> restart. Always target `munandy-backend` by name, and reload Apache with
> `apachectl -k graceful`.

## Updating the app (the usual task)

```bash
cd ~/munandy-reach && git pull
cd backend
npm ci --omit=dev
npm rebuild sqlite3 --build-from-source   # REQUIRED after every npm ci — see below
pm2 restart munandy-backend
```

Check it came back:

```bash
pm2 logs munandy-backend --lines 15 --nostream | grep -vi password
curl -s -X POST https://reach.munandy.co.zw/api/auth/login \
  -H "Content-Type: application/json" -d '{"email":"x","password":"y"}'; echo
# Expect: {"error":"Invalid credentials or inactive account"}
```

### Why the sqlite3 rebuild is required

The ready-made `sqlite3` v6 binary that npm downloads needs GLIBC 2.38, but
Debian 12 ships 2.36. Without the rebuild the app crash-loops with
`GLIBC_2.38 not found ... node_sqlite3.node`. The rebuild compiles the driver
on the server against its own libraries (`build-essential` and `python3` are
already installed).

## First-time install (for reference / a new server)

```bash
sudo apt-get update -qq && sudo apt-get install -y -qq git build-essential python3
cd ~ && git clone https://github.com/ziggie12/Android-Reach-App-2026.git munandy-reach
cd ~/munandy-reach/backend && mkdir -p logs

cat > .env <<EOF
NODE_ENV=production
PORT=3100
HOST=127.0.0.1
JWT_SECRET=$(node -e "console.log(require('crypto').randomBytes(48).toString('hex'))")
CORS_ORIGIN=*
EOF
chmod 600 .env

npm ci --omit=dev
npm rebuild sqlite3 --build-from-source
pm2 start ecosystem.config.js && pm2 save
```

Then add the forwarding lines to the `reach.munandy.co.zw` `<VirtualHost *:443>`
section of `bitnami-ssl.conf` (back the file up first):

```apache
# Munandy Reach Node API (PM2: munandy-backend on 127.0.0.1:3100)
ProxyPass        /api/     http://127.0.0.1:3100/api/ retry=0 timeout=60
ProxyPassReverse /api/     http://127.0.0.1:3100/api/
ProxyPass        /uploads/ http://127.0.0.1:3100/uploads/ retry=0 timeout=60
ProxyPassReverse /uploads/ http://127.0.0.1:3100/uploads/
```

```bash
sudo /opt/bitnami/apache/bin/apachectl configtest && \
sudo /opt/bitnami/apache/bin/apachectl -k graceful
```

### Default admin account

On first start with an empty database the app creates `admin@munandy.com`
with a random password, printed once to the PM2 log:

```bash
pm2 logs munandy-backend --lines 100 --nostream | grep -A3 "DEFAULT ADMIN"
```

Log in and change it immediately. Don't paste it into chats or tickets.

## Surviving a reboot

PM2 only restarts apps after a reboot if its startup service is enabled:

```bash
systemctl is-enabled pm2-bitnami 2>/dev/null || echo "not set up"
```

If it isn't set up, run `pm2 startup`, run the `sudo` command it prints, then
`pm2 save`.

## Backups

The whole database is one file. Take a safe copy while the app runs:

```bash
sudo apt-get install -y -qq sqlite3   # once
mkdir -p ~/backups
sqlite3 ~/munandy-reach/backend/munandy.db ".backup '$HOME/backups/munandy-$(date +%F).db'"
```

Also enable **automatic snapshots** for the instance in the Lightsail console,
and back up `~/munandy-reach/backend/uploads/` (proof-of-delivery photos).

## Rolling back the Apache change

The original config was backed up next to it as `bitnami-ssl.conf.bak-<date>`:

```bash
sudo cp /opt/bitnami/apache/conf/bitnami/bitnami-ssl.conf.bak-* \
        /opt/bitnami/apache/conf/bitnami/bitnami-ssl.conf
sudo /opt/bitnami/apache/bin/apachectl -k graceful
```

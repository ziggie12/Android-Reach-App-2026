// Merge duplicate customers (same name + location) left behind by the old route-save
// retry bug. Keeps one copy per shop (the one used by routes, if any), re-links any
// route stops that pointed at another copy, then deletes the extra copies.
//
//   node scripts/dedupe-customers.js            # preview only, changes nothing
//   node scripts/dedupe-customers.js --apply    # backs up the database, then merges
//
// Optionally pass a database path (default: ../munandy.db).
const path = require('path');
const sqlite3 = require('sqlite3');

const apply = process.argv.includes('--apply');
const dbFile = process.argv.find(a => a.endsWith('.db')) || path.resolve(__dirname, '..', 'munandy.db');
const db = new sqlite3.Database(dbFile);
db.configure('busyTimeout', 5000); // the server may be writing at the same time

const all = (sql, p = []) => new Promise((res, rej) => db.all(sql, p, (e, r) => (e ? rej(e) : res(r))));
const run = (sql, p = []) => new Promise((res, rej) => db.run(sql, p, function (e) { e ? rej(e) : res(this.changes); }));

(async () => {
    const customers = await all(`SELECT id, name, ROUND(lat, 5) AS la, ROUND(lng, 5) AS lo FROM customers`);
    const used = new Set((await all(`SELECT DISTINCT customer_id FROM route_stops`)).map(r => r.customer_id));

    const groups = new Map();
    for (const c of customers) {
        const key = JSON.stringify([c.name, c.la, c.lo]);
        if (!groups.has(key)) groups.set(key, []);
        groups.get(key).push(c.id);
    }

    const plan = [];
    for (const [key, ids] of groups) {
        if (ids.length < 2) continue;
        const usedIds = ids.filter(id => used.has(id));
        const keep = usedIds.length ? Math.max(...usedIds) : Math.min(...ids);
        plan.push({
            name: JSON.parse(key)[0],
            keep,
            drop: ids.filter(id => id !== keep),
            relink: usedIds.filter(id => id !== keep).length,
        });
    }

    const dropCount = plan.reduce((n, p) => n + p.drop.length, 0);
    console.log(`${customers.length} customers, ${plan.length} duplicated shops, ${dropCount} extra copies to remove`);
    plan.forEach(p => console.log(`  "${p.name}": keep #${p.keep}, remove ${p.drop.length}` +
        (p.relink ? `, re-link stops from ${p.relink} other cop${p.relink > 1 ? 'ies' : 'y'}` : '')));

    if (!plan.length) return db.close();
    if (!apply) {
        console.log('\nPreview only - nothing changed. Re-run with --apply to merge.');
        return db.close();
    }

    const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-');
    const backup = dbFile.replace(/\.db$/, `.backup-${stamp}.db`);
    await run(`VACUUM INTO ?`, [backup]);
    console.log(`\nBackup written: ${backup}`);

    await run('BEGIN IMMEDIATE');
    try {
        for (const p of plan) {
            const ph = p.drop.map(() => '?').join(',');
            await run(`UPDATE route_stops SET customer_id = ? WHERE customer_id IN (${ph})`, [p.keep, ...p.drop]);
            await run(`DELETE FROM customers WHERE id IN (${ph})`, p.drop);
        }
        await run('COMMIT');
        console.log(`Done: removed ${dropCount} duplicate customers.`);
    } catch (e) {
        await run('ROLLBACK');
        console.error(`Rolled back - nothing changed: ${e.message}`);
        process.exitCode = 1;
    }
    db.close();
})().catch(e => {
    console.error(e.message);
    process.exitCode = 1;
    db.close();
});

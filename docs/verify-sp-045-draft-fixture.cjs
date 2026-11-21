// Inspect the migration input on host SQLite. Room upgrade checks require the assigned target version.
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { DatabaseSync } = require('node:sqlite');

const app = resolve(__dirname, '../android/app');
const schema = JSON.parse(readFileSync(resolve(app, 'schemas/com.socialpilot.app.data.local.AppDatabase/1.json'), 'utf8')).database;
const seed = readFileSync(resolve(app, 'src/androidTest/assets/sp045/drafts-v1-MO_PHONG.sql'), 'utf8');
const db = new DatabaseSync(':memory:');
try {
    assert.equal(schema.version, 1);
    const drafts = schema.entities.find(entity => entity.tableName === 'drafts');
    db.exec(drafts.createSql.replace('${TABLE_NAME}', drafts.tableName));
    db.exec(seed);
    const rows = db.prepare('SELECT id, topic, createdAt FROM drafts ORDER BY createdAt DESC, id DESC LIMIT 20').all();
    assert.deepEqual(rows.map(row => [row.id, row.topic, row.createdAt]), [
        [42, null, 1795060800001], [11, 'MO_PHONG', 1795060800000], [7, 'MO_PHONG', 1795060800000]
    ]);
    assert.equal(db.prepare("SELECT seq FROM sqlite_sequence WHERE name = 'drafts'").get().seq, 50);
    const next = db.prepare('INSERT INTO drafts(topic, createdAt) VALUES (?, ?)').run('MO_PHONG', 1795060800003);
    assert.ok(next.lastInsertRowid > 50);
    console.log('PASS MO_PHONG v1 input: 42/11/7, NULL, UTC timestamps, deleted ID 50, next ID ' + next.lastInsertRowid);
} finally {
    db.close();
}

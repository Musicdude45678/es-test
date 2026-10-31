import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { loadConfig } from '../src/config.js';
import { createApp } from '../src/app.js';
import { sign } from '../src/auth.js';

async function boot() {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'reps-api-'));
  const config = loadConfig({ DATA_DIR: dir, SYNC_TOKEN: 'reps_testtoken', PUBLIC_URL: 'http://localhost', SESSION_SECRET: 's3cret', OWNER_NAME: 'Tester' });
  const app = await createApp(config);
  const server = app.listen(0);
  await new Promise((r) => server.once('listening', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  return { base, server, config };
}

const snapshot = { schemaVersion: 1, exportedAt: 1, exercises: [{ id: 'e1', name: 'Bench' }], workouts: [{ id: 'w1', endedAt: 5 }], workoutExercises: [], sets: [{ id: 's1' }], routines: [], routineExercises: [] };

test('phone API: token auth, push, list, latest', async () => {
  const { base, server } = await boot();
  try {
    let r = await fetch(`${base}/api/me`);
    assert.equal(r.status, 401);
    r = await fetch(`${base}/api/me`, { headers: { authorization: 'Bearer wrong' } });
    assert.equal(r.status, 401);
    r = await fetch(`${base}/api/me`, { headers: { authorization: 'Bearer reps_testtoken' } });
    assert.equal(r.status, 200);
    let body = await r.json();
    assert.equal(body.snapshotCount, 0);
    assert.equal(body.displayName, 'Tester');

    r = await fetch(`${base}/api/snapshot/latest`, { headers: { authorization: 'Bearer reps_testtoken' } });
    assert.equal(r.status, 404);

    r = await fetch(`${base}/api/snapshot`, { method: 'PUT', headers: { authorization: 'Bearer reps_testtoken', 'content-type': 'application/json' }, body: JSON.stringify(snapshot) });
    assert.equal(r.status, 200);
    body = await r.json();
    assert.equal(body.ok, true);
    assert.equal(body.snapshotCount, 1);

    r = await fetch(`${base}/api/snapshot`, { method: 'PUT', headers: { authorization: 'Bearer reps_testtoken', 'content-type': 'application/json' }, body: '{"nope":true}' });
    assert.equal(r.status, 400);

    r = await fetch(`${base}/api/snapshot/latest`, { headers: { authorization: 'Bearer reps_testtoken' } });
    assert.equal(r.status, 200);
    body = await r.json();
    assert.equal(body.workouts[0].id, 'w1');

    r = await fetch(`${base}/api/snapshots`, { headers: { authorization: 'Bearer reps_testtoken' } });
    body = await r.json();
    assert.equal(body.snapshots.length, 1);

    // connect info needs a browser session, not a token
    r = await fetch(`${base}/api/connect`, { headers: { authorization: 'Bearer reps_testtoken' } });
    assert.equal(r.status, 401);
  } finally { server.close(); }
});

test('browser session cookie unlocks dashboard + connect info', async () => {
  const { base, server, config } = await boot();
  try {
    const cookie = `reps_session=${encodeURIComponent(sign({ uid: 'owner', exp: Date.now() + 60000 }, config.sessionSecret))}`;
    let r = await fetch(`${base}/`, { headers: { cookie } });
    assert.equal(r.status, 200);
    assert.match(await r.text(), /Reps/);
    r = await fetch(`${base}/api/connect`, { headers: { cookie } });
    const body = await r.json();
    assert.equal(body.token, 'reps_testtoken');
    assert.match(body.link, /^reps:\/\/connect\?url=/);
    r = await fetch(`${base}/api/connect/qr.svg`, { headers: { cookie } });
    assert.equal(r.status, 200);
    assert.match(r.headers.get('content-type'), /svg/);
    // tampered cookie is rejected
    r = await fetch(`${base}/api/connect`, { headers: { cookie: cookie.slice(0, -2) + 'xx' } });
    assert.equal(r.status, 401);
    // without OIDC configured the root shows setup instructions
    r = await fetch(`${base}/`);
    assert.match(await r.text(), /setup/i);
  } finally { server.close(); }
});

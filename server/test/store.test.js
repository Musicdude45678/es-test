import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { SnapshotStore } from '../src/store.js';

const snap = (n) => ({ schemaVersion: 1, exportedAt: n, exercises: [], workouts: Array.from({ length: n }, (_, i) => ({ id: String(i) })), sets: [] });

test('saves, dedupes identical content and lists newest first', async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'reps-'));
  let now = Date.parse('2026-01-10T10:00:00Z');
  const store = new SnapshotStore(dir, { now: () => now });
  const a = await store.save('u', JSON.stringify(snap(1)), snap(1));
  assert.equal(a.deduped, false);
  now += 1000;
  const b = await store.save('u', JSON.stringify({ ...snap(1), exportedAt: 999 }), { ...snap(1), exportedAt: 999 });
  assert.equal(b.deduped, true, 'same content with different exportedAt is deduped');
  now += 1000;
  const c = await store.save('u', JSON.stringify(snap(2)), snap(2));
  assert.equal(c.deduped, false);
  const list = await store.list('u');
  assert.equal(list.length, 2);
  assert.ok(list[0].at > list[1].at);
  const latest = await store.latest('u');
  assert.equal(JSON.parse(latest.json).workouts.length, 2);
});

test('rolling retention thins old snapshots', async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'reps-'));
  const start = Date.parse('2025-01-01T00:00:00Z');
  let now = start;
  const store = new SnapshotStore(dir, { now: () => now });
  // 3 snapshots a day for 400 days
  for (let d = 0; d < 400; d++) {
    for (let k = 0; k < 3; k++) {
      now = start + d * 86400000 + k * 3600000 * 5;
      const s = { ...snap(1), workouts: [{ id: `${d}-${k}` }] };
      await store.save('u', JSON.stringify(s), s);
    }
  }
  const list = await store.list('u');
  const ageDays = (it) => (now - it.at) / 86400000;
  assert.equal(list.filter((it) => ageDays(it) <= 2).length, 6 + 1, 'everything in the last 48h kept');
  const daily = list.filter((it) => ageDays(it) > 2 && ageDays(it) <= 60);
  assert.ok(daily.length >= 55 && daily.length <= 60, `one per day for two months, got ${daily.length}`);
  const weekly = list.filter((it) => ageDays(it) > 60 && ageDays(it) <= 364);
  assert.ok(weekly.length >= 40 && weekly.length <= 46, `one per week for a year, got ${weekly.length}`);
  const monthly = list.filter((it) => ageDays(it) > 364);
  assert.ok(monthly.length >= 1 && monthly.length <= 3, `one per month beyond a year, got ${monthly.length}`);
  assert.ok(list.length < 130, `total kept ${list.length}`);
});

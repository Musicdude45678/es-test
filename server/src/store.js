import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

const DAY = 24 * 3600 * 1000;

/**
 * Snapshot files per user: <dataDir>/users/<id>/snapshots/<timestamp>.json plus latest.json.
 * Every push from the phone becomes a new snapshot unless its content is identical to the
 * latest one. Old snapshots are thinned out with a rolling policy (see prune()).
 */
export class SnapshotStore {
  constructor(dataDir, opts = {}) {
    this.dataDir = dataDir;
    this.now = opts.now || (() => Date.now());
    this.policy = Object.assign({ keepAllHours: 48, dailyDays: 60, weeklyWeeks: 52 }, opts.policy || {});
  }

  dir(userId) { return path.join(this.dataDir, 'users', safe(userId), 'snapshots'); }

  async list(userId) {
    const dir = this.dir(userId);
    let names;
    try { names = await fs.readdir(dir); } catch (e) { if (e.code === 'ENOENT') return []; throw e; }
    const items = [];
    for (const n of names) {
      if (!n.endsWith('.json') || n === 'latest.json') continue;
      const id = n.slice(0, -5);
      const at = idToTime(id);
      if (!Number.isFinite(at)) continue;
      const st = await fs.stat(path.join(dir, n));
      items.push({ id, at, bytes: st.size });
    }
    items.sort((a, b) => b.at - a.at);
    return items;
  }

  async read(userId, id) {
    if (!/^[0-9TZ.-]+$/.test(id)) return null;
    try { return await fs.readFile(path.join(this.dir(userId), id + '.json'), 'utf8'); } catch (e) { if (e.code === 'ENOENT') return null; throw e; }
  }

  async latest(userId) {
    const items = await this.list(userId);
    if (items.length === 0) return null;
    const json = await this.read(userId, items[0].id);
    return json == null ? null : { ...items[0], json };
  }

  /** Saves a snapshot. Returns { id, at, deduped, count }. */
  async save(userId, snapshotText, parsed) {
    const dir = this.dir(userId);
    await fs.mkdir(dir, { recursive: true });
    const hash = contentHash(parsed);
    const hashFile = path.join(dir, 'latest.sha256');
    let previous = null;
    try { previous = (await fs.readFile(hashFile, 'utf8')).trim(); } catch (e) { if (e.code !== 'ENOENT') throw e; }
    const items = await this.list(userId);
    if (previous === hash && items.length > 0) {
      return { id: items[0].id, at: items[0].at, deduped: true, count: items.length };
    }
    const at = this.now();
    let id = timeToId(at);
    if (items.some((i) => i.id === id)) id = timeToId(at + 1);
    const tmp = path.join(dir, id + '.json.tmp');
    await fs.writeFile(tmp, snapshotText);
    await fs.rename(tmp, path.join(dir, id + '.json'));
    await fs.writeFile(hashFile, hash);
    const count = await this.prune(userId);
    return { id, at, deduped: false, count };
  }

  /**
   * Rolling retention: keep everything from the last `keepAllHours`, then the newest snapshot per
   * day for `dailyDays`, then one per week for `weeklyWeeks`, then one per month forever.
   */
  async prune(userId) {
    const items = await this.list(userId); // newest first
    const now = this.now();
    const keep = new Set();
    const seenDay = new Set(), seenWeek = new Set(), seenMonth = new Set();
    for (const it of items) {
      const age = now - it.at;
      const d = new Date(it.at);
      const dayKey = d.toISOString().slice(0, 10);
      const monthKey = dayKey.slice(0, 7);
      const weekKey = isoWeekKey(d);
      if (age <= this.policy.keepAllHours * 3600 * 1000) { keep.add(it.id); continue; }
      if (age <= this.policy.dailyDays * DAY) { if (!seenDay.has(dayKey)) { seenDay.add(dayKey); keep.add(it.id); } continue; }
      if (age <= this.policy.weeklyWeeks * 7 * DAY) { if (!seenWeek.has(weekKey)) { seenWeek.add(weekKey); keep.add(it.id); } continue; }
      if (!seenMonth.has(monthKey)) { seenMonth.add(monthKey); keep.add(it.id); }
    }
    if (items.length > 0) keep.add(items[0].id);
    for (const it of items) {
      if (!keep.has(it.id)) await fs.unlink(path.join(this.dir(userId), it.id + '.json')).catch(() => {});
    }
    return keep.size;
  }
}

export function contentHash(parsed) {
  const copy = { ...parsed };
  delete copy.exportedAt;
  return crypto.createHash('sha256').update(stableStringify(copy)).digest('hex');
}

function stableStringify(v) {
  if (Array.isArray(v)) return '[' + v.map(stableStringify).join(',') + ']';
  if (v && typeof v === 'object') return '{' + Object.keys(v).sort().map((k) => JSON.stringify(k) + ':' + stableStringify(v[k])).join(',') + '}';
  return JSON.stringify(v);
}

function timeToId(ms) { return new Date(ms).toISOString().replace(/:/g, '-'); }
function idToTime(id) { return Date.parse(id.replace(/^(\d{4}-\d{2}-\d{2}T\d{2})-(\d{2})-(\d{2}\.\d{3}Z)$/, '$1:$2:$3')); }
function safe(s) { return String(s).replace(/[^a-zA-Z0-9_-]/g, '_'); }
function isoWeekKey(d) {
  const date = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));
  const day = date.getUTCDay() || 7;
  date.setUTCDate(date.getUTCDate() + 4 - day);
  const yearStart = new Date(Date.UTC(date.getUTCFullYear(), 0, 1));
  const week = Math.ceil(((date - yearStart) / DAY + 1) / 7);
  return `${date.getUTCFullYear()}-W${week}`;
}

import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

/**
 * Users live in <dataDir>/users.json. One owner today; add more objects to the
 * "users" array (or let the dashboard owner share tokens) to support more people.
 *
 * {
 *   "users": [
 *     { "id": "owner", "displayName": "Armandt", "emails": ["me@example.com"],
 *       "oidcSubs": ["<pocket-id subject>"],
 *       "tokens": [{ "token": "…", "label": "Phone", "createdAt": 1700000000000 }] }
 *   ]
 * }
 */
export class UserStore {
  constructor(dataDir, ownerBootstrap = {}) {
    this.file = path.join(dataDir, 'users.json');
    this.bootstrap = ownerBootstrap;
    this.data = { users: [] };
    this.lock = Promise.resolve();
  }

  async load() {
    await fs.mkdir(path.dirname(this.file), { recursive: true });
    try {
      this.data = JSON.parse(await fs.readFile(this.file, 'utf8'));
      if (!Array.isArray(this.data.users)) this.data.users = [];
    } catch (e) {
      if (e.code !== 'ENOENT') throw e;
      this.data = { users: [] };
    }
    let changed = false;
    if (this.data.users.length === 0) {
      this.data.users.push({
        id: 'owner',
        displayName: this.bootstrap.name || 'Owner',
        emails: this.bootstrap.email ? [this.bootstrap.email] : [],
        oidcSubs: [],
        tokens: [{ token: this.bootstrap.syncToken || newToken(), label: 'Phone', createdAt: Date.now() }],
        createdAt: Date.now(),
      });
      changed = true;
    } else if (this.bootstrap.syncToken) {
      // Keep the env-provided token valid for the owner even if users.json was edited.
      const owner = this.data.users[0];
      if (!owner.tokens.some((t) => t.token === this.bootstrap.syncToken)) {
        owner.tokens.push({ token: this.bootstrap.syncToken, label: 'Phone (env)', createdAt: Date.now() });
        changed = true;
      }
      if (this.bootstrap.email && !owner.emails.includes(this.bootstrap.email)) {
        owner.emails.push(this.bootstrap.email);
        changed = true;
      }
    }
    if (changed) await this.save();
    return this;
  }

  async save() {
    this.lock = this.lock.then(async () => {
      const tmp = this.file + '.tmp';
      await fs.writeFile(tmp, JSON.stringify(this.data, null, 2));
      await fs.rename(tmp, this.file);
    });
    return this.lock;
  }

  get owner() { return this.data.users[0]; }

  byId(id) { return this.data.users.find((u) => u.id === id) || null; }

  byToken(token) {
    if (!token) return null;
    const probe = sha(token);
    for (const u of this.data.users) {
      for (const t of u.tokens || []) {
        if (crypto.timingSafeEqual(sha(t.token), probe)) return u;
      }
    }
    return null;
  }

  /**
   * Resolve an OIDC identity to a user. Matches by subject, then by e-mail (binding the subject),
   * and lets the very first login claim the owner account when no owner e-mail was configured.
   */
  async byOidc({ sub, email }) {
    email = (email || '').trim().toLowerCase();
    let user = this.data.users.find((u) => (u.oidcSubs || []).includes(sub));
    if (!user && email) user = this.data.users.find((u) => (u.emails || []).map((e) => e.toLowerCase()).includes(email));
    if (!user) {
      const owner = this.owner;
      const unclaimed = owner && (owner.oidcSubs || []).length === 0 && (owner.emails || []).length === 0;
      if (unclaimed) user = owner;
    }
    if (!user) return null;
    let changed = false;
    user.oidcSubs = user.oidcSubs || [];
    user.emails = user.emails || [];
    if (!user.oidcSubs.includes(sub)) { user.oidcSubs.push(sub); changed = true; }
    if (email && !user.emails.includes(email)) { user.emails.push(email); changed = true; }
    if (changed) await this.save();
    return user;
  }

  async rotateToken(userId, label = 'Phone') {
    const u = this.byId(userId);
    if (!u) return null;
    const t = { token: newToken(), label, createdAt: Date.now() };
    u.tokens = [t];
    await this.save();
    return t;
  }
}

export function newToken() {
  return 'reps_' + crypto.randomBytes(24).toString('base64url');
}

function sha(s) { return crypto.createHash('sha256').update(String(s)).digest(); }

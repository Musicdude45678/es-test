import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import QRCode from 'qrcode';
import { oidcConfigured } from './config.js';
import { UserStore } from './users.js';
import { SnapshotStore } from './store.js';
import { createOidc, parseCookies, setCookie, clearCookie, sign, unsign } from './auth.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const publicDir = path.join(here, '..', 'public');

export async function createApp(config, deps = {}) {
  const users = deps.users || (await new UserStore(config.dataDir, config.owner).load());
  const store = deps.store || new SnapshotStore(config.dataDir);
  const secure = config.publicUrl.startsWith('https://');
  let oidc = null;
  let oidcError = null;
  if (oidcConfigured(config)) {
    try { oidc = await createOidc(config.oidc); } catch (e) { oidcError = e.message; console.error('[reps] OIDC setup failed:', e.message); }
  }

  const app = express();
  app.disable('x-powered-by');
  if (config.trustProxy) app.set('trust proxy', 1);

  // ---- identity resolution: Bearer token (phone) or session cookie (browser) ----
  app.use((req, _res, next) => {
    const auth = req.headers.authorization || '';
    if (auth.startsWith('Bearer ')) {
      const user = users.byToken(auth.slice(7).trim());
      if (user) { req.user = user; req.authVia = 'token'; }
      return next();
    }
    const session = unsign(parseCookies(req).reps_session, config.sessionSecret);
    if (session && session.uid) {
      const user = users.byId(session.uid);
      if (user) { req.user = user; req.authVia = 'session'; req.session = session; }
    }
    next();
  });

  const requireUser = (req, res, next) => {
    if (!req.user) return res.status(401).json({ ok: false, error: 'unauthorized' });
    next();
  };
  const requireSession = (req, res, next) => {
    if (!req.user || req.authVia !== 'session') return res.status(401).json({ ok: false, error: 'login required' });
    next();
  };

  app.get('/healthz', (_req, res) => res.json({ ok: true, time: Date.now() }));

  // ---- pages ----
  app.get('/', (req, res) => {
    if (req.user && req.authVia === 'session') return res.sendFile(path.join(publicDir, 'dashboard.html'));
    if (!oidc) {
      res.locals.oidcError = oidcError;
      return res.status(200).sendFile(path.join(publicDir, 'setup.html'));
    }
    return res.sendFile(path.join(publicDir, 'login.html'));
  });
  app.get('/setup-status', (_req, res) => res.json({ configured: Boolean(oidc), error: oidcError, issuer: config.oidc.issuer || null, redirectUri: config.oidc.redirectUri, publicUrl: config.publicUrl, ephemeralSessionSecret: config.sessionSecretIsEphemeral }));
  app.use('/assets', express.static(path.join(publicDir, 'assets'), { maxAge: '1h' }));

  // ---- auth ----
  app.get('/auth/login', (req, res) => {
    if (!oidc) return res.redirect('/');
    const { url, state, nonce, verifier } = oidc.begin();
    setCookie(res, 'reps_oidc', sign({ state, nonce, verifier, exp: Date.now() + 10 * 60 * 1000 }, config.sessionSecret), { maxAgeSec: 600, secure });
    res.redirect(url);
  });

  app.get('/auth/callback', async (req, res) => {
    if (!oidc) return res.redirect('/');
    try {
      const pending = unsign(parseCookies(req).reps_oidc, config.sessionSecret);
      clearCookie(res, 'reps_oidc', secure);
      if (!pending || req.query.state !== pending.state) throw new Error('Login session expired, please try again');
      if (req.query.error) throw new Error(`${req.query.error}: ${req.query.error_description || ''}`);
      const { claims, idToken } = await oidc.complete({ code: String(req.query.code), verifier: pending.verifier, nonce: pending.nonce });
      const email = (claims.email || '').toLowerCase();
      if (config.allowedEmails.length && !config.allowedEmails.includes(email)) throw new Error('This account is not allowed to use this dashboard');
      const user = await users.byOidc({ sub: claims.sub, email });
      if (!user) throw new Error('This account is not linked to a Reps user');
      if ((!user.displayName || user.displayName === 'Owner') && (claims.name || claims.preferred_username)) {
        user.displayName = claims.name || claims.preferred_username;
        await users.save();
      }
      const exp = Date.now() + config.sessionDays * 86400 * 1000;
      setCookie(res, 'reps_session', sign({ uid: user.id, exp, idt: idToken.length > 3000 ? undefined : idToken }, config.sessionSecret), { maxAgeSec: config.sessionDays * 86400, secure });
      res.redirect('/');
    } catch (e) {
      res.status(400).send(errorPage('Sign-in failed', e.message));
    }
  });

  const logout = (req, res) => {
    const idt = req.session?.idt;
    clearCookie(res, 'reps_session', secure);
    const url = oidc ? oidc.logoutUrl(idt) : null;
    res.redirect(url || '/');
  };
  app.get('/auth/logout', logout);
  app.post('/auth/logout', logout);

  // ---- API (phone with Bearer token, or browser with session) ----
  app.get('/api/me', requireUser, async (req, res) => {
    const items = await store.list(req.user.id);
    res.json({
      ok: true,
      user: req.user.id,
      displayName: req.user.displayName || req.user.id,
      snapshotCount: items.length,
      latestSnapshotAt: items[0]?.at ?? null,
      serverTime: Date.now(),
      via: req.authVia,
    });
  });

  app.put('/api/snapshot', requireUser, express.text({ type: '*/*', limit: config.maxSnapshotBytes }), async (req, res) => {
    let parsed;
    try { parsed = JSON.parse(req.body); } catch { return res.status(400).json({ ok: false, error: 'body is not valid JSON' }); }
    if (!parsed || !Array.isArray(parsed.workouts) || !Array.isArray(parsed.exercises) || !Array.isArray(parsed.sets)) {
      return res.status(400).json({ ok: false, error: 'not a Reps snapshot' });
    }
    const result = await store.save(req.user.id, req.body, parsed);
    res.json({ ok: true, savedAt: result.at, snapshotId: result.id, deduped: result.deduped, snapshotCount: result.count });
  });

  app.get('/api/snapshots', requireUser, async (req, res) => {
    const items = await store.list(req.user.id);
    res.json({ ok: true, snapshots: items });
  });

  app.get('/api/snapshot/latest', requireUser, async (req, res) => {
    const latest = await store.latest(req.user.id);
    if (!latest) return res.status(404).json({ ok: false, error: 'no snapshot yet' });
    res.set('X-Snapshot-Id', latest.id);
    if (req.query.download) res.attachment(`reps-backup-${latest.id}.json`);
    res.type('application/json').send(latest.json);
  });

  app.get('/api/snapshot/:id', requireUser, async (req, res) => {
    const json = await store.read(req.user.id, req.params.id);
    if (json == null) return res.status(404).json({ ok: false, error: 'not found' });
    if (req.query.download) res.attachment(`reps-backup-${req.params.id}.json`);
    res.type('application/json').send(json);
  });

  // ---- dashboard-only endpoints (need a browser session) ----
  app.get('/api/connect', requireSession, (req, res) => {
    const token = req.user.tokens?.[0]?.token;
    if (!token) return res.status(404).json({ ok: false, error: 'no token' });
    const link = `reps://connect?url=${encodeURIComponent(config.publicUrl)}&token=${encodeURIComponent(token)}&name=${encodeURIComponent(req.user.displayName || req.user.id)}`;
    res.json({ ok: true, url: config.publicUrl, token, link });
  });

  app.get('/api/connect/qr.svg', requireSession, async (req, res) => {
    const token = req.user.tokens?.[0]?.token;
    if (!token) return res.status(404).end();
    const link = `reps://connect?url=${encodeURIComponent(config.publicUrl)}&token=${encodeURIComponent(token)}&name=${encodeURIComponent(req.user.displayName || req.user.id)}`;
    const svg = await QRCode.toString(link, { type: 'svg', margin: 1, color: { dark: '#0e1014', light: '#ffffff' } });
    res.type('image/svg+xml').set('Cache-Control', 'no-store').send(svg);
  });

  app.post('/api/tokens/rotate', requireSession, async (req, res) => {
    const t = await users.rotateToken(req.user.id);
    res.json({ ok: true, token: t.token });
  });

  app.use('/api', (_req, res) => res.status(404).json({ ok: false, error: 'not found' }));
  app.use((err, _req, res, _next) => {
    if (err?.type === 'entity.too.large') return res.status(413).json({ ok: false, error: 'snapshot too large' });
    console.error('[reps]', err);
    res.status(500).json({ ok: false, error: 'server error' });
  });

  app.locals.users = users;
  app.locals.store = store;
  return app;
}

function errorPage(title, message) {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title><link rel="stylesheet" href="/assets/styles.css"></head><body class="center"><main class="card narrow"><h1>${title}</h1><p class="muted">${escapeHtml(message)}</p><a class="btn" href="/">Back</a></main></body></html>`;
}
function escapeHtml(s) { return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }

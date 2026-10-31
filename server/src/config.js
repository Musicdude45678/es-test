import crypto from 'node:crypto';

const bool = (v, d = false) => (v == null || v === '' ? d : ['1', 'true', 'yes', 'on'].includes(String(v).toLowerCase()));

export function loadConfig(env = process.env) {
  const publicUrl = (env.PUBLIC_URL || `http://localhost:${env.PORT || 3000}`).replace(/\/+$/, '');
  const sessionSecret = env.SESSION_SECRET || '';
  return {
    port: Number(env.PORT || 3000),
    dataDir: env.DATA_DIR || './data',
    publicUrl,
    trustProxy: bool(env.TRUST_PROXY, true),
    // Pocket ID (any OIDC provider works): issuer URL, client id + secret created in Pocket ID.
    oidc: {
      issuer: (env.OIDC_ISSUER || '').replace(/\/+$/, ''),
      clientId: env.OIDC_CLIENT_ID || '',
      clientSecret: env.OIDC_CLIENT_SECRET || '',
      scopes: env.OIDC_SCOPES || 'openid profile email',
      redirectUri: `${publicUrl}/auth/callback`,
      postLogoutRedirectUri: `${publicUrl}/`,
    },
    // Secret used to sign the session cookie. Generated per process if missing (sessions then reset on restart).
    sessionSecret: sessionSecret || crypto.randomBytes(32).toString('hex'),
    sessionSecretIsEphemeral: !sessionSecret,
    sessionDays: Number(env.SESSION_DAYS || 30),
    // Bootstrap for the owner account.
    owner: {
      email: (env.OWNER_EMAIL || '').trim().toLowerCase(),
      name: env.OWNER_NAME || '',
      syncToken: env.SYNC_TOKEN || '',
    },
    // Optional hard allow-list of e-mails allowed to sign in to the dashboard (comma separated).
    allowedEmails: (env.ALLOWED_EMAILS || '').split(',').map((s) => s.trim().toLowerCase()).filter(Boolean),
    maxSnapshotBytes: Number(env.MAX_SNAPSHOT_MB || 50) * 1024 * 1024,
  };
}

export function oidcConfigured(config) {
  return Boolean(config.oidc.issuer && config.oidc.clientId && config.oidc.clientSecret);
}

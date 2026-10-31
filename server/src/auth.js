import crypto from 'node:crypto';
import { createRemoteJWKSet, jwtVerify } from 'jose';

// ---------- signed cookies ----------
export function sign(value, secret) {
  const payload = Buffer.from(JSON.stringify(value)).toString('base64url');
  const mac = crypto.createHmac('sha256', secret).update(payload).digest('base64url');
  return `${payload}.${mac}`;
}

export function unsign(cookie, secret) {
  if (!cookie || typeof cookie !== 'string') return null;
  const i = cookie.lastIndexOf('.');
  if (i < 0) return null;
  const payload = cookie.slice(0, i), mac = cookie.slice(i + 1);
  const expected = crypto.createHmac('sha256', secret).update(payload).digest('base64url');
  if (mac.length !== expected.length || !crypto.timingSafeEqual(Buffer.from(mac), Buffer.from(expected))) return null;
  try {
    const v = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
    if (v && v.exp && Date.now() > v.exp) return null;
    return v;
  } catch { return null; }
}

export function parseCookies(req) {
  const out = {};
  for (const part of (req.headers.cookie || '').split(';')) {
    const i = part.indexOf('=');
    if (i < 0) continue;
    out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim());
  }
  return out;
}

export function setCookie(res, name, value, { maxAgeSec, secure, path = '/' }) {
  const parts = [`${name}=${encodeURIComponent(value)}`, `Path=${path}`, 'HttpOnly', 'SameSite=Lax'];
  if (secure) parts.push('Secure');
  if (maxAgeSec != null) parts.push(`Max-Age=${maxAgeSec}`);
  res.append('Set-Cookie', parts.join('; '));
}

export function clearCookie(res, name, secure) {
  setCookie(res, name, '', { maxAgeSec: 0, secure });
}

// ---------- OIDC (authorization code + PKCE) ----------
export async function createOidc(oidc) {
  const res = await fetch(`${oidc.issuer}/.well-known/openid-configuration`);
  if (!res.ok) throw new Error(`OIDC discovery failed: ${res.status} from ${oidc.issuer}`);
  const meta = await res.json();
  const jwks = createRemoteJWKSet(new URL(meta.jwks_uri));

  return {
    meta,
    begin() {
      const state = crypto.randomBytes(16).toString('base64url');
      const nonce = crypto.randomBytes(16).toString('base64url');
      const verifier = crypto.randomBytes(32).toString('base64url');
      const challenge = crypto.createHash('sha256').update(verifier).digest('base64url');
      const url = new URL(meta.authorization_endpoint);
      url.searchParams.set('response_type', 'code');
      url.searchParams.set('client_id', oidc.clientId);
      url.searchParams.set('redirect_uri', oidc.redirectUri);
      url.searchParams.set('scope', oidc.scopes);
      url.searchParams.set('state', state);
      url.searchParams.set('nonce', nonce);
      url.searchParams.set('code_challenge', challenge);
      url.searchParams.set('code_challenge_method', 'S256');
      return { url: url.toString(), state, nonce, verifier };
    },
    async complete({ code, verifier, nonce }) {
      const body = new URLSearchParams({
        grant_type: 'authorization_code',
        code,
        redirect_uri: oidc.redirectUri,
        client_id: oidc.clientId,
        client_secret: oidc.clientSecret,
        code_verifier: verifier,
      });
      const tr = await fetch(meta.token_endpoint, {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded', accept: 'application/json' },
        body,
      });
      const tokens = await tr.json().catch(() => ({}));
      if (!tr.ok || !tokens.id_token) throw new Error(`Token exchange failed: ${tr.status} ${tokens.error || ''} ${tokens.error_description || ''}`.trim());
      const { payload } = await jwtVerify(tokens.id_token, jwks, { issuer: meta.issuer, audience: oidc.clientId });
      if (payload.nonce !== nonce) throw new Error('OIDC nonce mismatch');
      let claims = payload;
      if (!claims.email && meta.userinfo_endpoint && tokens.access_token) {
        const ur = await fetch(meta.userinfo_endpoint, { headers: { authorization: `Bearer ${tokens.access_token}` } });
        if (ur.ok) claims = { ...claims, ...(await ur.json()) };
      }
      return { claims, idToken: tokens.id_token };
    },
    logoutUrl(idToken) {
      if (!meta.end_session_endpoint) return null;
      const url = new URL(meta.end_session_endpoint);
      if (idToken) url.searchParams.set('id_token_hint', idToken);
      url.searchParams.set('post_logout_redirect_uri', oidc.postLogoutRedirectUri);
      url.searchParams.set('client_id', oidc.clientId);
      return url.toString();
    },
  };
}

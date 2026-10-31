import { loadConfig, oidcConfigured } from './config.js';
import { createApp } from './app.js';

const config = loadConfig();
const app = await createApp(config);
app.listen(config.port, () => {
  console.log(`[reps] listening on :${config.port}  public=${config.publicUrl}  data=${config.dataDir}`);
  if (!oidcConfigured(config)) console.log('[reps] OIDC not configured: dashboard shows setup instructions; the phone API still works with the sync token.');
  if (config.sessionSecretIsEphemeral) console.log('[reps] SESSION_SECRET not set: dashboard logins reset when the server restarts.');
});

# Reps

A gym companion for Android that makes logging a set a two-tap job, plus a private web dashboard that keeps a rolling backup of everything and charts your progress.

```
reps/
├── app/        Android app (Kotlin, Jetpack Compose, Room)
├── server/     Dashboard + backup server (Node 22, Express) → reps.edgestudios.co.za
└── .github/    Actions: builds the APK on every push and attaches it to the "latest" release
```

## Install the app

1. On your phone open the repo's **Releases → latest** and download `app-release.apk` (GitHub Actions builds it on every push; the same file is also an artifact on each workflow run).
2. Open it and allow installing from this source when Android asks.
3. Later versions install straight over the top as long as they are signed with the same key (see **Signing** below).

Minimum Android 8.0 (API 26). No account, no permissions beyond internet and vibration.

## How the app works at the gym

- **Start workout** (or start a saved routine) → **Add exercise** → the weight and reps are already filled in from last time.
- Tap **Log set**. That is it. A rest timer starts, the phone buzzes when rest is over, and the next set is pre-filled.
- The **−/+** buttons step the weight by your chosen increment (2.5 kg by default). Tap the number for a keypad.
- **Bar weight is handled for you.** Barbell exercises count the bar (20 kg default, configurable per exercise or globally). The keypad has a **Bar only** chip for empty-bar sets, and the plate breakdown per side is shown under the weight.
- Dumbbells are logged per hand; bodyweight exercises log added weight (0 for none).
- New personal records are flagged with a trophy as you log them.
- Finish → summary → History. Progress tab shows weekly volume, recent records, and a trend line per exercise (top weight, estimated 1RM, volume).
- Everything is stored on the phone (Room/SQLite) and works offline. Units can be kg or lb.

## Dashboard and backup (reps.edgestudios.co.za)

The app pushes a full snapshot of its data to the server a few seconds after any change and right after you finish a workout. The server keeps rolling backups (everything from the last 48 h, then one per day for 60 days, one per week for a year, one per month forever) and renders the dashboard from the latest snapshot. On a new phone: connect, tap **Restore**, done.

### Deploy with Coolify

1. **Coolify → New resource → Application → Public/Private repository (GitHub App)**, pick this repo and branch.
2. Build pack: **Dockerfile**. Base directory: `/server`. Dockerfile location: `/server/Dockerfile`. Port: `3000`.
3. Domain: `https://reps.edgestudios.co.za`.
4. **Persistent storage**: add a volume mounted at `/data` (this holds `users.json` and all snapshots).
5. **Environment variables**:

   | Variable | Value |
   |---|---|
   | `PUBLIC_URL` | `https://reps.edgestudios.co.za` |
   | `SYNC_TOKEN` | a long random string, e.g. `openssl rand -base64 32` (the phone's token) |
   | `SESSION_SECRET` | another long random string (signs dashboard login cookies) |
   | `OIDC_ISSUER` | your Pocket ID base URL, e.g. `https://id.edgestudios.co.za` |
   | `OIDC_CLIENT_ID` | from the Pocket ID client (step below) |
   | `OIDC_CLIENT_SECRET` | from the Pocket ID client |
   | `OWNER_EMAIL` | optional. Your Pocket ID e-mail. If left out, the first account to sign in becomes the owner. |

6. Deploy. `GET /healthz` is the health check. Until OIDC is configured the site shows a setup page with the exact callback URL, and the phone API already works.

### Pocket ID client

In Pocket ID → **OIDC Clients → Add**:

- Name: `Reps`
- Callback URL: `https://reps.edgestudios.co.za/auth/callback`
- Logout callback URL: `https://reps.edgestudios.co.za/`
- PKCE: on (the server always sends a PKCE challenge; it also sends the client secret, so a confidential client is fine)

Copy the client ID and secret into Coolify, redeploy, open the dashboard and sign in.

### Connect the phone

Dashboard → **Connect phone** shows a QR code and an **Open in Reps** button (`reps://connect?...`) that fills in the server URL and token automatically. Or type them in the app under **Settings → Dashboard & backup**. If the server already holds backups and the phone is empty, the app offers to restore.

### Auth model (ready for more people)

- The phone authenticates with a bearer **sync token**; the dashboard authenticates with **Pocket ID** (OIDC authorization code + PKCE, ID token verified against the JWKS). Both resolve to a user in `/data/users.json`.
- One user today (`owner`). To add someone, add an object to `users` with their e-mail(s) and a token (or let them sign in once and move their `oidcSubs`), and give them the token for their phone. Each user's snapshots live under `/data/users/<id>/`.
- `ALLOWED_EMAILS` (comma separated) hard-limits who can sign in regardless of `users.json`.
- `POST /api/tokens/rotate` (from the dashboard, "New token") invalidates the old phone token.

### API (used by the app)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/me` | token or session | who am I, snapshot count |
| `PUT` | `/api/snapshot` | token or session | push a full snapshot (JSON) |
| `GET` | `/api/snapshot/latest` | token or session | latest snapshot (restore) |
| `GET` | `/api/snapshots` | token or session | list backups |
| `GET` | `/api/snapshot/:id` | token or session | one backup (`?download=1` to save) |
| `GET` | `/api/connect` | session | URL + token + `reps://` link for the phone |

## Building locally

```bash
# Android (needs Android SDK; set ANDROID_HOME or local.properties sdk.dir)
gradle wrapper --gradle-version 8.14.3  # once: fetches the wrapper jar (Android Studio does this for you)
./gradlew :app:assembleRelease          # → app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest

# Server
cd server && npm install && npm test && npm start
```

GitHub Actions builds and uploads the APK on every push that touches the app, and publishes it as the `latest` release when pushed to `main`.

### Signing

The release key is **not** in the repo; it was handed to you separately (`reps-release.jks` + `keystore.properties`). Add it before you install for the first time, so every later build installs as an update:

- **Locally**: put both files in a `keystore/` folder at the repo root (gitignored), or point `REPS_KEYSTORE_PROPERTIES` at the properties file.
- **GitHub Actions**: add repository secrets `REPS_KEYSTORE_B64` (`base64 -w0 reps-release.jks`), `REPS_KEYSTORE_PASSWORD`, `REPS_KEY_ALIAS` (`reps`) and `REPS_KEY_PASSWORD`.

Without a key the release build is signed with a throwaway debug key: it installs fine on a fresh phone, but Android refuses it as an update over an APK signed with a different key (uninstall first, then restore from the dashboard).

## Stack

Android: Kotlin 2.0, Jetpack Compose + Material 3, Room, DataStore, Navigation Compose, kotlinx.serialization. No analytics, no third-party services.
Server: Node 22, Express, `jose` for ID-token verification, file-based snapshot store, vanilla JS dashboard with inline SVG charts.

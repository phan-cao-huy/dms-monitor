# Android T1–T3 — 1.0.24 (build 22)

Uses the existing production HTTPS API, accounts, workspace permissions and database.
No database migration or duplicate Android-only notification rules are introduced.
All new screens support Vietnamese and English.

## Navigation

- Alerts → Rules: workspace defaults and custom device rules, numeric/slider thresholds,
  duration and cooldown. OFFLINE uses each device's offline delay; disk-health failures
  have a fixed condition. Existing device FCM delivery is preserved.
- Alerts → Email, or Tools → Email: Owner/Admin channel CRUD, Gmail/SMTP setup,
  explicit test send and delivery history. Editing without a password retains the
  stored password. SMTP acceptance does not guarantee inbox delivery.
- Device → Overview: physical disk health, temperature and wear when reported.
- Device → Network connections / Windows events: Agent snapshots, event level filters
  and cursor pagination. Unknown, unavailable and stale data are identified explicitly.
- Tools → Service checks: Owner/Admin PING/TCP/HTTP/SSL checks, run now, edit/pause/delete,
  recent results and incident/recovery history. Checks run from the server's network.
  Incident notifications use the existing email delivery policy.
- Device → Charts: seven resource metrics, 1h through 90d, timestamp-based plots,
  selectable samples and automatic source resolution. Missing samples remain gaps.
- Device → Settings & labels: offline delay (1–1440 minutes), label CRUD with server
  authorization (Owner/Admin or authorized group Manager); other users can read labels.
- Device list: label filters. Tools → Resource analytics: scoped time-range queries,
  aggregation, grouping and label filtering using server-side metric summaries.

Disk health, Windows events and connection data require Agent 1.3.0. Unsupported
sensors and unavailable Windows logs are not synthesized. Historical GPU/temperature
data only exists after the backend started retaining those measurements.

## Lifecycle and security

Foreground polling pauses when screens leave the STARTED lifecycle. Workspace changes
clear scoped lists and discard stale responses. New forms do not persist SMTP passwords;
validation errors never echo password-bearing request input. Existing command progress
wording and signed APK update validation are preserved.

## Validation, 2026-10-29

- `:app:assembleRelease :app:testReleaseUnitTest :app:lintRelease :app:lintDebug` passed.
- 20 unit tests passed; release lint reports 0 errors and 36 warnings.
- APK package `com.example.myapplication`, version 1.0.24 / 22, signed with the existing
  release certificate; SHA-256 fingerprint:
  `dd96fe9070f3737e1f469ddf57f748cff6a4898ae963fc70d5ee1538f1b42dc7`.
- APK SHA-256: `8fe24666618159d02eec5e99689953bb32a55ec48c7f983be212079c2d22f908`.
- No connected Android device was available. Installation, visual layout and real
  Gmail delivery from the new Android forms still require a phone check. Automated
  tests do not send email, kill user processes or create live service incidents.

The signed APK is served separately at `/static/dms-app.apk`; binaries and private
Firebase/signing credentials are not committed.

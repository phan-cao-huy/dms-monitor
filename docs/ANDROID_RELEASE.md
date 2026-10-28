# DMS Monitor release 1.0.20 (build 21)

## Application

The application uses https://dms.ky215.io.vn/api/v1/ exclusively. The login screen
has no server settings. Old saved server preferences are no longer read.
Cleartext HTTP is disabled. No trust-all certificates or TLS bypass is used.
The health/version endpoints responded with HTTP 200 using the application's
OkHttp User-Agent during verification. No authenticated account operation was run.

Release builds are not debuggable and disable HTTP request logging. Server APK updates are restored: REQUEST_INSTALL_PACKAGES and a narrowly scoped
FileProvider are present. Downloads are user-initiated, same-origin HTTPS, and
checked against expected version/size/package and the installed release certificate.
Accessibility/overlay permissions remain absent; authentication backup remains disabled. Package identity remains
com.example.myapplication; no identity changes were made to evade classification.
A production build is not a guarantee of MB Bank acceptance.

## Signing

A new RSA-3072 PKCS12 release keystore was created with user authorization:
C:/Users/NTK215/.android/dms-release/dms-monitor-release.p12

Alias: dms-monitor-release
Credential backup: C:/Users/NTK215/.android/dms-release/signing.properties
Public certificate: C:/Users/NTK215/.android/dms-release/dms-monitor-release-cert.pem
Gradle local configuration: mobile/keystore.properties (ignored by Git).

Passwords were generated randomly and are not stored in tracked sources or
printed in build commands. The key and credential files have explicit NTFS
permissions limited to the current Windows user and SYSTEM. Back up the keystore
and credentials securely; future updates must use this same signing identity.
Do not commit or publicly share the keystore or signing.properties.

The release key differs from the debug key used by previous APKs. Android will
normally require uninstalling the debug installation before installing this
release. This clears local sessions/settings; server-side account/device records
are not removed by uninstalling the Android client.

## Build and validation

Run from mobile/:

gradlew.bat :app:assembleRelease :app:testDebugUnitTest :app:lintRelease --offline --no-daemon

The single-use daemon avoids reusing an older daemon with cached/restricted access
to newly created signing files. Use apksigner verify --print-certs on the final APK
and inspect its manifest for versionCode=11, debuggable=false and
usesCleartextTraffic=false.

Output: mobile/app/build/outputs/apk/release/app-release.apk
The matching release APK is copied to backend/static/dms-app.apk and backend
metadata reports versionCode 11 / versionName 1.0.10. The Windows agent binaries
are also rebuilt from the corrected sources. Pushing these files does not rebuild
a deployed backend/worker image; use the deployment process after pulling.
Debug-signed installations must transition manually to the release key once.

## Web-aligned Android design (1.0.11)

Forest green, lime brand accent, neutral surfaces and a dark navigation bar match the web. Login and registration use a shared brand panel; the dashboard uses a two-column summary grid and outlined device cards. The launcher and in-app mark reuse the original web d monogram as Android vector resources. Wallpaper dynamic colors default off to keep brand colors consistent. Both light and dark palettes are supported.

Validation: release APK assembled, release unit tests and lint passed; apksigner verified the existing release certificate and aapt confirmed versionCode 12 / versionName 1.0.11. No visual verification on a physical Android device was performed.

## Alerts (1.0.12)

Server-backed rule creation and threshold editing on Android and web, with number inputs and sliders. Release build/test/lint pass; same release signing certificate. FCM client and high-priority data delivery are implemented but NOT configured or verified end-to-end. See [setup and current limitations](ALERT_NOTIFICATIONS.md). The local test notification does not prove server delivery.

## Firebase activation (1.0.13)

Android and worker use the same Firebase project. Google Services processed successfully and FIREBASE_CONFIGURED=true. Server FCM dry-run validation passed without sending a notification. The service account is outside Git with restricted local file ACLs, mounted read-only by the ignored local Compose override. Device receipt remains unverified until installation, sign-in and notification permission on a physical phone.

## Bilingual notification copy (1.0.14)

Push data contains Vietnamese and English titles and bodies. Android selects using SettingsManager.language; the web alert history offers a content-language selector. Copy identifies device, resource, measured value, comparison threshold and rule duration; offline/service/process alerts have separate wording. APK assembled, release unit tests/lint and signature verification passed; 20 focused backend tests and 11 web tests passed. No synthetic production push was sent for this copy change.

## Workspace terminology (1.0.15)

Web and Android use Không gian / Không gian làm việc, Nhóm thiết bị, Quyền truy cập. Workspace header/navigation use matching vector layout icons instead of the building emoji. API paths, identities and stored user names are unchanged.

## Update notices (1.0.16)

Android checks for a newer server version on foreground entry and every 15 minutes while started. Notice opens the existing verified downloader. Dismissal is per target version for the current app session. Only a strictly greater versionCode qualifies; downloaded APKs at or below the installed build are ignored. Profile refreshes installed/remote version on resume, including return from the installer. Downloading never marks a build installed. Web compares server web_version with WEB_VERSION and offers reload only when the build differs. Future web releases must update both version markers together.

## Device overview (1.0.17)

Android and web show status/freshness, active issues, CPU/RAM/GPU, every reported volume with used/free/total capacity, network, top processes and collapsed hardware details. Android adds nullable disk DTOs, maps created_at for history and cpu_pct for agent processes, and refreshes visible detail every 15 seconds. Missing telemetry is not treated as zero; estimated RAM GB is marked ~. Disk health/model/I/O are not claimed. Regression tests cover multi-volume parsing, zero GPU, missing CPU, timestamp and process CPU fields; web smoke tests cover all volume cards. No visual verification on a physical phone was performed.

## Actionable alerts and process ranking (1.0.18)

Android asks POST_NOTIFICATIONS once on first opening when not granted. VI/EN alerts name resource, actual usage, threshold, duration and top matching consumers in the latest snapshot; this is not proof of causality. History persists English text in message_en.

Web/Android sort CPU/RAM/GPU descending, select/hover rows, and color cells with increasing tint. CPU warm/hot: 20/50% of machine; RAM: 10/25% of system RAM; GPU: 40/80%. Missing GPU stays unknown. Snapshots do not claim to list all OS processes.

Agent 1.2.0 must be installed on PCs for normalized interval CPU, Windows per-process GPU busiest-engine counters, and union of CPU/RAM/GPU rankings. Download /static/dms-agent.exe or use the existing installer with a valid per-device install command in administrator PowerShell. Running agents were not automatically replaced. GPU counters are present on this host; full agent rollout and physical-phone UI verification remain unperformed.

Validation: backend/Postgres 64 passed; web 12 passed; Go tests and Windows GUI build passed; Android release build, tests and lint passed.

## Command result feedback (1.0.19)

Android maps command_id correctly and tracks the submitted command rather than leaving a static submission message. Sequential two-second polling stops on SUCCESS/FAILED/TIMEOUT; ordinary detail refresh continues after the five-minute fast polling window. Only agent SUCCESS reports a completed kill, with PID. Failures preserve the agent error; network failures do not fabricate success/failure. Web has corresponding polling, persistent result notice and terminal toast. Clearing workspace/logout cancels web tracking. Android refreshes processes after confirmed kill. Tests cover server ID mapping and pending/success/failure/timeout text. No real process was killed during validation.

## PID versus application stop (1.0.20)

Restores the requested pending/executing text Đang thực thi lệnh... in VI on web and Android. Observed command 91 successfully terminated Chrome child PID 285344; Chrome parent PID 283324 remained running. Existing PID behavior was working as scoped, not equivalent to ending every Chrome process. Dialog now explicitly offers whole-application stopping by exact executable name; PID remains default. Whole-app mode warns it affects all matching processes/windows across sessions and may lose unsaved work. The existing Agent name-based taskkill path is reused, requiring no additional Agent feature. API rejects paths/wildcards. Feedback distinguishes one PID from all named processes. Tests cover both payload scopes and results; no real browser was killed during validation.

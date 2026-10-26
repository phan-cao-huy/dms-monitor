# DMS Monitor release 1.0.10 (build 11)

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

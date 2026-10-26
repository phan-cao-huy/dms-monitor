# Android least-privilege update — v1.0.8 (build 9)

> Historical note: server-based APK updates were restored in v1.0.10 at the user's request. See ANDROID_RELEASE.md for current behaviour.

The reported MB Bank warning only says "malicious software". Its exact detection
reason has not been established. These changes remove unnecessary capabilities;
they do not hide DMS, alter its identity, or bypass bank/Android protection.

## Changes

- Removed REQUEST_INSTALL_PACKAGES, the package-install intents and unknown-source
  permission settings flow. Removed the APK downloader and FileProvider.
- Updates show version information and offer a user-initiated browser link.
  Update URLs must have the configured server's scheme, host and port, with no
  embedded credentials. Cross-origin links, scheme changes and downgrade links
  are rejected. HTTP remains supported for the existing LAN server; production
  hosting still needs HTTPS.
- Disabled application backup and explicitly excluded the authentication
  DataStore from cloud/device-transfer rules.
- Version is now 1.0.8 / 9. Package identity remains unchanged for data-preserving
  upgrades. APK permissions are inspected after building, not only in source.

## Verification / limitations

Debug/release builds, JVM tests and Android lint are checked. The signed debug
APK uses the same existing certificate as backend/static/dms-app.apk, so it can
be used to test an in-place upgrade of that build. It remains a development build,
not a production release. Release output is unsigned: a stable release keystore
is required before distributing a signed production APK. Do not publish a debug
build as a trusted production release or create a new package name to avoid a
security classification.

The server APK and version metadata are not changed by this patch; they must be
updated together after deciding on the release signing configuration. A different
signing key cannot normally upgrade the existing installation directly.

No MB Bank or Play Protect acceptance test was performed. No scan result or
false-positive classification has been confirmed. If the warning persists, use
MB's official support path to request review of the exact package/version and
certificate. Do not disable banking protections to use this build.

References:
- Android permission API: https://developer.android.com/reference/android/Manifest.permission#REQUEST_INSTALL_PACKAGES
- Google Play policy: https://support.google.com/googleplay/android-developer/answer/12085295
- MB's public accessibility guidance: https://mbstore.mbbank.com.vn/accessibility/

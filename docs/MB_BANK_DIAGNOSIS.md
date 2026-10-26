# MB Bank warning investigation — 2026-10-26

## Confirmed observations

The user reports that MB blocks while DMS Monitor is installed and works after
DMS is uninstalled. The screenshot category is "Remote Administration Tools".
That implicates DMS on this device but does not reveal the matching rule.

Inspected artifact: mobile/app/build/outputs/apk/debug/app-debug.apk
- Version: 1.0.8 / 9
- Package: com.example.myapplication
- debuggable: true
- Certificate subject: C=US, O=Android, CN=Android Debug
- Certificate SHA-256: fb40b5c765f40f2fab5588f080726806ed3bf8f9397f4014cc9e18f91fc810d9
- APK SHA-256: 9c4bb1b6ba683661a88f51e3259ff768a47f9e1ffc57b15e509e228e558dadf3
- APK MD5: c3d6de7a011f21b95877a3861f8b08a2

Final APK permissions: INTERNET, ACCESS_NETWORK_STATE, plus AndroidX's app-local
signature permission DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. No install-package,
accessibility, overlay, SMS, contacts or query-all-packages permissions were found.
No bundled APK/EXE/shell scripts were found. Native libraries are AndroidX graphics
and DataStore libraries. Source review did not find phone screen capture, remote
input, dynamic code loading or an accessibility service. This is not a complete
malware certification or a scan result.

## Concrete lead, not an established root cause

Cleafy's first-party Albiriox investigation lists malicious PENNY dropper samples
using the identical generic package com.example.myapplication:
https://www.cleafy.com/cleafy-labs/albiriox-rat-mobile-malware-targeting-global-finance-and-crypto-wallets

This package is also an ordinary Android sample identifier. A shared package name
does not imply shared code, certificate, behaviour or malware identity. No evidence
has been obtained that MB uses this particular report, blocks that package alone,
or that its classification is caused by the debug certificate. Package/reputation
collision is therefore a hypothesis for MB to investigate, not a proven diagnosis.
The prior installer permission hypothesis did not resolve the observed warning.

MB's public accessibility guidance does not publish its detection rules:
https://mbstore.mbbank.com.vn/accessibility/

## Limits and next evidence

ADB detected no attached phone, so the installed package/version/certificate and
bank diagnostic logs on the actual device could not be independently checked.
No APK was uploaded to a public scanning service. No changes to package identity,
certificate, bank software or bank security settings were made for this diagnosis.

An exact cause requires MB's detection evidence/review, or device diagnostic logs
that actually identify the matching package/rule. Provide the package, version,
APK hash, certificate hash and screenshot above when requesting review. A stable
production signing configuration remains necessary for release quality; it is
not established as the remedy for this warning.

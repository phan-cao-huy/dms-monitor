# Online devices with warnings — web/Android 1.0.30 (Android build 28)

The two reported online PCs had fresh heartbeats but API status `Warning`. Both
clients counted only exact `Online` values. The web Online filter also excluded
`Warning`, even though those devices were still connected and sending metrics.

Connected counts now include `Online` and `Warning`. The web Online filter uses
the same definition. Warning badges explicitly state online with a warning and
retain their warning color. The web KPI explains how many connected machines
have warnings; this is a subset, not an extra set of devices to add to the total.
The donut keeps mutually exclusive healthy-online / warning-online / offline /
paused segments. Android summary and warning card text follow the same convention.

Clients trust the API's computed status: no local heartbeat timeout or fake online
override was added. The server still marks stale/missing heartbeats Offline and
paused devices Paused. Alert evaluation, monitoring and permissions are unchanged.

## Verification

- Web syntax checks and 30 tests passed. Regression uses two Warning PCs and one
  Offline PC and expects two online devices. Paused/archived/unknown are excluded.
- Offline controller regression verifies the rendered KPI and Online filter with
  8 healthy + 1 warning: KPI is 9 and the warning device remains in the filter.
- Android regression checks warning counts and excludes paused/offline/archived.
- Android release build and 30 unit tests passed; debug/release lint 0 errors,
  36 warnings. Package/version/signature verified using the existing release signer.
  APK SHA-256: `d1f55e97fb877b1e6411c16e7a8b4501425d4009a591436043a58d22caf57b19`.
- No real phone or browser visual verification was performed.

## Manual acceptance

1. Open the workspace containing pc01/pc03, clear group/label/search filters.
2. While both send heartbeats, Online must include both even if RAM alerts persist.
3. Select Online: both must appear. Select Warning: only online warning devices appear.
4. The donut's exclusive counts must sum to the workspace total; warning is already
   included in the Online KPI and must not be added a second time.
5. On a test machine, stop Agent and wait past its offline threshold. Its API status
   and online count must change to Offline; restore Agent after testing.

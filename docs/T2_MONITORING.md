# T2 — Device details and service checks (web first)

Web 1.0.22, Agent 1.3.0. Android remains 1.0.20 at the user's request.
Backend and web changes use the existing database, users and workspaces.

## Web navigation

- Device overview: **Sức khỏe ổ đĩa vật lý**; separate from partitions and free space.
- Device detail: **Kết nối mạng** and **Nhật ký Windows** tabs, level filter and older-page cursor.
- Sidebar: **Kiểm tra dịch vụ** for Owner/Admin only; add/edit/delete, enable/disable,
  run now, last result, failure streak and incident/recovery history.
- Alert rules: **Sức khỏe ổ đĩa** with a fixed failure condition and editable duration/cooldown.

## Update the Windows Agent

Existing running Agents are not replaced automatically. Download:
https://dms.ky215.io.vn/static/dms-agent.exe

On each monitored computer, use an administrator account to stop the `DMS-Agent` service,
back up the installed executable, replace it with the downloaded Agent 1.3.0 and start the
service again. Keep `config.json` and `event-cursors.json`. The installer's default directory
is `C:\DMS-Agent`; check the service executable path if installed elsewhere.
No need to regenerate the device token or reinstall the Android APK.

The new Agent:
- Uses native WMI/COM for `MSFT_PhysicalDisk` in `root\Microsoft\Windows\Storage`, falling
  back to `MSStorageDriver_FailurePredictStatus` in `root\wmi`. This is a health summary,
  not a full SMART attribute reader. Unavailable/unknown health remains null; warning and
  unhealthy values are failures. Unsupported temperature/wear are omitted. Health is cached
  for 60 seconds; at most 32 disks are sent.
- Reads at most 50 TCP connection entries (ESTABLISHED/LISTEN), with PID/process and addresses.
  A null collection means unavailable; an empty list means no matching entries. This is not
  per-process bandwidth measurement and not necessarily the entire machine's connection list.
- Uses `wevtapi.dll` directly for new System/Application Error/Critical events and Security
  failed sign-in 4625. No PowerShell, CMD or child process is spawned for these collectors.
- Sends at most 48 events per pass (16 per log), messages limited to 1,000 Unicode characters.
  Provider-formatted text is preferred; provider/event data is a fallback if message resources
  are unavailable. First use starts at each log's current tail instead of uploading old history.
- Persists per-log record/time cursors atomically only after successful ingestion. Retries are
  deduplicated by device/log/record/timestamp on the server. Cleared logs restart from the tail
  when detected; rapid log rollover can still remove events before collection.
- Does not collect events while locally paused, and the server rejects ingestion while paused
  or archived. Log access failures are reported separately. Security may require service/admin
  privileges; unavailable logs do not stop other logs. On resume, retained cursors may include
  events that occurred during the pause; server retention still applies.

`DISK_HEALTH` rules require a fresh heartbeat and T2 snapshot no older than 120 seconds.
Unavailable data never counts as a disk failure. These are device alerts and use existing
FCM/email delivery. No new default disk health rule is silently added.

## Service checks

Checks run from the DMS **server**, not the Windows Agent. HTTP accepts http/https URL;
SSL accepts https URL; TCP uses host:port; Ping uses hostname/IP (IPv6 supported).
The Docker image includes Linux `iputils-ping`.

- Interval >=30 seconds, total probe deadline <=5 seconds, max 20 checks/workspace.
- HTTP reads only bounded response headers; no target body is returned or stored. 2xx/3xx
  are successful, with at most 3 redirects. Every hop is revalidated.
- DNS results are validated and a specific IP is pinned for the connection. HTTPS still
  verifies the original hostname/certificate. Loopback/link-local/multicast/metadata addresses
  are blocked; private targets require a server-managed CIDR allowlist. Broken DNS may be
  saved as a monitoring target; it fails until resolvable and allowed.
- `SYNTHETIC_ALLOWED_CIDRS` defaults to empty. For private homelab networks, set only the
  intended ranges, e.g. `192.168.10.0/24`, in the private server `.env`, then recreate backend
  and worker. Even an allowlist cannot grant loopback/link-local access. The administrator
  must make sure the server/VM actually has a route to that network.
- Incidents open after the configured N consecutive failures. SSL expiry within 14 days
  opens an incident immediately. A successful check closes the incident and resets the streak.
- Run now shares the same failure state and incident history and is limited to once/30 seconds.
  Explicit runs are allowed even for a paused check. Edits start a fresh evaluation window.
- CREATE/UPDATE/DELETE/RUN are audited. Target credentials and response bodies are not logged.
- Incident email uses the workspace's enabled Gmail/SMTP channels, in the channel language,
  with a separate ledger and max 3 attempts. An incident is not resent each polling cycle.
  Only incidents opened after channel creation, unresolved and <=24 hours old, are eligible.
  Existing device email/FCM statuses are unchanged. Service incidents are currently shown on
  the web service-check page; no Android/FCM service incident UI is added in this web-first task.
  SMTP acceptance does not prove inbox delivery; ambiguous SMTP disconnects can cause duplicates.

## Database and deployment

`database/migrate_t2_monitoring.sql` and identical bundled `app/db/t2_monitoring.sql` create
`device_events`, `synthetic_checks`, `check_results`, `check_audit`, `check_incidents`,
`check_email_deliveries`, indexes and deduplication constraints. Backend startup applies this
idempotently after T1. `database/dms01.sql` includes both migrations for fresh installations.

Back up PostgreSQL and `.env`, deploy/recreate backend first, then rebuild/recreate worker:
```
docker compose up -d --no-deps --build backend
docker compose up -d --no-deps --build worker
```

Retention cleanup uses bounded transactions: device events `EVENTS_RETENTION_DAYS` (default 7),
check results 7 days, resolved incidents 30 days and check audit 90 days. Open incidents remain.
Deleting a check removes its results/incidents/email ledger; audit records remain until retention.

## Validation and limits

Backend tests include PostgreSQL migrations applied twice, deduplicated ingestion, paused-device
rejection, scoped event reads, check API lifecycle, incident threshold/recovery, independent email
delivery and retention. Probe tests cover private/mixed DNS records, redirects to loopback, deadline,
input validation and Viewer rejection. Web tests cover safe rendering, unknown/stale data and forms.
Agent tests cover limits, Unicode truncation, persisted cursors, health values and XML parsing.

The optional native Windows read-only smoke test observed 2 disks, System/Application readable and
Security access denied in the test user's session. It does not establish support for all hardware,
drivers or Windows privilege configurations. No monitored user's running service was replaced.
No visual browser/phone QA or real incident email was performed for T2; transport is mocked in tests.

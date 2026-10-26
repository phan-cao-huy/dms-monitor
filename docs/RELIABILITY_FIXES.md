# Reliability fixes — 2026-10-26

These notes supersede the corresponding descriptions in PROJECT_CONTEXT.md.
Source changes are prepared and tested. Distribution binaries are rebuilt with
release 1.0.10; the deployed worker image still requires rebuilding after pulling.

## Behaviour

- Archived devices can authenticate while UNINSTALL is pending/executing, or for a five-minute
  replay window after SUCCESS to recover lost HTTP acknowledgements.
  They may fetch/report only that command; heartbeats are rejected. UNINSTALL
  does not expire after five minutes. Archive and command creation are atomic.
- Agent recursive cleanup is limited to the resolved dedicated installation
  directory (`C:\DMS-Agent` on Windows, `/opt/dms-agent` on Linux). Other locations
  fail safely. Cleanup launches only after a successful result response. SUCCESS
  means cleanup was scheduled; it cannot prove that files were removed.
- `/auth/users` retains a list response but contains only the current user's
  profile. Org member lists remain available through the org endpoints.
- Alert and alert-rule visibility follows assigned device groups. Existing
  access to ungrouped devices remains consistent with the device endpoints.
- Both monitoring endpoints update the persisted flag and queue the command in
  one transaction. Archived devices reject new control commands.
- Android leaves an alert unresolved when the server request fails and shows
  an error. Windows-only service code is excluded from Linux builds.

## Worker and alert rules

Run `python -m app.workers.runner` as a dedicated process. `EMBEDDED_WORKER=false`
is the default, and Compose explicitly disables it in the API. Standalone API
users can opt into `EMBEDDED_WORKER=true`. A PostgreSQL session advisory lock
allows only one scheduler; a monitor shuts it down if its lock connection fails.

The worker evaluates active CPU/RAM/DISK/NET_IN/NET_OUT/OFFLINE/SERVICE/
PROCESS_MISSING rules every minute. `alert_rule_states` persists the start of a
breach; gaps over two minutes reset continuity. Unknown/stale observations do
not trigger or resolve alerts. Duration, cooldown, recovery and maintenance
windows are honoured for configured rules. Explicit RAM rules replace the
built-in 90% RAM threshold. Existing built-in offline/RAM alerts remain available.

New agents request watch configuration in heartbeat responses. Active service
and process rules are merged with device watch_config. Process checks scan the
full process list; Windows services use SCM. Older agents cannot provide these
watch observations and must be updated for SERVICE/PROCESS_MISSING rules.

FCM delivery is optional and disabled by default (`FCM_ENABLED=false`). To enable,
configure `FCM_CREDENTIALS_FILE` and register client FCM tokens through the existing
endpoint. Delivery selects active refresh sessions with org/group access and
records SENT/FAILED. Tests mock Firebase; no real push was sent. Android Firebase
project setup/token registration and real-device delivery still require the
project's Firebase configuration; this patch does not supply credentials.

## Partitioning and retention

The worker creates the current and next Monday-aligned weekly partitions on
startup and daily. Existing matching rows in DEFAULT move atomically under a
table lock. Failure rolls back the move. DDL has lock/statement timeouts.
Historical Sunday-aligned partitions, if present in another deployment, require
an overlap review before using the new Monday boundaries.

Raw retention defaults to 7 days; hourly retention to 90 days. Cleanup deletes
expired rows in bounded batches, including legacy/default partitions. Empty
partition tables are retained. Set METRICS_RETENTION_DAYS and
HOURLY_RETENTION_DAYS before starting the new worker; startup prepares partitions
but does not immediately purge history.

`database/migrate_worker_reliability.sql` contains the explicit migration.
Worker startup creates the state table and installs the partition function.
API constraint migrations now execute in a single transaction.

## Validation and rollout

- Backend: run `ruff check .` and `python -m pytest -q` in backend/.
- PostgreSQL integration: set TEST_POSTGRES=1 and run
  `python -m pytest -q tests/test_postgres_reliability.py`. It creates a private
  schema inside a transaction and rolls everything back; it covers DEFAULT
  movement/idempotence, duration/recovery/cooldown, retention and uninstall auth.
- Agent: `go test ./...`, `go vet ./...`, Windows GUI and Linux builds.
- Android: `gradlew.bat :app:assembleDebug :app:lintDebug`. local.properties must
  escape the Windows drive colon (e.g. `sdk.dir=C\:/Users/.../Android/Sdk`).
  This machine-specific file is ignored by Git.

To activate, rebuild the backend/worker image, run the new worker, rebuild and
redistribute the agent and APK. Increment Android versionCode/versionName and
server update metadata together before publishing an APK. Inspect retention
settings and take the deployment's normal backup before enabling cleanup.

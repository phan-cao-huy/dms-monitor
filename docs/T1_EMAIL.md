# T1 — Email notifications (web first)

Initial web version: 1.0.21. Android support added in 1.0.24 / build 22;
see [Android rollout](ANDROID_T1_T2_T3.md). No Agent update is required for T1.
Telegram/webhook are deferred. Existing FCM delivery is unchanged.

## Setup

1. Back up PostgreSQL and the server `.env`.
2. Set `NOTIFICATION_ENCRYPTION_KEY` in `.env` to a Fernet key, generated once:
   `python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"`
   Keep this key outside Git, backed up with the database. Never replace it while encrypted
   channels remain in use. The local workspace key has already been generated privately.
3. Start/recreate the backend before starting the new worker. Backend startup applies
   `app/db/t1_email.sql` transactionally, including the idempotent seed for existing workspaces.
   Equivalent operator migration: `database/migrate_t1_email.sql`.
4. `docker compose up -d --no-deps --build backend`, then
   `docker compose up -d --no-deps --build worker`. Recreate is necessary after `.env` changes.
5. Open **Cảnh báo → Email** as Owner/Admin. Refresh cached web assets if necessary.

## Gmail

- Enable Google 2-Step Verification and create an app password at
  https://myaccount.google.com/apppasswords (availability depends on account policy).
- SMTP host: `smtp.gmail.com`; port `587` (STARTTLS), alternatively `465` (SSL/TLS).
- Username and From email: the Gmail address. Password: the app password, not the Google
  account password. Paste without display spaces.
- Add up to 20 recipients, one per line. Choose Vietnamese/English for this channel.
- Save, then explicitly choose **Gửi thử**. Check the spam folder as well.
- `SENT` means SMTP accepted the message, not proof it reached the inbox.

Reference: https://support.google.com/mail/answer/185833

## Behaviour

- Owner/Admin only: list, create, replace, delete, test and delivery history. Every lookup
  is scoped to the workspace and excludes deleted workspaces. SMTP passwords are never
  returned by the API; all channel configuration is encrypted at rest.
- TLS is mandatory; port 587 upgrades with STARTTLS before authentication, 465 uses SSL.
  Socket timeout is 10 seconds. Test emails are limited to once per channel per 60 seconds.
- Worker runs every minute. It considers unresolved alerts from the last 24 hours, created
  after the channel was created. It excludes archived/paused devices and deleted workspaces.
- Delivery ledger is unique per alert/channel. Success is not retried. Failed attempts are
  retried on a later worker tick, with a maximum of 3 total attempts. Phone notification
  status is separate. Tests do not create alert delivery records.
- SMTP cannot guarantee exactly-once delivery after an ambiguous disconnect or process crash.
  A crash after SMTP acceptance but before recording success can produce a duplicate.
  Partial recipient rejection can also cause retries for recipients who already accepted.
- Disabling a channel pauses sending, including pending retries. Deleting a channel also
  deletes its delivery history. Re-enabling can resume eligible pending deliveries.

## Default rules

Five workspace-scoped defaults are seeded once, including workspaces with no devices:
CPU >90% for 5 minutes; RAM >90% for 5 minutes; DISK >90% for 1 minute;
OFFLINE using each device's `offline_threshold_minutes` (1-minute evaluation window);
CPU temperature >85°C for 1 minute. Missing/unavailable temperature is never a failure.

Defaults apply to existing and future devices. Active device/group rules for the same
metric override its default on that device. Editing/disabling defaults survives reseeding.
Deleting a default disables it rather than recreating it at the next server startup.
Legacy immediate RAM alerts and offline alert creation yield to workspace defaults,
including disabled defaults. Device online/offline status tracking remains independent.
`DISK_HEALTH` is reserved in the database constraint for T2, with no collector or rule UI yet.

## Validation

Backend tests cover SMTP TLS/auth/timeout handling, encrypted credentials, Owner/Admin
permissions, workspace scoping, email retries independent of FCM and default observations.
Web tests cover editor payloads, secret omission, escaping and bilingual email views.
PostgreSQL integration tests are opt-in with `TEST_POSTGRES=1`; use a test database.
No real Gmail message is sent by automated tests. Configure Gmail and send an explicit test
before relying on this channel.

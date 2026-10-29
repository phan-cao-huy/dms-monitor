# Alert rule scope and uniqueness — web/Android 1.0.25

Each device has one effective rule for a numeric component (CPU, RAM, GPU, DISK,
CPU_TEMP, NET_IN, NET_OUT, OFFLINE, DISK_HEALTH). For named SERVICE and
PROCESS_MISSING rules, each distinct watched name is a separate component.

An enabled device rule takes priority over an enabled group rule, then the workspace
default. A disabled override falls back to the next enabled scope. Disabling an
inherited default affects devices without enabled overrides. It does not turn off
their explicit device rules.

API creation/activation rejects a second enabled rule for the same scope/component
with HTTP 409 and `RULE_COMPONENT_EXISTS`. Web and Android explain that the user
should edit the existing rule. Organization row locks serialize writes and partial
unique PostgreSQL indexes protect concurrent/direct inserts. Disabled configurations
may remain for history, but only one can be enabled at the same scope.

`unique_rules.sql` runs transactionally at backend startup. It keeps the newest
enabled custom rule when legacy duplicates exist, disables the older duplicates,
and preserves their rows and alert history. It is safe to rerun. Corresponding
operator migration: `database/migrate_unique_rules.sql`.

The evaluator independently chooses one winner per component. Shadowed/disabled
evaluation states are removed and their unresolved alerts retired on the next
evaluation pass, preventing stale windows from resurfacing when scope changes.
Both email and FCM senders recheck effective scope before sending, so an obsolete
rule cannot send merely because the evaluation job has not run yet.
Editing a rule resolves its existing alerts and resets its continuous observation
window; a new alert needs a fresh complete duration. Cooldown still applies to the
rule's history. Existing sent notifications are not retracted from phones/inboxes.

Rule lists exclude archived devices. Both clients have a device selector showing
only effective rules and display device IDs to distinguish repeated names. The
all-configurations view retains defaults, overrides and explicitly disabled rules.

## Incident checked on 2026-10-29

The active pc1 (device 29) had a device RAM rule >=50%, a workspace fallback >90%,
and a rule for an older archived pc1 (device 25) remained in the unfiltered list.
The >=50% rule did create alert 122. In earlier alert 119, 93% was the measured
RAM, while the threshold was 90%. This does not by itself prove successful phone
delivery; those records had FCM status FAILED when inspected. A Firebase dry run
confirmed two UnregisteredError tokens and one valid token. The sender now clears
only confirmed unregistered FCM tokens, preserves auth sessions, and records SENT
when valid delivery succeeds without transient failures. Other errors remain retryable;
if every token is invalid, no success is fabricated. Delivery status and threshold
evaluation are separate concerns.

## Verification

- Backend suite: 131 passed, 3 repository-copy checks skipped in the backend-only
  container; PostgreSQL integration tests enabled using rollback-only private schemas.
- Added live-database regression: a 50% device rule fires at 70% RAM even when a
  93% inherited rule exists; inherited history resolves and its state is removed.
- Migration rerun, duplicate creation/activation, database uniqueness and exclusion
  of archived devices covered by PostgreSQL integration tests.
- Expired FCM token mixed with successful delivery, all-expired tokens and transient
  failure cases covered. The rebuilt worker image also passed the complete suite.
- Following deployment, existing pc1 RAM alert 122 (threshold 50%) reached SENT
  with Firebase acceptance recorded. Receipt on the phone still requires user confirmation.
- Web: 27 tests passed, syntax checks passed.
- Android: release build and 24 unit tests passed; debug/release lint 0 errors,
  36 warnings. Phone installation and visual checks remain pending.
- APK 1.0.25 / build 23, same release signer. SHA-256:
  `19b7e203c8e060a1d6b102f6ecf3658b165d27b37810331f47d198f9cd555643`.

## Manual acceptance

1. Select the active pc1 by ID in Rules. Confirm exactly one RAM rule is shown.
2. Try creating another enabled RAM rule for that same device: saving is blocked;
   a concurrent API request must return 409 rather than create a duplicate.
3. Edit the existing RAM threshold, reopen web/app, and verify the same value.
4. On a test machine, keep RAM above the threshold for the configured duration;
   verify a new alert after the observation window (and applicable cooldown).
5. Disable the custom RAM rule: the effective list must show its enabled group
   rule or workspace fallback. Re-enable it: it must be the sole effective RAM rule.
6. Confirm archived pc1 configurations are absent and other workspace/device
   rules remain independent. Restore test thresholds after testing.

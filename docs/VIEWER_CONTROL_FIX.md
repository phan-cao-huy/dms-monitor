# Viewer device permissions — web/Android 1.0.31 (Android build 29)

The reported user had workspace Viewer membership but group Manager access on the
target device. Previously the group role replaced the workspace role during device
authorization. Two successful kill commands were found for that combination.
Android also rendered process/reboot controls without checking the effective role.

Workspace Viewer is now a read-only ceiling for device control and modification.
An existing group Manager entry cannot elevate a workspace Viewer. Owner/Admin
retain control. Workspace Manager can control ungrouped devices or devices in a
group where they also hold Manager. A group Viewer restriction still limits a
workspace Manager. Reading still follows existing membership/group visibility.

The shared server access check rejects device mutations with HTTP 403 before
creating a command or committing changes: PID/application kill, reboot, service
restart, pause/resume/uninstall, settings/group changes, archive and label writes.
Web and Android use the same matrix to gate controls. Android hides kill/reboot/
delete controls, disables group/monitoring edits and gates the send callback.
Command progress wording is unchanged.

No memberships were rewritten or deleted. To intentionally delegate control,
Owner/Admin must grant workspace Manager and the appropriate group Manager role.
Viewer is not a control delegation, even if an old group Manager row remains.

## Verification

- Backend: 150 tests passed with PostgreSQL enabled; 3 repository-copy checks skipped
  in the backend-only container. Direct HTTP regression tests verify Viewer + group
  Manager cannot queue any supported device command or mutate settings/labels/archive.
- Role matrix tests preserve Owner/Admin and authorized Manager behavior; Viewer
  read access remains allowed. Tests do not execute any real process kill.
- Read-only verification against the reported account/device after deployment:
  device control returns 403; device read remains allowed. No pending/executing
  Viewer commands were found when checked.
- Web syntax checks and 32 tests passed, including the role matrix.
- Android release build and 32 unit tests passed; debug/release lint: 0 errors,
  36 warnings. Package/version/signature verified with the existing release signer.
  APK SHA-256: `cff721913f198175ac1736902e5368f6afb57d1de9e8f695f794771f61b5692e`.
- No connected phone or browser visual verification was performed.

## Manual acceptance

1. Sign in as workspace Viewer with a legacy group Manager assignment. Open the
   device's process list: no kill button. Commands history stays readable; reboot
   and delete controls are absent. Group/monitoring changes are unavailable.
2. A direct POST command request with that account must return 403 and create no row.
3. Verify Owner/Admin still have controls. Use a disposable test process if checking
   command execution; do not kill an active user browser to test permissions.
4. Verify workspace Manager + group Viewer cannot control; Manager + group Manager can.
5. Change roles while detail is open: the server must enforce current roles even
   before the client's next refresh updates control visibility.

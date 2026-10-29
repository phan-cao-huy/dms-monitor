# Join workspace selection — web/Android 1.0.28 (Android build 26)

An invited user who already owned a personal workspace could join successfully
but continue seeing the personal workspace's members/devices. Dashboard refresh
preferred its current workspace over the newly joined ID. Independent organization
requests could then overwrite newer scoped results and persisted selection.

Android now explicitly selects the ID returned by join/create in both dashboard
and organization state. Scoped lists and filters clear during transition. A pending
explicit selection cannot silently fall back to a personal workspace if an older
workspace list arrives. Organization refresh uses one cancellable generation of
requests; delayed group-member results are also discarded after scope changes.
Dashboard selection synchronizes the organization panel, including device-tab switches.

The web already selected the joined workspace, but did not reset previous device
filters/data. It now uses the same reset path as a manual workspace switch and
opens the joined workspace's member list. Backend permissions are unchanged.

Viewer membership allows reading the member list and ungrouped devices. Devices
inside restricted groups require Owner/Admin to grant group access. Joining never
silently promotes a user to Admin or grants all group permissions.

## Verification

- Backend: 134 tests passed, PostgreSQL enabled, 3 repository-copy checks skipped
  inside the backend-only container. New rollback-only integration test joins a
  Viewer who already owns a personal workspace, reads both shared members, verifies
  visibility of ungrouped devices, then grants group access and verifies that device
  becomes visible without exposing devices from the personal workspace.
- Web syntax checks and 27 tests passed. Distribution synchronized to the backend.
- Android tests cover explicit joined selection, stale lists, normal manual selection
  and saved workspace restoration. Phone installation/visual checks remain pending.
- Android release build and 28 unit tests passed. Debug/release lint: 0 errors,
  36 warnings. APK package/version/signature verified; same release signing identity.
  APK SHA-256: `fa8a71055b6c7506c552692a5c7885ce87b07d63035d9f94c1742485884d2c86`.

## Manual acceptance

1. Sign in as a user who owns a personal workspace; keep it selected.
2. Join another workspace using a Viewer invitation. The header must switch to the
   invited workspace, member list must show inviter and invitee, and Devices must
   show devices permitted to this account in that same workspace.
3. Wait through automatic refresh; navigate Devices → Workspace → Tools. The chosen
   workspace must remain consistent. Switch back to the personal workspace, then
   return to the invited one; no old member/device list may flash or remain.
4. For a restricted group, grant this Viewer access as Owner/Admin. Refresh the
   invitee's app/web and confirm the authorized group/device appears.
5. Repeat with a device search/group filter active before joining; old filters
   must not hide devices in the invited workspace. Repeat with slow networking.
6. Users who joined before this fix should select the shared workspace from the
   header dropdown once; do not leave/rejoin or duplicate memberships to recover.

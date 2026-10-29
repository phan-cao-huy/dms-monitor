# Group permission editor and End task — web/Android 1.0.32 (Android build 30)

## Group editor

Selecting an existing member now initializes the editor from their stored group
role instead of resetting it to Viewer. A new assignment starts at Viewer.
Save is disabled when an existing assignment is unchanged. The web table separates
the stored group role from effective device permissions. Android group rows and
the assignment dialog explain both scopes too.

Workspace Viewer remains read-only, even when a legacy group Manager assignment
exists. The editor explains that selecting group Manager alone does not grant
device control. No membership is automatically promoted/downgraded by opening
the editor, and the server permission fix from 1.0.31 remains in effect.

## End task

The old default targeted one PID, which can be a disposable child/renderer of a
multi-process application. Ending that PID does not necessarily close its parent
window and the application can replace the child.

The default is now **Force close application / End task**. It submits an exact
`process_name` and no PID, using the existing Windows Agent execution path:
`taskkill /F /T /IM <exact executable name>`. It force-terminates matching processes
and their children. The dialog identifies the application, explains scope across
matching processes/sessions and warns about unsaved work. Explicit PID-only mode
remains available; it uses `/F /T /PID`.

Agent production code/version is unchanged. No new Agent installation is required
for an Agent already supporting this path. Apps/services that independently relaunch
themselves can still return; End task is not a disable-autostart operation.
Existing **Đang thực thi lệnh...** progress wording, final-result tracking and
Viewer restrictions are preserved.

## Verification

- Web syntax checks and 35 tests passed: existing Manager preselection, unchanged
  detection, effective Viewer/Manager permissions and End task scope ordering.
- Windows executor package tests passed. A uniquely named copy of the test binary
  spawned a child; the actual Agent `execKillProcess(process_name)` path terminated
  both parent and child, verified using Windows process handles. Cleanup targets
  only that random fixture executable. Chrome and user applications were not killed.
- Phone installation and visual checks were not performed.
- Android release build and 32 unit tests passed, debug/release lint 0 errors,
  36 warnings. Package/version/signature verified against the existing release signer.
  APK SHA-256: `6a9f2945a36ad4551264f866e67f305c955ddf9ef088aee6134803f31f5c99bc`.

## Manual acceptance

1. Open the reported group. Select the existing Manager: editor shows Manager and
   Save is disabled. Select Viewer intentionally: Save enables. Cancel leaves data unchanged.
2. Select another member: their own current role loads, not the previous selection.
3. For workspace Viewer/group Manager, stored Manager is visible but effective
   permission says read-only. No kill/reboot control should appear for that account.
4. As an authorized Manager/Owner/Admin, use a disposable test application. Open
   End task and verify whole-application mode is selected; confirm and check all
   matching test processes/window exit. Do not use an active work browser for this test.

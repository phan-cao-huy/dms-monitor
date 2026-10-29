# Device group assignment — Android 1.0.27 (build 25)

The shared PATCH `/api/v1/devices/{device_id}` accidentally declared an untyped
`ToggleMonitoringIn` argument. FastAPI interpreted it as a required query parameter,
rejecting normal JSON requests from web/Android with HTTP 422 before updating the
device. The unrelated parameter is removed; monitoring uses its separate endpoint.

Android also serialized an unassignment request as an empty object because Gson
omits nulls. It now explicitly sends `group_id: 0`, consistent with the web and
legacy API. The API also supports explicit JSON null for removal. Omitting
`group_id` entirely preserves the device's group when editing other settings.

The change applies immediately to web through the shared backend; no web assets,
database migration or worker deployment are required. Android 1.0.27 adds the
explicit unassignment payload. Existing source authorization and same-workspace
destination checks remain enforced.

## Verification

- Backend: 133 tests passed with PostgreSQL integration enabled; 3 repository-copy
  checks skipped in the backend-only container. Test data used private rollback schemas.
- Real HTTP regression verifies PATCH succeeds without the unrelated query parameter.
- PostgreSQL regression verifies assign A → move B → preserve B on unrelated edit →
  remove using 0/null; foreign-workspace destination returns 404, Viewer returns 403.
- Public OpenAPI verified: PATCH parameters contain only the device path ID.
- Android regression checks Gson sends a numeric group ID and explicit zero on removal.
- Android release build, 25 unit tests, debug/release lint passed (0 errors,
  36 warnings each). APK package/version/signature verified against the existing
  release identity. APK SHA-256:
  `7f7f94110192b1311ba1a0a1b9c3a9bd82f5547046bf9402e0115cf789e266ba`.

## Manual acceptance

1. As Owner/Admin, create groups A/B and choose a test device.
2. Web: edit the device → select A → save; reopen and confirm A.
3. Android: move that same device to B; check both detail and dashboard, then web.
4. Android: choose **Chưa phân nhóm**; both clients must show no group.
5. Assign again, then edit only the offline delay; group must remain unchanged.

Do not use a live permission-restricted group for a destructive manual test. Phone
installation and visual checks were not performed; no connected Android device.

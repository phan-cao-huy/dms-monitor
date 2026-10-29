# Label editor — web/Android 1.0.29 (Android build 27)

Reported input `KT01_` / `phong` kept Android's Save button disabled because the
API uses lowercase keys matching `[a-z][a-z0-9_]{0,31}`. The old form gave no
specific reason beyond a short field label.

Both clients now normalize key input to lowercase and trim surrounding whitespace,
so this example saves as `kt01_: phong`. Stored keys and filter syntax remain
compatible with the existing API/database; no migration or permission change.

Android shows the key format, error styling, a Unicode character counter and a
specific value error for >64 characters or control characters. A new label that
collides with an existing normalized key prompts editing instead of silently
overwriting it. Editing an existing label keeps the key read-only and explains it.

Web normalizes during input and before submission, shows inline validation and
blocks invalid form submission with the reason. Values use the API's 64 Unicode
code point limit instead of the browser's UTF-16 maxlength limit. The existing
normalized-key duplicate check preserves the original label value on accidental
creation; users can explicitly edit that label.

## Verification and manual acceptance

- Web syntax and 28 tests passed, including the reported example, invalid initial
  digit/internal space, 33-character keys, control characters and Unicode value limits.
- Android contract tests cover the reported example without relaxing strict stored
  key validation. Existing permission enforcement is unchanged.
- Android release build and all 28 unit tests passed. Debug/release lint reports
  0 errors and 36 warnings. Package/version/signature verified with the existing
  release signer. APK SHA-256:
  `9befa4e59c4f3ec53b48c10ba124d693dc7d671f3d038549cd4575281fe34729`.
- On both clients, add `KT01_` with value `phong`: expect `kt01_`, enabled save and
  the stored label after refreshing/reopening. Edit its value and verify web/app sync.
- Try creating `KT01_` again: expect a prompt to edit the existing label, no overwrite.
- Try `01_KT`, an internal space or a 65-character value: expect a visible reason.
- Editing an existing label must allow changing its value while keeping its key fixed.
- Phone visual/installation checks were not performed; no connected Android device.

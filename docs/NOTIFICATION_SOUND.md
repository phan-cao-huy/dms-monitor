# Android notification sound — 1.0.26 (build 24)

Device alerts and the local test use a stable high-importance channel,
`dms_alerts_sound_v1`, with the system notification sound and notification audio
attributes. Vibration permission is declared. Global notification permission,
in-app alert switches, phone volume, silent mode and Do Not Disturb still apply.

This is a one-time channel migration from `dms_alerts`, not a new channel on each
release. Android does not allow an app to change an existing channel's sound
behavior after creation. The new channel's future user preferences remain under
system control; the app does not delete/recreate it to reset those preferences.
See [Android notification channels](https://developer.android.com/develop/ui/views/notifications/channels).

The phone notification card shows the channel sound configuration and opens that
specific channel's Android settings. Copy supports Vietnamese and English. Its
sound status describes channel configuration, not a measurement of audible output.

Local tests cancel only the previous test notification before posting another,
allowing a new alert sound instead of updating the same notification silently.
Real alert IDs still use `setOnlyAlertOnce(true)` to avoid repeated sounds when
the same alert is updated. OS rate limits/cooldowns can still suppress rapid tests.

## Manual checks on a phone

1. Update from 1.0.25, open DMS and allow notifications if prompted.
2. With notification volume above zero and silent/DND disabled, open the phone
   notification card and send a test: expect a tray notification and audible tone.
3. Dismiss it, wait a few seconds and test again: expect another audible alert,
   subject to the phone's notification cooldown settings.
4. Open **Cài đặt âm báo / Notification sound settings**, change the tone and
   return to DMS. Test again; the chosen tone should apply.
5. Set that channel to silent: the card must show silent and later notifications
   must respect it. Restore sound afterwards.
6. Trigger a new real RAM alert after its duration/cooldown: check sound while
   DMS is in the background. This verifies FCM as well as local posting.
7. Disable app notifications, or use silent/DND: verify DMS respects system policy.
8. Relaunch DMS after customizing the channel: user sound preferences must remain.

No connected phone was available for acoustic or visual verification. APK is
published separately at `/static/dms-app.apk` and uses the existing release signer.

Release build and 24 unit tests passed; debug/release lint each reports 0 errors,
36 warnings. Package `com.example.myapplication`, version 1.0.26 / 24. APK SHA-256:
`91a281a736e6dad36d53123b91d693119044aca2b6a4fcebc17de47c8c469da1`.

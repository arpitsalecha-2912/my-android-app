# My Android Apps

This repo builds **multiple Android apps**, each installed separately on your phone. Every push to `main` builds and signs them automatically.

## Get the apps (on your phone)

Open the **Releases** page and download from the latest build, or use these direct links:

- **WiFi Scanner** — see your Wi-Fi details, find devices on the network, full phone info, prank buddies:
  `releases/download/latest/wifi-scanner.apk`
- **Loud Alarm** — one Start button: full-volume alarm + lock screen; unlock to Stop:
  `releases/download/latest/loud-alarm.apk`

Tap the file → allow "Install unknown apps" → Install. Both are signed with the same key, so new builds install over the old app as updates.

## Apps in this repo

| App | Module | Package |
|-----|--------|---------|
| WiFi Scanner | `app` | `com.arpit.myapp` |
| Loud Alarm | `alarm` | `com.arpit.alarm` |

To add another app later: add a new module folder, `include(":name")` in `settings.gradle.kts`, and two copy lines in `.github/workflows/build-apk.yml`.

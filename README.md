# Money Tracker

A personal expense tracker for Android. Reads your bank transaction SMS on-device to log every debit and credit automatically, with a dashboard, categories, and reports. All data stays on your phone — nothing is uploaded.

## Install

Open on your phone: **Releases → Latest build → `money-tracker.apk`**, or the direct link:
`releases/download/latest/money-tracker.apk`

Tap the file → allow "Install unknown apps" → Install. Each push to `main` builds a new signed APK at the same link, so it installs over the old one as an update.

## Features
- Auto-import transactions from bank SMS (read-only, on-device)
- Add transactions manually
- Dashboard: month spend, income, net, spending by category, recent list
- Reports: last-6-months trend, category donut, top places
- Auto-categorisation (Food, Shopping, Fuel, Bills, Travel, and more)

## Permissions
- `READ_SMS` / `RECEIVE_SMS` — only to detect bank transaction messages. Parsing happens locally; messages are never sent anywhere.

## Build
`com.arpit.expense`, module `app`. CI builds and signs the APK on every push.

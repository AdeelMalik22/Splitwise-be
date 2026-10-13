# Splitwise Android app

Native Android client (Kotlin, Jetpack Compose, Material 3) for the Django API in this repo.

## Architecture

- `data/` — Retrofit API interfaces, kotlinx.serialization models, DataStore-backed `TokenStore`, OkHttp
  auth interceptor + 401 `Authenticator` that refreshes the JWT, and `Repository` (maps failures to
  readable messages).
- `ui/` — ViewModels exposing `StateFlow` state (`Load<T>` = loading / error / ready) and stateless Compose screens.
- `MainActivity` — session-driven root: login screen when signed out, navigation graph when signed in.

## Features

Register / sign in, groups, group detail, multiple-payer expenses, categories,
search and expense filters, balances, settlement suggestions, monthly and
category reports, invites, notifications, profile editing, fingerprint login,
offline reads, and queued offline expense/payment writes.

On the Home screen, tap the profile avatar to open Account. Account contains
profile settings, reports, password changes, local logout, and the Danger Zone
with “Log out all devices”.

Expense filters are shown above the expense list. The list scrolls separately
from the filter controls. Category filters can be hidden with “Hide category”
and restored with “Show category”.

## Run

1. Start the API: `python manage.py runserver` (optionally `python manage.py seed_demo_data`;
   demo password `SplitwiseDemo!2026`).
2. Open `android/` in Android Studio and run on an emulator (debug builds call `http://localhost:8000/`, forwarded to your computer with `adb reverse tcp:8000 tcp:8000`),
   or build from the CLI: `cd android && ./gradlew :app:assembleDebug`.

For a physical device or a deployment, change `API_BASE_URL` in `app/build.gradle.kts`
(release builds must use HTTPS; cleartext is only enabled for debug).

## Local caching and offline writes

Room stores user-scoped groups, expenses, members, balances, notifications,
activity, invites, payments, and overview data. Cached data is shown while the
backend refreshes in the background. New expenses and payments created without
network access are stored in a user-scoped Room queue and replayed after the
next authenticated refresh. Logging out clears that user's cached data and
pending writes.

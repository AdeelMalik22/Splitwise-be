# Splitwise Android app

Native Android client (Kotlin, Jetpack Compose, Material 3) for the Django API in this repo.

## Architecture

- `data/` — Retrofit API interfaces, kotlinx.serialization models, DataStore-backed `TokenStore`, OkHttp
  auth interceptor + 401 `Authenticator` that refreshes the JWT, and `Repository` (maps failures to
  readable messages).
- `ui/` — ViewModels exposing `StateFlow` state (`Load<T>` = loading / error / ready) and stateless Compose screens.
- `MainActivity` — session-driven root: login screen when signed out, navigation graph when signed in.

## Features

Register / sign in, groups, group detail (expenses, balances from `/expense/<id>/settlements/`, members),
add equal-split expense, invite users by username, accept/decline invites, notifications.

## Run

1. Start the API: `python manage.py runserver` (optionally `python manage.py seed_demo_data`;
   demo password `SplitwiseDemo!2026`).
2. Open `android/` in Android Studio and run on an emulator (debug builds call `http://localhost:8000/`, forwarded to your computer with `adb reverse tcp:8000 tcp:8000`),
   or build from the CLI: `cd android && ./gradlew :app:assembleDebug`.

For a physical device or a deployment, change `API_BASE_URL` in `app/build.gradle.kts`
(release builds must use HTTPS; cleartext is only enabled for debug).

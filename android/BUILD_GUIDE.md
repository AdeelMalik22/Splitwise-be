# Building SplitEase for normal use

## Requirements

- Android Studio with an Android SDK and JDK 17
- A working internet connection for Gradle dependencies
- The API available at `https://15.134.231.253/`

Run all commands from the `android/` directory.

## Build an installable release APK

```bash
./gradlew :app:assembleRelease -PapiBaseUrl=https://15.134.231.253/
```

The APK is created at:

```text
app/build/outputs/apk/release/app-release.apk
```

To place a copy in the repository root for sharing:

```bash
cp app/build/outputs/apk/release/app-release.apk ../splitease-release.apk
```

Install it on a connected Android phone with:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Or install the root copy from the repository root:

```bash
adb install -r splitease-release.apk
```

The phone must allow installation from the source used to share the APK.

## Build a debug APK

```bash
./gradlew :app:assembleDebug -PapiBaseUrl=https://15.134.231.253/
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Check the connected phone

```bash
adb devices
```

The phone should appear with the status `device`. Enable USB debugging and accept the authorization prompt if it does not appear.

## Important signing note

The current release build uses the local Android debug signing key so the APK can be installed directly for testing and private sharing. A Play Store or public production release needs a permanent private release keystore. Keep that keystore and its passwords backed up securely, then configure it in Gradle before publishing.

## Changing the API URL

The API URL is supplied at build time:

```bash
./gradlew :app:assembleRelease -PapiBaseUrl=https://your-api.example.com/
```

Include the trailing slash because Retrofit requires the base URL to end with `/`.

## Verification

Run Android unit tests and a debug build with:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

For backend verification from the repository root:

```bash
python manage.py check
DJANGO_TEST=true python manage.py test
```

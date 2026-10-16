# Grok Build steps — Starfront Command M1 APK

## Option A: build from this zip

1. Download `starfront-command.zip` and unzip it. You get a `starfront-command/`
   folder containing `settings.gradle`, `build.gradle`, and the `app/` module —
   a standard Android Gradle project (package `com.wilson.starfront`).
2. In your Grok Build account, create a new Android project and upload the zip
   (or point it at the unzipped folder), the same way you did for the launcher
   APKs.
3. Build settings to confirm:
   - `compileSdk 34`, `minSdk 26`, `targetSdk 34`
   - Java 17 / `jvmTarget 17`
   - Android Gradle Plugin `8.5.2`, Kotlin `1.9.24`
   - No Gradle wrapper is bundled — let Grok Build / Android Studio use its own
     Gradle distribution (8.7+) for the first sync.
4. Build the **release** APK: `app-release.apk`.
   **Signing:** the project is wired for release signing via `keystore.properties`
   in the project root (already filled in, gitignored). Before building, drop
   `starfront-release.p12` into the unzipped project root folder, next to
   `keystore.properties`. Every release build Grok makes this way carries the
   same signature, so new APKs install as updates over the old one — no
   uninstall needed. (If `keystore.properties` is missing, the build falls back
   to the debug key instead.)
5. Install on the Onn tablet:
   - Copy the APK to the tablet (USB, Drive, or direct download).
   - Open it and allow "Install unknown apps" for the file manager / browser.
   - The game launches in landscape, fullscreen.

## Option B: build from GitHub

Repo: `https://github.com/wilsonsamiano/starfront-command`

Point Grok Build at the repo URL instead of the zip — same build settings as
above. Pull/re-clone to pick up new commits.

## Smoke test on the tablet (2 minutes)

1. Game opens, camera shows the whole map, top bar says `WAVE 1/10 IN 25s`.
2. Tap a Constructor (the "C" units) → bottom bar shows **MG Turret (4)** and
   **Barracks** → tap MG Turret → tap near the lane → ghost turns green → turret
   builds (shows % while constructing).
3. Tap **START WAVE** (or wait) → red "s" swarm walks the lane → turret fires.
4. Drag a box over your units → all selected → tap ground → they move.
5. Long-press ground → "Attack-move" toast → units engage on the way.
6. Pinch to zoom, two-finger drag to pan, tap minimap to jump.

## Shipping an update (so the in-app updater picks it up)

1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle`.
2. Build the signed release APK with the same keystore as always.
3. On GitHub, create a release tagged `vN` where **N = the new versionCode**
   (e.g. `versionCode 3` → tag `v3`) and attach the APK to the release.
4. Installed copies check `releases/latest` on launch and offer one-tap
   download + install. Same signing key = installs over the old version,
   no uninstall needed.

## If the build fails

- **Gradle sync errors**: make sure Grok Build uses Gradle 8.7+ and JDK 17.
- **Namespace / SDK errors**: needs Android SDK 34 (compileSdk 34).
- **Signing errors**: confirm `starfront-release.p12` sits in the project root
  next to `keystore.properties`, and the passwords in `keystore.properties`
  match the keystore.
- Anything else: send me the exact error text and I'll fix the project.

## Keystore backup

Your release keystore lives at `~/workspace/keystores/starfront-release.p12`
with its password in `starfront-release-password.txt` (same folder). Back both
up somewhere safe — a password manager plus an offline copy. If the keystore is
lost, no future APK can ever be installed as an update over the current one,
and the Play Store won't accept the app under a new key either. It is valid
until 2056.

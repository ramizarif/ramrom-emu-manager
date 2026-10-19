# Releasing

## Before the first public release

- [ ] Choose the app's licence (PLAN §9, deferred to M6) and add `LICENSE`.
- [ ] Create the release key once and back it up somewhere safe outside this machine. Losing it
      means existing installs can't update; Obtainium and Android both check the signature.
      ```
      keytool -genkeypair -v -keystore ~/keys/ramrom-release.jks -alias ramrom \
        -keyalg RSA -keysize 4096 -validity 10000
      ```
      Then write `keystore.properties` at the repo root (see README).
- [ ] LGPL: 7-Zip-JBinding and 7-Zip are linked as unmodified native libraries. Each release's
      notes link their source (github.com/omicronapps/7-Zip-JBinding-4Android, 7-zip.org).
- [ ] Public GitHub repository for releases (Obtainium reads GitHub releases).

## Every release

1. Bump `versionCode` (by 1) and `versionName` in `app/build.gradle.kts`.
2. `./gradlew test` passes.
3. `./gradlew :app:assembleRelease`, then check the signature:
   `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`
4. Install the release build on a test device over the previous release (not a debug build: the
   signatures differ) and run: onboarding, Health, Import, Settings › About › Open-source licences.
5. Tag `v<versionName>` and create a GitHub release with the APK attached as
   `RamROM-Emu-Manager-<versionName>.apk`, so one regex matches every release.
6. Release notes: what changed, in plain words. Never mention where to get games, BIOS or keys.

## Obtainium

Users add the GitHub repository URL. Settings that keep updates reliable:

- Filter APKs by regex: `RamROM-Emu-Manager-.*\.apk`
- Version detection: from the tag (`v0.1.0` → `0.1.0`)

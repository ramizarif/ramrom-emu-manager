# RamROM Emu Manager

Sets up an Android handheld for emulation: picks emulators that suit the device, installs them,
makes the game folders, points every frontend at them, checks BIOS files and keys, and keeps an
eye on the setup afterwards. Built for the AYN Thor first; any Android 13+ device works.

**Your games stay yours.** The app works with games, BIOS files and keys you already own. It never
provides them, links to them, or suggests where to get them.

## What it does

- **Plan**: choose frontends (ES-DE, Cocoon, iiSU, RetroHrai!) and systems; the app recommends an
  emulator per system for the device's chip.
- **Set up**: installs apps, creates `ROMs/<system>` folders, grants permissions, writes ES-DE's
  emulator choices, applies device defaults to emulator configs. What Android won't let an app do
  (folder picks inside another app) is guided, with a floating step chip over that app.
- **Health**: a setup percentage and a list of what stops games running (missing apps, BIOS,
  keys, folder access, frontends pointed at the wrong folder), each with a fix.
- **Import**: sorts game files from Downloads or any folder into the right system folder, unpacks
  archives, and groups multi-disc games.
- **Library and Apps**: what's on the device, what opens it, and each emulator's settings.

## Access modes

- **Elevated (Shizuku)**: installs without prompts, sets permissions, writes emulator configs in
  `Android/data`, reads folder grants. Shizuku is installed and paired from inside the app.
- **Limited**: everything that needs Shizuku becomes a guided step. Folders, import and the BIOS
  check still work on their own.

## Building

Requires JDK 17 or newer (built with 21) and the Android SDK (compileSdk 34).

```
./gradlew test               # unit tests (engine, knowledge, access)
./gradlew :app:assembleDebug # debug build
```

Release builds are signed only when `keystore.properties` exists at the repo root (it is
git-ignored, as are `*.jks` and `*.keystore`):

```
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

See [RELEASING.md](RELEASING.md) for the release checklist.

## Layout

| Module | What's in it |
|---|---|
| `app` | Screens, routes, the setup run on a real device, import, Shizuku install |
| `core:engine` | Plan compiler, setup runner, health checker, setup score, import planner, library |
| `core:knowledge` | The bundled knowledge file (`bundle-v1.json`): devices, chips, emulators, frontends, BIOS |
| `core:access` | Shizuku and limited access behind one interface |
| `core:data` | Room database (change history), diagnostics export |
| `core:ui` | Theme, components, scaffolds |
| `core:entitlements` | Free vs unlocked features |

## Licence

GNU General Public License version 3 or later ([LICENSE](LICENSE)), with an additional permission
to link 7-Zip, whose RAR code carries the unRAR restriction ([LICENSE-EXCEPTION](LICENSE-EXCEPTION)).
Third-party software and its licences are listed in the app under Settings › About › Open-source
licences.

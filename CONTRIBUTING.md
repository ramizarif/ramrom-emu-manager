# Contributing

Thanks for helping. This guide covers:
- the rules that keep the project legal;
- how to set up a test device;
- where things live in the code;
- what a good pull request looks like.

## The rules that don't bend

These rules keep the project, its users and its contributors out of trouble. A pull request that
breaks one is closed, however good the rest of it is.

1. **No sources for games, BIOS files or keys.** That means:
   - no links, URLs, site names or search terms;
   - no "where to get" hints in code, comments, strings, docs, issues or release notes.

   The app works with files the user already owns.
2. **No code that downloads, decrypts or converts encrypted game content or keys.** Reading a file
   header to tell what a file is (as `SwitchTitleId` does) is fine. Decrypting it is not.
3. **Only official sources for apps.** An emulator is installed from its developers' own releases or
   download page. Never use a mirror or a re-upload, and never use builds the developer doesn't
   publish. If an app can't be distributed (ES-DE is paid, NetherSX2 is a patched closed-source app),
   it's marked `user_obtains`, and the app never downloads, hosts or links it.
4. **Don't copy code from projects whose licence doesn't allow it here.** This project is
   GPL-3.0-or-later. Code under a compatible licence (MIT, Apache-2.0, BSD, GPL-3.0) can come in,
   with its notice kept and an entry on the licences screen. AGPL and proprietary code can't. Ideas
   are fine. Code isn't.

## Set up a test device

Use a real handheld if you have one, or an Android emulator. The rest of this section assumes an
emulator, because then nothing on your own device is at risk.

1. In Android Studio's Device Manager, create a virtual device with an **Android 14 (API 34)
   Google APIs** image. Use `arm64-v8a` on Apple Silicon, or `x86_64` on Intel and AMD.
2. Boot it and install the debug build: `./gradlew installDebug`.
3. **Optional: pretend to be a handheld.** Debug builds can act as one of the devices in the
   knowledge file, so you get that device's recommendations and settings:

   ```sh
   adb shell run-as com.ramrom.emumanager sh -c 'echo ayn-thor > files/debug-device'
   ```

   Restart the app afterwards. Device ids are listed in `bundle-v1.json` under `devices`.
4. **Elevated mode on an emulator:**
   1. Let the app install Shizuku.
   2. Wireless pairing doesn't work on an emulator, so start Shizuku over adb instead: in Shizuku,
      open *Start via adb*, and run the command it shows from your computer.
   3. Back in RamROM Emu Manager, *Settings › Access* should say Connected.

On an emulator, some apps don't run at all (Eden needs a real GPU) or show a blank screen (iiSU).
Setup, Health, Import and the step card all work, so test those there, and check emulator
behaviour on real hardware.

Never test changes to emulator settings on your own handheld without a backup of the emulator's
`Android/data` folder.

## Where things live

| You want to... | Look in |
|---|---|
| Add a device, or fix how one is detected | `bundle-v1.json` › `devices` (and `DeviceDetectorTest`) |
| Change an emulator's package name, folders, install source or ROM extensions | `bundle-v1.json` › `emulators` |
| Add a BIOS file the app checks for | `bundle-v1.json` › `bios`: file name, MD5, SHA-1, size, which emulator folders it goes in |
| Change device default settings for an emulator | `bundle-v1.json` › `settingsProfiles` |
| Change what setup does, or in what order | `core/engine/.../PlanCompiler.kt` |
| Change how steps run on a device | `app/.../run/DeviceStepExecutor.kt` |
| Add a Health check | `core/engine/.../health/HealthChecker.kt` |
| Change how imports are sorted | `core/engine/.../importer/` |
| Change a screen | `app/.../screens/` (UI) and the matching `*Routes.kt` (wiring) |

### The knowledge file

`core/knowledge/src/main/assets/knowledge/bundle-v1.json` is the app's knowledge of the outside
world. Every fact in it should come from somewhere you can point to:
- the emulator's own docs or source code;
- the device maker's specifications;
- an MD5 that matches a known-good dump.

Put that source in your pull request description.

`BundleParsingTest` checks the file parses, and that ids referenced across sections exist. Run
`./gradlew :core:knowledge:test` after any change to it.

Settings profiles carry a `verdict` and `verifiedOn`. Mark a profile verified only if you tested it
on that exact device model.

## Code style

- **Kotlin, Jetpack Compose, no XML layouts.** Follow the code around your change: naming, comment
  density, and how state flows from a `*Routes.kt` file into a screen.
- **Keep logic out of `app` when you can.** Anything that decides what to do belongs in
  `core:engine` as plain Kotlin with a unit test. `app` carries those decisions out on a device.
- **Everything must work with a controller.** Each screen sets its button hints (A, B, X, Y,
  START, L1/R1), and focus has to be reachable with a D-pad.
- **Write user-facing text in plain words:**
  - short sentences that say what happens;
  - no em dashes;
  - no jargon where a plain word works ("folder", not "directory");
  - never words that suggest obtaining games ("get ROMs", "download games").

## Before you open a pull request

- `./gradlew test` passes.
- `./gradlew assembleDebug` builds without new warnings in the code you touched.
- If you changed a screen, include before and after screenshots. A real device is best, an emulator
  is fine.
- If you changed what the app writes to another app's files, say how you tested it, and that the
  files were restored afterwards.

By contributing, you agree that your contribution is licensed under the project's licence
(GPL-3.0-or-later with the 7-Zip exception in `LICENSE-EXCEPTION`).

## Reporting bugs

Use the bug report template. The most useful thing you can attach is the report from *Settings ›
Report a problem*. It leaves out game names, but read it before you post it anyway.

## Device reports

"It works on my Retroid Pocket 5" is useful. Use the device report template and say:
- which systems you set up;
- what Health showed;
- anything you had to fix by hand.

Reports like these are how a device moves from "best guess" to "verified".

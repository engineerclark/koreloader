# koreloader

Install [KOReader](https://github.com/koreader/koreader), [Plato](https://github.com/baskerville/plato), [KFMon](https://github.com/NiLuJe/kfmon) and [NickelMenu](https://github.com/pgaskin/NickelMenu) on a Kobo e-reader **from your Android phone**, with no computer needed.

Plug the Kobo into your phone with a USB-C OTG cable, tap **Connect** on the Kobo, choose the Kobo drive in the app, and open **Manage Kobo packages**. Each package shows whether it's installed and whether an update is available. Install the latest or an older version, or delete it.

> **Status:** early development. Installing, updating and deleting work end to end on an emulator with a simulated Kobo, but haven't been tested on a real Kobo yet. See [Roadmap](#roadmap).

## How it works

Installing these tools on a Kobo is just copying files onto its USB drive. The Kobo does the rest itself:

1. The app downloads NiLuJe's [One-Click Install Package](https://www.mobileread.com/forums/showthread.php?t=314220) for what you chose, straight from the upstream server, and checks its size and BLAKE2b checksum.
2. It reads `.kobo/version` on the Kobo to identify the model and firmware, and refuses to continue on unsupported setups (currently Kobo firmware 5.x).
3. It adds the setting that stops the Kobo from indexing hidden folders as books (`ExcludeSyncFolders` in `.kobo/Kobo/Kobo eReader.conf`, recommended by KOReader for firmware 4.17+).
4. It unpacks the package onto the Kobo, hidden folders included (`.adds/`, `.kobo/`), and writes `.kobo/KoboRoot.tgz` **last** so a half-written update file is never left behind.
5. You eject the Kobo from Android and unplug it. The Kobo shows "Updating", installs KFMon and NickelMenu, and reboots. KOReader then appears as a book called "koreader" in your library and as an entry in NickelMenu.

The app uses Android's Storage Access Framework: you grant access to the Kobo drive once with the system folder picker. It needs no special permissions and no root.

## Requirements

- An Android phone running Android 10 or newer with USB OTG support
- A cable to connect the Kobo (USB-C to USB-C, or USB-C to micro-USB for older Kobos)
- A Kobo running firmware **4.x** (firmware 5.x is not supported yet)

### Before you install

From NiLuJe's [one-click package instructions](https://www.mobileread.com/forums/showthread.php?t=314220):

- **Fully restart your Kobo first.** Then connect it straight from the Home screen, without opening any book.
- These packages are meant for **new setups**. If you use KSM, don't use them: KSM always takes priority. If you use fmon, note that fmon and KFMon are mutually exclusive, and whichever was installed last wins.
- After a Kobo **firmware update**, KFMon stops working. Reinstall the standalone KFMon package (planned) to restore it.

### Troubleshooting

After you eject and unplug, the Kobo should look like it's processing a book and then restart to install the update. **If it doesn't restart, something went wrong.** Reconnect it and run the install again.

## Supported packages

| Package | Source | Versions | Delete |
| --- | --- | --- | --- |
| KFMon (includes NickelMenu) | NiLuJe's one-click package | Latest only (no older builds are published) | Official uninstaller, which runs on the Kobo after unplugging |
| KOReader | GitHub releases | All releases; older ones without a checksum are labelled | Keeps settings and history unless you choose otherwise |
| Plato | GitHub releases | All releases; older ones without a checksum are labelled | Keeps settings and library data unless you choose otherwise |

KOReader and Plato need KFMon to start. If it's missing, or a Kobo firmware update disabled it, koreloader installs it in the same step.

## Roadmap

- [x] Spike: pick the Kobo drive and write a file into a hidden folder (emulator; real Kobo pending)
- [x] Milestone 1: install KOReader (one-click package) end to end (emulator; real Kobo pending)
- [x] Package manager: tiles with installed/update status, version history, install/update/older versions, delete (emulator; real Kobo pending)
- [ ] Remote package catalog (new packages without an app update)
- [ ] NickelMenu as its own package
- [ ] iOS (Kotlin Multiplatform). Blocked on how iOS exposes USB drives.

## Building

Requires JDK 21 and the Android SDK.

```sh
./gradlew :androidApp:assembleDebug   # build the APK
./gradlew :androidApp:installDebug    # install on a connected phone
./gradlew jvmTest                     # run unit tests (no device needed)
scripts/fake-kobo.sh create           # simulate a Kobo on an emulator/phone (see CLAUDE.md)
scripts/fake-kobo.sh eject            # simulate unplugging it (Nickel processes KoboRoot.tgz)
scripts/emulator-ui.sh texts          # read the app's screen over adb
```

Or open the project in Android Studio.

## Project layout

The code follows a domain-driven, hexagonal layout. Each layer is a separate Gradle module:

```
domain/          Entities and rules: packages, Kobo device and firmware, install ordering
application/     Use cases (detect device, download & verify, install) and the ports they need
infrastructure/  Adapters: package manifest client, hashing, zip reading, Android storage (SAF)
presentation/    ViewModels and UI state
androidApp/      Jetpack Compose UI and app wiring
```

`domain`, `application`, `infrastructure` and `presentation` are Kotlin Multiplatform modules, so they can be reused by an iOS app later.

See [CLAUDE.md](CLAUDE.md) for the technical details: verified upstream install behaviour, install safety rules, and architecture.

## Safety

This app only copies files to your Kobo's USB drive, the same way you would from a computer. Still, you modify your device at your own risk. Always eject the Kobo safely before unplugging it, especially while `.kobo/KoboRoot.tgz` is present. If something goes wrong, removing the files listed in [CLAUDE.md](CLAUDE.md#one-click-packages-ocp-by-niluje) before unplugging stops the Kobo from processing a partial install.

## Credits and licensing

All the hard work is done by the upstream projects:

- [KOReader](https://github.com/koreader/koreader) (AGPL-3.0)
- [KFMon](https://github.com/NiLuJe/kfmon) and the One-Click Packages by NiLuJe (GPL-3.0+)
- [Plato](https://github.com/baskerville/plato) by baskerville (AGPL-3.0)
- [NickelMenu](https://github.com/pgaskin/NickelMenu) by pgaskin (MIT)

The packages are downloaded from their official distribution points when you install them. koreloader bundles only small upstream files (logos, KFMon launcher configs and the KFMon uninstaller), listed in [THIRD_PARTY_ASSETS.md](THIRD_PARTY_ASSETS.md).

koreloader is not affiliated with Rakuten Kobo or any of the projects above. License: GPL (exact version TBD).

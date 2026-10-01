# CLAUDE.md

Guidance for Claude Code (and other agents) working in this repository.

## What this is

**koreloader**: an Android app (Kotlin, Jetpack Compose) that installs KOReader, Plato, KFMon and NickelMenu onto a Kobo e-reader connected to the phone over USB (OTG). It is meant to feel like an app store: list packages, pick one, tap Install.

Installing is **only file copying** onto the Kobo's USB mass-storage volume (FAT32, label `KOBOeReader`). No code runs on the Kobo from this app. Nickel (Kobo's firmware UI) does the rest when the device is ejected (it processes `.kobo/KoboRoot.tgz` and reboots).

- App ID / namespace: `org.mmclark.koreloader`
- minSdk 29 (Android 10), target/compile the latest stable SDK
- Android only for now. iOS is a later goal via Kotlin Multiplatform, so keep shared logic platform-agnostic (see Architecture).

## Verified upstream facts (checked 2026-09-30)

Re-verify before changing any of these. Sources are listed at the bottom.

### One-Click Packages (OCP) by NiLuJe
- Public, machine-readable manifest: `https://storage.gra.cloud.ovh.net/v1/AUTH_2ac4bfee353948ec8ea7fd1710574097/kfmon-pub/manifest.json`
  - Shape: `{ "Storage_URL": "...", "OCP": [ { name, version, filename, path, mimetype, date, revision, size, MD5, BLAKE2B } ] }`. The download URL is `Storage_URL + "/" + path`.
  - `name` is one of `KFMon`, `KOReader`, `Plato`, `Both` (Plato + KOReader).
  - Hashes are **MD5 and BLAKE2b-512 (hex)**, **not SHA-256**. Verify size + BLAKE2b (MD5 alone is not enough).
  - The container is wiped and re-uploaded on every release, so filenames and versions change. Never hardcode a filename; always resolve it through the manifest.
- `OCP-KOReader-<ver>.zip` already contains everything needed for a first install: KFMon, a `.kobo/KoboRoot.tgz` with **KFMon and NickelMenu merged**, KFMon configs (`.adds/kfmon/config/koreader.ini` etc.), NickelMenu entries (`.adds/nm/kfmon`, `.adds/nm/koreader`), `.adds/koreader/`, and icons such as `kfmon.png` and `koreader.png` at the root.
- OCP KOReader can lag the latest KOReader release (e.g. OCP v2026.03 vs upstream v2026.07.1).
- Archive layout, checked by downloading both on 2026-09-30 (`b2sum` and `md5` matched the manifest):
  - Entry names are plain relative paths (`.adds/kfmon/config/kfmon.ini`), with no `./` prefix. Directory entries end in `/`.
  - `OCP-KOReader-v2026.03.zip`: 1,214 entries, 86.6 MB unpacked, 43.6 MB zipped. `.kobo/KoboRoot.tgz` (788 KB) is already the **last** entry. Root files: `kfmon.png`, `koreader.png`.
  - `OCP-KFMon-*.zip`: 10 entries: `kfmon.png`, `.adds/kfmon/{bin,config,log}/…`, `.kobo/KoboRoot.tgz`.
- Forum thread guidance (first post and post #2, as pasted by the user on 2026-09-30):
  - The packages are aimed at **new users**. Launchers are mutually exclusive: between fmon and KFMon, whichever was installed last wins. **KSM always takes precedence**, so the packages are not meant for KSM users. Warn about this before installing.
  - The `ExcludeSyncFolders` tweak must be made **before unplugging**, in the same USB session as the install. After a firmware upgrade, make it during the first USB connection.
  - NiLuJe strongly recommends a **clean full reboot of the Kobo first**, then connecting **straight from the Home screen** without opening any documents. This avoids USB mass-storage problems. Show this as a pre-install checklist.
  - Expected result after a safe eject: the Kobo looks like it is processing a book, then restarts to process an update. **If it does not reboot, something went wrong** (most likely the extraction). Put this in the post-install troubleshooting text.
  - Afterwards KOReader/Plato launch from their icon in Home or Library, or from the new entries in the Home main menu (NickelMenu).
  - A firmware update disables KFMon. The OCP-KFMon package exists to restore it.
  - The listing in the thread matches `manifest.json` (same filenames and MD5s). The manifest is the source to use.
- Reference installer behaviour (`tools/install.sh` in the kfmon repo); our install engine must match it:
  1. Confirm `<root>/.kobo` exists.
  2. **Append** to `.kobo/Kobo/Kobo eReader.conf`:
     ```
     
     [FeatureSettings]
     ExcludeSyncFolders=(\\.(?!kobo|adobe).+|([^.][^/]*/)+\\..+)
     ```
     A duplicate section at the end is fine (Qt's QSettings merges it). We skip the append if that exact line is already present.
  3. Install: extract the OCP zip to the **volume root**, overwriting existing files.
     Uninstall (`KFMon-Uninstaller.zip`): extract into **`.kobo/`**, not the root.
  4. Sanity checks: `.kobo/KoboRoot.tgz` exists; for installs, `kfmon.png` exists at the root.
  5. Flush, then tell the user to eject safely and unplug.
  On failure the script cleans nothing up. It lists what to remove: `.adds/kfmon`, `.adds/koreader`, `.adds/plato`, `.kobo/KoboRoot.tgz`, `kfmon.png`, `koreader.png`, `icons/plato.png`.

### KOReader (GitHub releases, `koreader/koreader`)
- Release assets: `koreader-kobo-<tag>.zip` and, since v2026.07, `koreader-kobov5-<tag>.zip`. The GitHub API returns a `digest: "sha256:..."` for each asset.
- `kobov5` targets the **Kobo firmware 5.x** userland, which needs "further changes, including to the delivery mechanism" (KOReader PR #12568). The OCP bundles use the regular `kobo` build.
  **Rule for now: block installs when firmware major >= 5** and say clearly that it is unsupported.
- Manual install: extract the zip's `koreader/` folder into `.adds/`. NickelMenu entry `.adds/nm/koreader` contains:
  `menu_item:main:KOReader:cmd_spawn:quiet:exec /mnt/onboard/.adds/koreader/koreader.sh`
- NickelMenu method needs KOReader 2020.05+ and firmware 4.6+. KFMon needs firmware 2.9.0+.
- A Kobo firmware update disables KFMon (reinstall it with the OCP-KFMon package). NickelMenu survives firmware updates.

### NickelMenu (`pgaskin/NickelMenu`)
- Release asset `KoboRoot.tgz`. Configs live in `.adds/nm/`.

### Plato (`baskerville/plato`)
- Release asset `plato-<tag>.zip`, extracted into `.adds/plato/`. KFMon config `.adds/kfmon/config/plato.ini`, icon `icons/plato.png`.

### `.kobo/version`
- One line of comma-separated fields. calibre's Kobo driver reads `[0]` as the serial, `[2]` as the firmware version (e.g. `4.38.21908`), and the **last** field as the model ID string `00000000-0000-0000-0000-000000000NNN`, where NNN is the device ID.
- Device IDs (from `pgaskin/koboutils` `kobo/device.go`) include: Clara HD 376, Clara 2E 386, Clara BW 391, Clara Colour 393, Libra 2 388, Libra Colour 390, Sage 383, Elipsa 2E 389. Treat unknown IDs as "unknown model" (a warning, not a block).

## Install invariants (do not break)

1. **`.kobo/KoboRoot.tgz` is written last**, and never directly under its final name: write `KoboRoot.tgz.part` (or similar), fsync, delete any existing `KoboRoot.tgz`, then rename. Nickel processes this file on unplug, and a truncated tarball can brick the update. If anything fails before the rename, delete the temp file.
2. **Verify the archive before touching the device** (size + BLAKE2b for OCP; SHA-256 for GitHub assets). Download to app cache, not to the Kobo.
3. **Stream everything.** Read the cached archive entry by entry (`java.util.zip.ZipFile`) straight into SAF output streams, with no whole-archive buffering. KOReader is about 44 MB zipped, 87 MB unpacked, 1,214 entries.
3a. **Delete any stale `.kobo/KoboRoot.tgz` before extracting anything.** A leftover from an earlier aborted run must not be processed if this run fails.
4. **Reject unsafe zip entries**: absolute paths, `..` segments, empty names. Normalise `\` to `/`.
5. **Check the device first**: `.kobo/version` must exist and parse; block firmware 5.x; warn on unknown models.
6. The Kobo config edit is append-only and idempotent. Never rewrite the whole `Kobo eReader.conf`.
7. Never claim success until the post-install sanity checks pass. Then show the eject instructions: eject from Android (Settings, Storage, or the notification), unplug, and wait for "Updating" then a reboot. If the Kobo does not reboot, the install failed; say so in the troubleshooting text.
8. Before installing, show a checklist: fully reboot the Kobo, connect from the Home screen without opening documents, and not for KSM users. Also warn that fmon and KFMon are mutually exclusive.

## Android storage notes (SAF)

- Ask the user to pick the Kobo root with `ACTION_OPEN_DOCUMENT_TREE`, then `takePersistableUriPermission` (read and write). USB mass storage is served by `ExternalStorageProvider`. Picking a USB volume root is allowed on Android 11+ (only the primary storage root and Download are blocked).
- Do **not** use `DocumentFile.findFile` in loops (it is O(n) queries per lookup). Use `DocumentsContract` directly, with an in-memory `path -> documentUri` cache for directories created or seen during a run.
- `createDocument` on an existing name produces `name (1)`. Look up first, then open existing documents with mode `"wt"` (truncate) or delete and recreate. Use mime `application/octet-stream` so the provider doesn't rewrite extensions.
- `renameDocument` also avoids collisions by renaming, so delete the target first.
- Flush important files with `ParcelFileDescriptor` + `Os.fsync` (KoboRoot.tgz, Kobo eReader.conf).
- Apps cannot eject a volume. Instruct the user instead.
- Fallback if SAF is unreliable or slow: libaums (USB host + FAT32 directly). Keep it behind the same `KoboVolume` interface.

## Architecture (domain-driven, hexagonal, KMP-ready)

Domain entities, application logic and infrastructure each live in **separate Gradle modules** with strict one-way dependencies. Every module except `:androidApp` is a Kotlin Multiplatform library. Today the only target is Android (plus `jvm` for fast tests); iOS targets come later.

```
:domain          entities, value objects, domain services, repository interfaces
:application     use cases, ports (interfaces) to the outside world, use-case results/progress
:infrastructure  adapters that implement the ports (network, hashing, zip, SAF storage)
:presentation    ViewModels + UI state (shareable with a future iOS/Compose Multiplatform UI)
:androidApp      Compose UI, Activity, SAF folder picker, composition root (DI wiring)
```

Dependency rule (arrows point to what a module may depend on):

```
androidApp ──► presentation ──► application ──► domain
     │                               ▲
     └──────► infrastructure ────────┘ (and ──► domain)
```

- **`:domain`** has no dependencies besides the Kotlin stdlib: no coroutines, serialization, I/O or framework types.
  It holds `Package`/`PackageId`/`PackageVersion`, `Checksum` (algorithm + hex), `KoboDevice` (serial, `FirmwareVersion`, `DeviceModel`), `DeviceCompatibilityPolicy` (block FW 5.x, warn on unknown model), install rules as pure functions (`InstallPlan`: entry ordering with KoboRoot last, `ArchivePath` validation that rejects `..` and absolute paths, the `ExcludeSyncFolders` append rule), and repository interfaces such as `PackageCatalog`.
- **`:application`** depends only on `:domain` (and kotlinx.coroutines).
  It holds use cases (`DetectKoboDevice`, `ResolvePackage`, `DownloadAndVerifyPackage`, `InstallPackage`, later `UninstallPackage`) and the **ports** they need: `KoboVolume` (list/read/write/rename/delete/fsync by relative path), `PackageDownloader`, `ArchiveReader` (streaming entries), `HashFunction`/`ChecksumVerifier`, `Clock` if needed. It orchestrates domain rules and reports progress as a `Flow`.
- **`:infrastructure`** implements the ports.
  - `commonMain`: Ktor client for the OCP `manifest.json` and GitHub releases, kotlinx.serialization DTOs **mapped to domain types at the boundary** (DTOs never leak out), and BLAKE2b/SHA-256 hashers.
  - `androidMain`: `SafKoboVolume` (DocumentsContract), `ZipInputStreamArchiveReader`, file-cache downloads.
  - `jvmSharedMain` (a custom source set shared by `android` and `jvm`): adapters that need `java.io`/`java.util.zip` but not Android (`CachingFileDownloader`, `FileChecksumCalculator`, `ZipFileArchiveReader`), so they are unit-tested on the JVM. `NetworkAdapters` owns the Ktor client and exposes only port types, so `:androidApp` never depends on Ktor.
  - Split into `:infrastructure:catalog`, `:infrastructure:storage-saf` and so on once it grows.
- **`:presentation`** depends on `:application` (and `:domain` types for display). It holds ViewModels (androidx.lifecycle ViewModel is multiplatform), immutable UI state and `StateFlow`s. No Android or Compose imports.
- **`:androidApp`** is the composition root. It wires infrastructure adapters into use cases with manual DI, and holds the Compose screens and the Android-only glue (`ACTION_OPEN_DOCUMENT_TREE`, persisted URI permissions).

Rules:
- Never import `java.*`/`android.*` in any `commonMain`. Platform code lives in `androidMain` of `:infrastructure` (or in `:androidApp`).
- Ports belong to `:application`; implementations belong to `:infrastructure`. Use cases never see a DTO, `Uri`, `InputStream` or HTTP type. Byte streams cross the port boundary as `okio.Source`/`Sink` (Okio is multiplatform), or as a domain-neutral abstraction.
- Domain types are immutable and validated at construction (factory functions returning a result, or `require`). Model unknown or unsupported states explicitly (sealed types) rather than with nulls or strings.
- Use KMP-capable libraries: kotlinx.coroutines, kotlinx.serialization, Ktor client (OkHttp engine on Android), Okio. Avoid Hilt (it is Android-only).
- Unit-test `:domain` and `:application` in `commonTest`/`jvmTest` with fakes for the ports (e.g. an in-memory `KoboVolume`). These must run without a device or emulator.
- Downloads and installs must survive configuration changes. Keep them in ViewModel scope for milestone 1; move to WorkManager or a foreground service once installs are long-running.

## Roadmap

1. **Spike**, done: the "Storage test" screen (`RunStorageCheck`) exercises hidden folders, overwrite-with-truncate, append, rename-over-existing, recursive delete and small-file throughput in `.adds/koreloader-test`. It passed on the emulator (2026-09-30). **Still to do on a real Kobo.**
2. **Milestone 1**, done on the emulator with `scripts/fake-kobo.sh`: the main screen installs OCP-KOReader (with KFMon and NickelMenu) end to end. That covers the picker, the `.kobo/version` check, the manifest lookup, download, size + BLAKE2b + MD5 verification, the config append, extraction with KoboRoot last, the sanity checks and the eject screen. Verified on 2026-09-30: all 1,039 files byte-identical to the zip, no temp file left, and a re-install reuses the cached download, creates no duplicates and doesn't re-append the config. **Still to do on a real Kobo, including the reboot.**
3. Later: our own remote catalog JSON (id, name, description, version, source URL, sha256, target paths, dependencies, supported models/firmware, actions, reboot-between-steps flag), installed-state detection, Plato, NickelMenu-only, update/uninstall (KFMon uninstaller goes into `.kobo/`), store-style UI, Room/DataStore cache.

## Licensing

KOReader is AGPL-3.0, KFMon GPL-3.0+, Plato AGPL-3.0, NickelMenu MIT. **Do not bundle their binaries in the APK or repo.** Fetch them from upstream at install time.

## Build

- JDK 21, Gradle wrapper (Kotlin DSL, version catalog in `gradle/libs.versions.toml`).
- Point `ANDROID_HOME` (or `local.properties` `sdk.dir`, which is git-ignored) at an Android SDK.
- `./gradlew :androidApp:assembleDebug` builds the app.
- `./gradlew jvmTest` runs fast unit tests for all KMP modules (no device).
- `KORELOADER_OCP_ZIP=/path/to/OCP-KOReader-vX.zip ./gradlew :infrastructure:jvmTest --rerun-tasks` also checks that a real one-click package plans cleanly. This test is opt-in; never commit the zip, because of licensing.
- `./gradlew :androidApp:installDebug` installs on a connected phone.

## Testing without a Kobo

`scripts/fake-kobo.sh` creates a fake Kobo volume at `/sdcard/FakeKobo` on a connected emulator or phone, using adb. In the app's picker, choose internal storage, then **FakeKobo**. This goes through the same SAF provider (`ExternalStorageProvider`) as a USB drive, but internal storage is ext4/f2fs: case-sensitive and much faster than a Kobo, so timings are not representative.

```sh
scripts/fake-kobo.sh create                  # .kobo/version (FW 4.38.21908, Clara BW 391) + empty Kobo eReader.conf
scripts/fake-kobo.sh version 5.1.0           # firmware 5.x, expect "not supported"
scripts/fake-kobo.sh version 4.5.0           # too old, expect blocked
scripts/fake-kobo.sh version 4.38.21908 999  # unknown model, expect a warning only
scripts/fake-kobo.sh garbage                 # unparseable .kobo/version
scripts/fake-kobo.sh show                    # list files + print version and Kobo eReader.conf
scripts/fake-kobo.sh remove                  # delete /sdcard/FakeKobo
```

After changing `.kobo/version`, tap **Re-check** in the app. `ANDROID_SERIAL` picks the device when several are connected; `ADB` overrides the adb path. The default is adb on PATH, then `$ANDROID_HOME`, then `~/Library/Android/sdk`.

The closest stand-in for a real Kobo is a FAT32 USB stick containing a `.kobo/version` file, attached to a phone over OTG.

## Sources

- KFMon README and installer scripts: https://github.com/NiLuJe/kfmon (`README.md`, `tools/install.sh`, `tools/one-click.py`, `tools/upload.sh`)
- OCP thread: https://www.mobileread.com/forums/showthread.php?t=314220 (tools can't fetch it; the user pasted posts #1 and #2 on 2026-09-30)
- KOReader Kobo install wiki: https://github.com/koreader/koreader/wiki/Installation-on-Kobo-devices
- KOReader kobov5: https://github.com/koreader/koreader/pull/12401, https://github.com/koreader/koreader/pull/12568
- `.kobo/version` parsing: calibre `src/calibre/devices/kobo/driver.py`. Device IDs: https://github.com/pgaskin/koboutils/blob/master/kobo/device.go

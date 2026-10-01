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

- Release history: GitHub has every stable release (247 total; `koreader-kobo-<tag>.zip` assets go back years). `ota` is a prerelease of nightlies and is filtered out. **A SHA-256 `digest` exists only for v2025.08 and later**; older releases have none.
- Version marker on the volume: `.adds/koreader/git-rev` (e.g. `v2026.07.1`).
- The release zip holds `koreader/…` plus a stray top-level `koreader.png` (an old fmon relic that `one-click.py` deletes). We map `koreader/` to `.adds/koreader/` and ignore the rest.
- User data in `.adds/koreader` (`frontend/datastorage.lua`, `readhistory.lua`): `settings.reader.lua`(`.old`), `history.lua`(`.old`), `defaults.{custom,persistent,legacy}.lua`, and the folders `settings/` (plugin settings, statistics and vocabulary DBs), `docsettings/`, `hashdocsettings/`, `history/`, `statistics/`, `patches/`, `styletweaks/`, `screenshots/`, `clipboard/` and `icons/`. Per-book `.sdr` sidecars live next to the books and are never touched.

### KFMon launcher files and uninstaller
- KFMon has **no GitHub releases**. Only the current build exists: OCP-KFMon from the manifest, or `KFMon-v*.zip` on the MobileRead thread t=274231.
- `OCP-KFMon` contains `kfmon.png`, `.adds/kfmon/{config/kfmon.ini,bin/kfmon-printlog.sh,log/}` and the KFMon+NickelMenu `KoboRoot.tgz`. It has **no** launcher configs (`one-click.py` strips them).
- Launcher files per app, from the kfmon repo and **bundled** in koreloader (see `THIRD_PARTY_ASSETS.md`; byte-identical to OCP-KOReader's): `koreader.png` at the root plus `.adds/kfmon/config/koreader.ini`; `icons/plato.png` plus `.adds/kfmon/config/plato.ini`; NickelMenu shards `.adds/nm/{kfmon,koreader,plato}`. `nm/kfmon` is a `generator : main : kfmon` line that lists KFMon watches in the Home menu.
- Uninstall: the official `KFMon-Uninstaller.zip` (MobileRead attachment) contains a `KoboRoot.tgz` that swaps in `kfmon-uninstall.sh` as `kfmon-update.sh`, plus an `on-animator.sh`. We bundle that `KoboRoot.tgz` and place it as `.kobo/KoboRoot.tgz`. On the next boot the Kobo deletes `/usr/local/kfmon`, KFMon's onboard configs and log, **the KOReader and Plato launcher files** (`koreader.png`, `icons/plato.png`, `.adds/kfmon/config/*.ini`, `.adds/nm/{kfmon,koreader,plato,doc}`), and any empty dirs. The script triggers via `/etc/udev/rules.d/99-kfmon.rules` (`KERNEL=="loop0", RUN+="/usr/local/kfmon/bin/kfmon-update.sh"`), which both tarballs ship. In the installer, `kfmon-update.sh` only deletes that rule.
- **NickelMenu does not uninstall itself when `.adds/nm` is removed** (checked 2026-10-01). Its NickelHook uninstall triggers are `.adds/nm/uninstall` (the flag is deleted, then the library) and, only in builds with `NM_UNINSTALL_CONFIGDIR`, a missing `.adds/nm`. The shipped `libnm.so` (v0.6.0, inside OCP-KFMon's `KoboRoot.tgz`) reports `NM_UNINSTALL_CONFIGDIR: false`. That auto-uninstall was a custom pre-0.6.0 build (`one-click.py`), so the comment in `kfmon-uninstall.sh` is outdated. After a KFMon uninstall, NickelMenu stays installed and shows a config error item until `.adds/nm` exists again.
- The KFMon+NickelMenu `KoboRoot.tgz` also contains **`./mnt/onboard/.adds/nm/doc`**. Nickel extracts the tarball over `/`, so that file appears on the user partition after the reboot.
- **Several installs in one USB session work**: only one `KoboRoot.tgz` can exist, and only an install that writes one replaces it. Example: KOReader pulls in KFMon (writing the installer `KoboRoot.tgz`); then Plato, installed before ejecting, sees KFMon's files and receipt and leaves the pending installer alone. One eject applies everything. Any success screen reports a restart while a `KoboRoot.tgz` is pending, whoever wrote it.

### NickelMenu (`pgaskin/NickelMenu`)
- Release asset `KoboRoot.tgz`. Configs live in `.adds/nm/`.

### Plato (`baskerville/plato`)
- Release asset `plato-<tag>.zip` (files at the zip root), extracted into `.adds/plato/`. KFMon config `.adds/kfmon/config/plato.ini`, icon `icons/plato.png`. 96 releases; SHA-256 digest only for 0.9.45 and later.
- **No version file**: the installed version comes from koreloader's receipt, or is "unknown".
- User data: `.adds/plato/Settings.toml` and `config.sh`. Library metadata sits in each default library root (`library.rs`): `.metadata.json`, `.fat32-epoch`, `.reading-states/` and `.thumbnail-previews/` in `/`, `.kobo/dropbox/` and `.kobo/kepub/`. **Never touch `.trash`**, which holds deleted books.

### `.kobo/version`
- One line of comma-separated fields. calibre's Kobo driver reads `[0]` as the serial, `[2]` as the firmware version (e.g. `4.38.21908`), and the **last** field as the model ID string `00000000-0000-0000-0000-000000000NNN`, where NNN is the device ID.
- Device IDs (from `pgaskin/koboutils` `kobo/device.go`) include: Clara HD 376, Clara 2E 386, Clara BW 391, Clara Colour 393, Libra 2 388, Libra Colour 390, Sage 383, Elipsa 2E 389. Treat unknown IDs as "unknown model" (a warning, not a block).

## Install invariants (do not break)

1. **`.kobo/KoboRoot.tgz` is written last**, and never directly under its final name: write `KoboRoot.tgz.part` (or similar), fsync, delete any existing `KoboRoot.tgz`, then rename. Nickel processes this file on unplug, and a truncated tarball can brick the update. If anything fails before the rename, delete the temp file.
2. **Verify the archive before touching the device** (size + BLAKE2b for OCP; SHA-256 for GitHub assets). Download to app cache, not to the Kobo.
3. **Stream everything.** Read the cached archive entry by entry (`java.util.zip.ZipFile`) straight into SAF output streams, with no whole-archive buffering. KOReader is about 44 MB zipped, 87 MB unpacked, 1,214 entries.
3a. **When an install writes a `KoboRoot.tgz`, delete any stale one before extracting anything.** A leftover from an earlier aborted run must not be processed if this run fails. Installs that don't write one (e.g. KOReader on a working KFMon) leave a pending one alone.
4. **Reject unsafe zip entries**: absolute paths, `..` segments, empty names. Normalise `\` to `/`.
5. **Check the device first**: `.kobo/version` must exist and parse; block firmware 5.x; warn on unknown models.
6. The Kobo config edit is append-only and idempotent. Never rewrite the whole `Kobo eReader.conf`.
7. Never claim success until the post-install sanity checks pass. Then show the eject instructions: eject from Android (Settings, Storage, or the notification), unplug, and wait for "Updating" then a reboot. If the Kobo does not reboot, the install failed; say so in the troubleshooting text.
8. **Dependencies**: KOReader and Plato depend on KFMon. If KFMon is missing, needs a reinstall after a firmware update, or has a pending removal, its latest release is installed in the same run, first. That gives one Kobo reboot.
9. **Delete keeps user data by default.** The opt-in checkbox ("Also delete settings and reading history") is off by default and, when ticked, lists exactly what will be removed (`UninstallPackage.preview`).
10. **Unverifiable releases** (no published checksum) are labelled "no checksum" and need an explicit confirmation before installing. Only their size is checked.
11. Before installing, show a checklist: fully reboot the Kobo, connect from the Home screen without opening documents, and not for KSM users. Also warn that fmon and KFMon are mutually exclusive.

## Android storage notes (SAF)

- Ask the user to pick the Kobo root with `ACTION_OPEN_DOCUMENT_TREE`, then `takePersistableUriPermission` (read and write). USB mass storage is served by `ExternalStorageProvider`. Picking a USB volume root is allowed on Android 11+ (only the primary storage root and Download are blocked).
- Do **not** use `DocumentFile.findFile` in loops (it is O(n) queries per lookup). Use `DocumentsContract` directly, with an in-memory `path -> documentUri` cache for directories created or seen during a run.
- `createDocument` on an existing name produces `name (1)`. Look up first, then open existing documents with mode `"wt"` (truncate) or delete and recreate. Use mime `application/octet-stream` so the provider doesn't rewrite extensions.
- `renameDocument` also avoids collisions by renaming, so delete the target first.
- Flush important files with `ParcelFileDescriptor` + `Os.fsync` (KoboRoot.tgz, Kobo eReader.conf).
- Apps cannot eject a volume. Instruct the user instead.
- **A `SafKoboVolume` must live for one operation only** (a status load, an install or a delete): `KoboVolumeFactory.open` per operation. Its listing cache goes stale when another instance writes, or when Nickel changes files between USB sessions. A long-lived instance showed "Not installed" right after an install.
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
  It holds:
  - catalog: `PackageId`, `PackageRelease`, `Artifact`, `Checksum`, and the `PackageCatalog` repository interface;
  - device: `KoboDevice`, `FirmwareVersion`, `DeviceModel`, `DeviceCompatibilityPolicy`;
  - packages: `Packages`/`PackageDefinition` (static knowledge per package: marker file, version file, `ArchiveLayout`, bundled launcher files, dependencies, required files, `Removal` rule, optional `notices` shown as info cards on the detail screen), `InstalledStateResolver`, `PackageStatusPolicy` (update available, KFMon reinstall after firmware change, removal pending), `InstallReceipt`, `RemovalPlan`;
  - install: `InstallPlan.compose` (multiple archives plus bundled files, safe paths, case-insensitive conflicts, KoboRoot deferred), `NickelConfig`;
  - volume: `KoboPath`.
- **`:application`** depends only on `:domain` (and kotlinx.coroutines).
  It holds:
  - use cases: `DetectKoboDevice`, `FetchReleases`, `InspectVolume`, `GetPackageOverview`, `DownloadPackage`, `InstallPackage` (resolves dependencies, records receipts), and `UninstallPackage` (plus `preview`);
  - ports: `KoboVolume`/`KoboVolumeFactory`, `PackageDownloader`, `ChecksumCalculator`, `ArchiveReader`, `BundledAssets` and `ReceiptStore`.
  Progress is reported as a `Flow`.
- **`:infrastructure`** implements the ports.
  - `commonMain`:
    - `UpstreamPackageCatalog` (15-minute cache, to respect GitHub's 60 requests/hour anonymous limit), which combines `OcpManifestCatalog` for KFMon and `GitHubReleasesCatalog` for KOReader and Plato;
    - `VolumeReceiptStore` (`.adds/koreloader/receipts.json`);
    - `Blake2b`.
    DTOs are **mapped to domain types at the boundary** and never leak out.
  - `androidMain`: `SafKoboVolume` (DocumentsContract).
  - `jvmSharedMain` (a custom source set shared by `android` and `jvm`): adapters that need `java.io`/`java.util.zip` but not Android (`CachingFileDownloader` (keeps 3 files), `FileChecksumCalculator`, `ZipFileArchiveReader`, and `ResourceBundledAssets`, which serves Java resources whose SHA-256s are pinned in `BundledAssetsTest`), so they are unit-tested on the JVM. `NetworkAdapters` owns the Ktor client and exposes only port types, so `:androidApp` never depends on Ktor.
  - Split into `:infrastructure:catalog`, `:infrastructure:storage-saf` and so on once it grows.
- **`:presentation`** depends on `:application` (and `:domain` types for display). It holds ViewModels (androidx.lifecycle ViewModel is multiplatform), immutable UI state and `StateFlow`s. No Android or Compose imports.
- **`:androidApp`** is the composition root. It wires infrastructure adapters into use cases with manual DI, and holds the Compose screens and the Android-only glue (`ACTION_OPEN_DOCUMENT_TREE`, persisted URI permissions).
  Screens (Navigation Compose): `home` (pick the drive, device card, "Manage Kobo packages"), `packages` (grid of tiles with status chips), `package/{id}` (status, version dropdown, install/update/reinstall/older version, delete dialog). `HomeViewModel` is activity-scoped and owns the selected `VolumeRef`; the other ViewModels are scoped to their destination.

Rules:
- Never import `java.*`/`android.*` in any `commonMain`. Platform code lives in `androidMain` of `:infrastructure` (or in `:androidApp`).
- Ports belong to `:application`; implementations belong to `:infrastructure`. Use cases never see a DTO, `Uri`, `InputStream` or HTTP type. Byte streams cross the port boundary as `okio.Source`/`Sink` (Okio is multiplatform), or as a domain-neutral abstraction.
- Domain types are immutable and validated at construction (factory functions returning a result, or `require`). Model unknown or unsupported states explicitly (sealed types) rather than with nulls or strings.
- Use KMP-capable libraries: kotlinx.coroutines, kotlinx.serialization, Ktor client (OkHttp engine on Android), Okio. Avoid Hilt (it is Android-only).
- Unit-test `:domain` and `:application` in `commonTest`/`jvmTest` with fakes for the ports (e.g. an in-memory `KoboVolume`). These must run without a device or emulator.
- Downloads and installs must survive configuration changes. Keep them in ViewModel scope for milestone 1; move to WorkManager or a foreground service once installs are long-running.

## Roadmap

1. **Spike**, done and then removed (2026-09-30). The "Storage test" screen (`RunStorageCheck`, in git history) exercised hidden folders, overwrite-with-truncate, append, rename-over-existing, recursive delete and small-file throughput, and passed on the emulator. The installs now cover the same operations. On a real Kobo, a first install doubles as the check.
2. **Milestone 1**, done on the emulator with `scripts/fake-kobo.sh`: the main screen installs OCP-KOReader (with KFMon and NickelMenu) end to end. That covers the picker, the `.kobo/version` check, the manifest lookup, download, size + BLAKE2b + MD5 verification, the config append, extraction with KoboRoot last, the sanity checks and the eject screen. Verified on 2026-09-30: all 1,039 files byte-identical to the zip, no temp file left, and a re-install reuses the cached download, creates no duplicates and doesn't re-append the config. **Still to do on a real Kobo, including the reboot.**
3. **Package manager**, done on the emulator (2026-09-30):
   - home, packages grid and detail screens;
   - KFMon, KOReader and Plato tiles with installed, update and reinstall-needed status;
   - version history from GitHub;
   - install, update and older-version installs with automatic KFMon;
   - delete that keeps user data by default, with an opt-in to remove it;
   - KFMon delete through the official uninstaller, shown as "removal pending".
   Verified flows: KOReader v2024.11 (unverified) installed with KFMon, then updated to v2026.07.1 (KFMon skipped, no reboot needed); Plato installed; KOReader deleted with settings kept; KFMon uninstaller placed (byte-identical). **Still to do on a real Kobo.**
4. Later:
   - our own remote catalog JSON, so packages can change without an app update;
   - NickelMenu as its own package;
   - removing stale files on updates (today an update overwrites files but doesn't delete ones the new version dropped, same as unzip and the OCPs);
   - WorkManager or a foreground service for long installs;
   - Room/DataStore cache.

## Licensing

koreloader is **GPL-3.0-or-later** (`LICENSE` is the canonical text from gnu.org, byte-identical to KFMon's). Copyright notice: "Copyright (C) 2026 Michael Clark". The author and website (https://michael.mmclark.org) are shown on the home screen (`AppAuthor` in `presentation/.../credits/Credits.kt`). Why version 3: the bundled KFMon files are GPL-3.0-or-later; the KOReader and Plato logos are AGPL-3.0, which GPLv3 §13 allows combining with; and the Apache-2.0 libraries are GPLv3-compatible but not GPLv2-compatible. Don't introduce GPLv2-only code or GPL-incompatible dependencies. Creative Commons licenses aren't used: CC advises against them for software. The project doesn't use SPDX headers in source files.
- In the app, the home screen's credits card ends with the GPL's short notice for interactive programs ("comes with ABSOLUTELY NO WARRANTY…"), a link to the license, and **View full license**. The full license is the root `LICENSE`, copied into the APK as `assets/LICENSE.txt` by the `copyLicenseAsset` task in `androidApp/build.gradle.kts`. The values live in `AppLicense` (`presentation/.../credits/Credits.kt`).
- **Before distributing an APK**, set `AppLicense.SOURCE_URL` to the public repository. GPLv3 §6 requires the corresponding source to be offered with object code; the notice then shows the link.

Bundling GPL/AGPL artwork and scripts is therefore fine. Every bundled third-party file is listed in `THIRD_PARTY_ASSETS.md` with its source. The packages themselves (KOReader, Plato, KFMon binaries) are **not** bundled: they are fetched from upstream at install time, so users always get upstream's builds. Licenses: KOReader AGPL-3.0, KFMon GPL-3.0+, Plato AGPL-3.0, NickelMenu MIT.

The home screen credits upstream projects with links (`presentation/.../credits/Credits.kt`, rendered by `CreditsCard`): KOReader (the KOReader contributors), Plato (baskerville), KFMon & One-Click Packages (NiLuJe) and NickelMenu (pgaskin), plus reference data from koboutils and calibre. Each package's detail screen has its own Credits card (`Credits.forPackage`, rendered by `PackageCredits`), with project, author, license and link: KOReader/Plato plus the KFMon launcher files, or KFMon plus NickelMenu. `CreditsTest` checks that every package has credits. When the app starts using another upstream project or file, add it there and to `THIRD_PARTY_ASSETS.md`.

## Build

- JDK 21, Gradle wrapper (Kotlin DSL, version catalog in `gradle/libs.versions.toml`).
- Point `ANDROID_HOME` (or `local.properties` `sdk.dir`, which is git-ignored) at an Android SDK.
- `./gradlew :androidApp:assembleDebug` builds the app.
- `./gradlew jvmTest` runs fast unit tests for all KMP modules (no device).
- `KORELOADER_OCP_ZIP=/path/to/OCP-KOReader-vX.zip ./gradlew :infrastructure:jvmTest --rerun-tasks` also checks that a real one-click package plans cleanly. This test is opt-in; never commit the zip, because of licensing.
- `./gradlew :androidApp:installDebug` installs on a connected phone.

## Branding

`branding/` holds the koreloader logo SVGs: the mark, the wordmark, the lockup (mark + wordmark) and the app icon. The brand colours are navy `#12263A` and blues `#3D7BFF`/`#1F4FCC`; on dark backgrounds they become light `#F5F6F8` and `#6B9BFF`/`#3D6FE0`, as in the app-icon SVG. `branding/generate_drawables.py` converts the SVGs into Android vector drawables. Don't hand-edit the generated files; rerun the script after changing an SVG.
- `ic_launcher_foreground`/`ic_launcher_monochrome`: the adaptive launcher icon (`mipmap-anydpi-v26/ic_launcher*.xml`, background `@color/ic_launcher_background`). The mark is about 53dp tall, inside the 66dp safe zone.
- `splash_logo` (plus a `drawable-night` variant): the launch screen, with the mark above the wordmark. It lives inside the splash icon rather than Android's branding-image slot, which would pin the wordmark to the bottom of the screen. Android masks the icon to a 192dp circle, and the script asserts the lockup fits inside it. Theme: `Theme.Koreloader.Splash` (androidx core-splashscreen, `installSplashScreen()` in `MainActivity`).
- `lockup` (plus a night variant): shown in the home screen's top bar, 32dp tall.

## Testing without a Kobo

`scripts/fake-kobo.sh` creates a fake Kobo volume at `/sdcard/FakeKobo` on a connected emulator or phone, using adb. In the app's picker, choose internal storage, then **FakeKobo**. This goes through the same SAF provider (`ExternalStorageProvider`) as a USB drive, but internal storage is ext4/f2fs: case-sensitive and much faster than a Kobo, so timings are not representative.

```sh
scripts/fake-kobo.sh create                  # .kobo/version (FW 4.38.21908, Clara BW 391) + empty Kobo eReader.conf
scripts/fake-kobo.sh version 5.1.0           # firmware 5.x, expect "not supported"
scripts/fake-kobo.sh version 4.5.0           # too old, expect blocked
scripts/fake-kobo.sh version 4.38.21908 999  # unknown model, expect a warning only
scripts/fake-kobo.sh garbage                 # unparseable .kobo/version
scripts/fake-kobo.sh show                    # list files + print version and Kobo eReader.conf
scripts/fake-kobo.sh eject                   # simulate eject + unplug + reboot (see below)
scripts/fake-kobo.sh remove                  # delete /sdcard/FakeKobo
```

After changing `.kobo/version`, tap **Re-check** in the app.

`eject` (`scripts/fake_kobo_eject.py`) reproduces what a real Kobo does after unplugging with a `.kobo/KoboRoot.tgz` present, using a **fake root filesystem** at `/sdcard/FakeKoboRootfs`. That partition isn't visible over USB, so the app never sees it, just as on a real Kobo.
1. It extracts the tarball over `/`. `./mnt/onboard/…` entries go to the fake volume (e.g. NickelMenu's `.adds/nm/doc`); everything else goes to the fake root filesystem. Symlinks become placeholder files. Then it deletes the tarball.
2. Boot: it runs the `RUN+=` scripts of udev rules found on the fake root filesystem (KFMon's `kfmon-update.sh`). The real script is used, with `/mnt/onboard` mapped to the fake volume, other absolute paths mapped into the fake root filesystem, and `killall`/`reboot`/`sync` defused. So the installer deletes its own udev rule, and the uninstaller removes `/usr/local/kfmon`, the onboard files and the empty folders.
3. It applies NickelMenu's NickelHook checks (`.adds/nm/uninstall` flag; missing `.adds/nm` only if the shipped `libnm.so` was built with `NM_UNINSTALL_CONFIGDIR`).
4. It only reports images Nickel would import as books; there is no Nickel database to update.
Without a `KoboRoot.tgz` it does nothing, because there is no reboot. `show` lists the fake root filesystem too. Afterwards, Refresh in the app shows the result; the app keeps no file listings between loads. `ANDROID_SERIAL` picks the device when several are connected; `ADB` overrides the adb path. The default is adb on PATH, then `$ANDROID_HOME`, then `~/Library/Android/sdk`.

The closest stand-in for a real Kobo is a FAT32 USB stick containing a `.kobo/version` file, attached to a phone over OTG.

`scripts/emulator-ui.sh` drives the app over adb for quick manual checks: `texts` lists the on-screen text, `tap "<regex>"` taps the first element whose text matches, `shot <file.png>` takes a screenshot. When tapping buttons, match their full label (e.g. `"^Install "`, not a word that also appears in status text). If a tap reports "not found", stop: nothing happened.

## Sources

- KFMon README and installer scripts: https://github.com/NiLuJe/kfmon (`README.md`, `tools/install.sh`, `tools/one-click.py`, `tools/upload.sh`)
- OCP thread: https://www.mobileread.com/forums/showthread.php?t=314220 (tools can't fetch it; the user pasted posts #1 and #2 on 2026-09-30)
- KOReader Kobo install wiki: https://github.com/koreader/koreader/wiki/Installation-on-Kobo-devices
- KOReader kobov5: https://github.com/koreader/koreader/pull/12401, https://github.com/koreader/koreader/pull/12568
- `.kobo/version` parsing: calibre `src/calibre/devices/kobo/driver.py`. Device IDs: https://github.com/pgaskin/koboutils/blob/master/kobo/device.go

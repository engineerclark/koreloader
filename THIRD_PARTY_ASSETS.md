# Third-party assets

koreloader is copyleft (GPL) and bundles the files below. None of the packages themselves (KOReader, Plato, KFMon) are bundled; those are downloaded from upstream at install time.

## Files installed onto the Kobo

These live under `infrastructure/src/jvmSharedMain/resources/org/mmclark/koreloader/assets/`. They are installed onto the Kobo next to the packages. `BundledAssetsTest` pins their SHA-256, so any change to them is deliberate.

| Asset | Upstream source | License |
|---|---|---|
| `kfmon/koreader.png`, `kfmon/plato.png` | [NiLuJe/kfmon](https://github.com/NiLuJe/kfmon) `resources/` at `ca318697` | GPL-3.0-or-later |
| `kfmon/koreader.ini`, `kfmon/plato.ini` | NiLuJe/kfmon `config/` at `ca318697` | GPL-3.0-or-later |
| `nm/kfmon`, `nm/koreader`, `nm/plato` | NiLuJe/kfmon `nm/` at `ca318697` | GPL-3.0-or-later |
| `kfmon-uninstaller/KoboRoot.tgz` | `KoboRoot.tgz` inside the official `KFMon-Uninstaller.zip` (zip SHA-256 `c7166a08…aaf592`), attached to the [KFMon MobileRead thread](https://www.mobileread.com/forums/showthread.php?t=274231) (attachment 223769, 2026-06-07). Built by kfmon's `make kobo` from `scripts/uninstall/`. | GPL-3.0-or-later |

These are the same files NiLuJe's one-click packages ship. As of 2026-09-30, the launcher files are byte-identical to those in `OCP-KOReader-v2026.03.zip`.

To update: copy the new files from upstream, update the commit and attachment references above, and update the hashes in `BundledAssetsTest`.

## App artwork

These live under `androidApp/src/main/res/drawable-nodpi/`, downscaled to 384 px.

| File | Upstream source | License |
|---|---|---|
| `logo_koreader.png` | [koreader/koreader](https://github.com/koreader/koreader) `resources/koreader.png` | AGPL-3.0 |
| `logo_plato.png` | [baskerville/plato](https://github.com/baskerville/plato) `artworks/plato.png` | AGPL-3.0 |
| `logo_kfmon.png` | NiLuJe/kfmon `resources/kfmon.png` (KFMon's library cover) | GPL-3.0-or-later |

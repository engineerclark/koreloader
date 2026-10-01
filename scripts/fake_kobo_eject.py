#!/usr/bin/env python3
"""
Simulates ejecting and unplugging a Kobo, applied to the fake Kobo of scripts/fake-kobo.sh.

What a real Kobo does, and how it is simulated:

1. Nickel finds .kobo/KoboRoot.tgz, reboots, and extracts it over "/" before deleting it.
   -> Entries under ./mnt/onboard/ are written to the fake volume (the USB-visible user
      partition, e.g. NickelMenu's .adds/nm/doc); every other entry goes to the fake root
      filesystem (FAKE_ROOTFS, which the app can't see, just like on a real Kobo).
2. Early in the boot, udev rules on the root filesystem run their RUN+= scripts
   (KFMon's 99-kfmon.rules runs /usr/local/kfmon/bin/kfmon-update.sh: in the installer that
   only removes the rule; in the uninstaller it is kfmon-uninstall.sh).
   -> Those scripts are run on the device with /mnt/onboard mapped to the fake volume and
      other absolute paths mapped into the fake root filesystem.
3. When Nickel starts, NickelMenu (via NickelHook) uninstalls itself if .adds/nm/uninstall
   exists (deleting the flag), or, only in builds with NM_UNINSTALL_CONFIGDIR, if .adds/nm
   is missing. Current releases are built without it (checked in the shipped libnm.so).
4. Nickel imports new images outside hidden folders as "books" into its database
   (.kobo/KoboReader.sqlite + thumbnails). Not simulated (the fake Kobo has no database);
   the files that would be imported are listed.

Without a KoboRoot.tgz the Kobo just leaves USB mode: no reboot, so nothing else happens.

Usage (normally via `scripts/fake-kobo.sh eject`):
    fake_kobo_eject.py <adb> <fake volume dir> <fake rootfs dir>
"""
import io
import re
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

ONBOARD = "mnt/onboard/"


class Device:
    def __init__(self, adb: str):
        self.adb = adb

    def sh(self, command: str, check: bool = True) -> str:
        result = subprocess.run([self.adb, "shell", command], capture_output=True, text=True)
        if check and result.returncode != 0:
            raise SystemExit(f"adb shell failed ({result.returncode}): {command}\n{result.stderr}")
        return result.stdout

    def exists(self, path: str, kind: str = "e") -> bool:
        return self.sh(f"test -{kind} '{path}' && echo yes || true").strip() == "yes"

    def pull(self, remote: str) -> bytes:
        with tempfile.TemporaryDirectory() as tmp:
            local = Path(tmp) / "f"
            subprocess.run([self.adb, "pull", remote, str(local)], check=True, capture_output=True)
            return local.read_bytes()

    def push_bytes(self, data: bytes, remote: str):
        with tempfile.TemporaryDirectory() as tmp:
            local = Path(tmp) / "f"
            local.write_bytes(data)
            self.sh(f"mkdir -p '{remote.rsplit('/', 1)[0]}'")
            subprocess.run([self.adb, "push", str(local), remote], check=True, capture_output=True)


def apply_koboroot(dev: Device, tgz: bytes, volume: str, rootfs: str):
    """Step 1: extract the tarball over '/' (onboard part -> fake volume, rest -> fake rootfs)."""
    with tarfile.open(fileobj=io.BytesIO(tgz), mode="r:gz") as tar:
        for member in tar.getmembers():
            name = member.name.removeprefix("./")
            if not name or ".." in Path(name).parts or name.startswith("/"):
                continue
            if name.startswith(ONBOARD):
                target, where = f"{volume}/{name[len(ONBOARD):]}", "onboard"
            elif name.rstrip("/") + "/" == ONBOARD or name.rstrip("/") == "mnt":
                continue
            else:
                target, where = f"{rootfs}/{name}", "rootfs"
            if member.isdir():
                dev.sh(f"mkdir -p '{target}'")
            elif member.issym():
                # Symlinks can't be created on Android's shared storage: a placeholder file with the same
                # name stands in, so scripts that `rm -f` the link still behave the same.
                dev.push_bytes(f"symlink -> {member.linkname}\n".encode(), target)
                print(f"  {where}:  /{name} -> {member.linkname} (symlink, recorded as a placeholder file)")
            elif member.isfile():
                dev.push_bytes(tar.extractfile(member).read(), target)
                if where == "onboard":
                    print(f"  onboard: wrote {name[len(ONBOARD):]}")
        rootfs_files = [m.name.removeprefix(".") for m in tar.getmembers()
                        if m.isfile() and not m.name.removeprefix("./").startswith(ONBOARD)]
    for f in rootfs_files:
        print(f"  rootfs:  {f}")


def map_paths(script: str, volume: str, rootfs: str) -> str:
    """Rewrites a boot script's absolute paths onto the fake volume/rootfs and defuses process control."""
    script = script.replace("/mnt/onboard", volume)
    script = re.sub(r'(?<=[\s"\'=])/(etc|usr|var|bin|lib|opt|tmp)/', rf"{rootfs}/\1/", script)
    script = re.sub(r"^(\s*)(killall|pkill|reboot|sync)\b", r"\1: # \2", script, flags=re.MULTILINE)
    return script


def run_udev_boot_scripts(dev: Device, volume: str, rootfs: str):
    """Step 2: run RUN+= scripts of udev rules present on the (fake) root filesystem."""
    rules_dir = f"{rootfs}/etc/udev/rules.d"
    if not dev.exists(rules_dir, "d"):
        return
    for rule in dev.sh(f"ls '{rules_dir}'").split():
        text = dev.sh(f"cat '{rules_dir}/{rule}'")
        for script_path in re.findall(r'RUN\+="([^"]+)"', text):
            fake_script = f"{rootfs}{script_path}"
            if not dev.exists(fake_script, "f"):
                continue
            print(f"  boot: udev rule {rule} runs {script_path}")
            mapped = map_paths(dev.sh(f"cat '{fake_script}'"), volume, rootfs)
            output = dev.sh(f"cd '{volume}' && sh -c {sh_quote(mapped)} 2>&1 ; true", check=False)
            for line in output.strip().splitlines():
                print(f"        {line}")


def sh_quote(s: str) -> str:
    return "'" + s.replace("'", "'\"'\"'") + "'"


def nickelmenu_boot(dev: Device, volume: str, rootfs: str):
    """Step 3: NickelHook's uninstall checks when Nickel loads libnm.so."""
    lib = f"{rootfs}/usr/local/Kobo/imageformats/libnm.so"
    if not dev.exists(lib, "f"):
        return
    flag = f"{volume}/.adds/nm/uninstall"
    if dev.exists(flag):
        dev.sh(f"rm -f '{flag}' '{lib}'")
        print("  boot: NickelMenu found .adds/nm/uninstall: deleted the flag and uninstalled itself")
        return
    configdir_flag = b"NM_UNINSTALL_CONFIGDIR: true" in dev.pull(lib)
    if configdir_flag and not dev.exists(f"{volume}/.adds/nm", "d"):
        dev.sh(f"rm -f '{lib}'")
        print("  boot: NickelMenu (built with NM_UNINSTALL_CONFIGDIR) found .adds/nm missing: uninstalled itself")
    elif not dev.exists(f"{volume}/.adds/nm", "d"):
        print("  boot: NickelMenu stays installed; .adds/nm is missing, so its menu shows a config error item")


def report_library_imports(dev: Device, volume: str):
    """Step 4 (report only): images Nickel would import as books (outside hidden folders)."""
    found = dev.sh(f"cd '{volume}' && find . -path './.*' -prune -o \\( -name '*.png' -o -name '*.jpg' \\) -print").split()
    for f in found:
        print(f"  library: Nickel imports {f.removeprefix('./')} as a book (not simulated: no Nickel database)")


def main():
    adb, volume, rootfs = sys.argv[1:4]
    dev = Device(adb)
    koboroot = f"{volume}/.kobo/KoboRoot.tgz"
    if not dev.exists(koboroot, "f"):
        print("No .kobo/KoboRoot.tgz: the Kobo just leaves USB mode (no update, no reboot).")
        return
    dev.sh(f"mkdir -p '{rootfs}'")
    print("Nickel processes .kobo/KoboRoot.tgz (extracted over /):")
    apply_koboroot(dev, dev.pull(koboroot), volume, rootfs)
    dev.sh(f"rm -f '{koboroot}'")
    print("  onboard: deleted .kobo/KoboRoot.tgz")
    print("Reboot:")
    run_udev_boot_scripts(dev, volume, rootfs)
    nickelmenu_boot(dev, volume, rootfs)
    report_library_imports(dev, volume)
    print("Done. Reconnect: in the app, tap Re-check or Refresh.")


if __name__ == "__main__":
    main()

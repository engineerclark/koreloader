#!/usr/bin/env bash
#
# Creates and manipulates a fake Kobo volume on a connected Android device or emulator,
# so the app can be tested without a real Kobo. Pick /sdcard/FakeKobo in the SAF picker.
#
# Usage: scripts/fake-kobo.sh <command> [args]
#
#   create [firmware] [model-id]  Create /sdcard/FakeKobo with .kobo/version and an empty
#                                 .kobo/Kobo/Kobo eReader.conf (default: 4.38.21908, 391 = Clara BW)
#   version <firmware> [model-id] Rewrite .kobo/version, e.g. "5.1.0" (unsupported) or "4.38.21908 999"
#   garbage                       Write an unparseable .kobo/version
#   show                          List the fake volume and print version + Kobo eReader.conf
#   eject                         Simulate ejecting + unplugging: Nickel processes .kobo/KoboRoot.tgz
#                                 (applying the KFMon uninstaller's onboard deletions), deletes it, reboots
#   remove                        Delete /sdcard/FakeKobo entirely
#
# Environment: ANDROID_SERIAL selects the device when several are connected (standard adb).
#              ADB overrides the adb binary (default: adb on PATH, else the Android Studio SDK).
#
set -euo pipefail

FAKE_ROOT="/sdcard/FakeKobo"
DEFAULT_FIRMWARE="4.38.21908"
DEFAULT_MODEL="391"

find_adb() {
    if [[ -n "${ADB:-}" ]]; then
        echo "${ADB}"
    elif command -v adb >/dev/null 2>&1; then
        command -v adb
    elif [[ -n "${ANDROID_HOME:-}" && -x "${ANDROID_HOME}/platform-tools/adb" ]]; then
        echo "${ANDROID_HOME}/platform-tools/adb"
    elif [[ -x "${HOME}/Library/Android/sdk/platform-tools/adb" ]]; then
        echo "${HOME}/Library/Android/sdk/platform-tools/adb"
    else
        echo "adb not found: put it on PATH or set ADB / ANDROID_HOME" >&2
        exit 1
    fi
}

ADB_BIN="$(find_adb)"

adb_shell() {
    "${ADB_BIN}" shell "$@"
}

version_line() {
    local firmware="$1" model="$2"
    printf 'N000000000000,4.1.15,%s,4.1.15,4.1.15,00000000-0000-0000-0000-%012d' "${firmware}" "$((10#${model}))"
}

write_version() {
    adb_shell "echo '$1' > ${FAKE_ROOT}/.kobo/version"
}

# What Nickel does with .kobo/KoboRoot.tgz when the Kobo is unplugged: extract it over the
# root filesystem (not visible over USB, so only reported here), run any update script it
# installed on the next boot, delete the tarball, and reboot. For the KFMon uninstaller, the
# onboard part of that script is applied, using the file lists read from the script itself.
simulate_eject() {
    if [[ "$(adb_shell "test -f '${FAKE_ROOT}/.kobo/KoboRoot.tgz' && echo yes || true")" != *yes* ]]; then
        echo "No .kobo/KoboRoot.tgz: the Kobo just leaves USB mode (no update, no reboot)."
        return
    fi
    local tmp
    tmp="$(mktemp -d)"
    trap 'rm -rf "${tmp}"' RETURN
    "${ADB_BIN}" pull "${FAKE_ROOT}/.kobo/KoboRoot.tgz" "${tmp}/KoboRoot.tgz" >/dev/null 2>&1 \
        || { echo "Can't read .kobo/KoboRoot.tgz from the device" >&2; exit 1; }
    local listing
    listing="$(tar -tzf "${tmp}/KoboRoot.tgz")"

    if grep -qE '^(\./)?usr/local/kfmon/bin/kfmon$' <<<"${listing}"; then
        echo "Nickel installs KFMon onto the root filesystem (not visible over USB)."
        grep -qE 'libnm\.so$' <<<"${listing}" && echo "Nickel installs NickelMenu onto the root filesystem (not visible over USB)."
    elif grep -qE '^(\./)?usr/local/kfmon/bin/kfmon-update\.sh$' <<<"${listing}"; then
        echo "KFMon uninstaller: on boot, kfmon-uninstall.sh removes KFMon and its onboard files."
        tar -xzf "${tmp}/KoboRoot.tgz" -C "${tmp}" ./usr/local/kfmon/bin/kfmon-update.sh
        local script="${tmp}/usr/local/kfmon/bin/kfmon-update.sh"
        local files dirs
        files="$(sed -nE 's/^for my_file in (.*) ; do$/\1/p' "${script}")"
        dirs="$(sed -nE 's/^for my_dir in (.*) ; do$/\1/p' "${script}")"
        [[ -n "${files}" && -n "${dirs}" ]] || { echo "Can't read the uninstall script's file lists" >&2; exit 1; }
        # Same loops as the script (files first, then empty directories), rooted at the fake volume.
        adb_shell "cd '${FAKE_ROOT}' && for f in ${files} ; do [ -f \"\$f\" ] && rm -f \"\$f\" && echo \"  deleted \$f\" ; done ; for d in ${dirs} ; do [ -d \"\$d\" ] && [ -z \"\$(ls -A -- \"\$d\")\" ] && rm -rf \"\$d\" && echo \"  removed empty \$d/\" ; done ; true"
        grep -q 'adds/nm' <<<"${files}" && [[ "$(adb_shell "test -d '${FAKE_ROOT}/.adds/nm' && echo yes || true")" != *yes* ]] \
            && echo "  .adds/nm is gone, so NickelMenu uninstalls itself (root filesystem, not visible over USB)."
    else
        echo "Unrecognised KoboRoot.tgz; Nickel would extract it over the root filesystem:"
        sed 's/^/  /' <<<"${listing}"
    fi
    adb_shell "rm -f '${FAKE_ROOT}/.kobo/KoboRoot.tgz'"
    echo "Deleted .kobo/KoboRoot.tgz and rebooted. Reconnect: in the app, tap Re-check or Refresh."
}

cmd="${1:-}"
shift || true

case "${cmd}" in
    create)
        firmware="${1:-${DEFAULT_FIRMWARE}}"
        model="${2:-${DEFAULT_MODEL}}"
        adb_shell "mkdir -p '${FAKE_ROOT}/.kobo/Kobo' && touch '${FAKE_ROOT}/.kobo/Kobo/Kobo eReader.conf'"
        write_version "$(version_line "${firmware}" "${model}")"
        echo "Created ${FAKE_ROOT} (firmware ${firmware}, model ${model})."
        ;;
    version)
        [[ $# -ge 1 ]] || { echo "usage: $0 version <firmware> [model-id]" >&2; exit 2; }
        write_version "$(version_line "$1" "${2:-${DEFAULT_MODEL}}")"
        echo "Updated .kobo/version. Tap Re-check in the app."
        ;;
    garbage)
        write_version "hello"
        echo "Wrote an unparseable .kobo/version. Tap Re-check in the app."
        ;;
    show)
        adb_shell "ls -laR '${FAKE_ROOT}' | head -60"
        echo "--- .kobo/version"
        adb_shell "cat '${FAKE_ROOT}/.kobo/version'"
        echo
        echo "--- .kobo/Kobo/Kobo eReader.conf"
        adb_shell "cat '${FAKE_ROOT}/.kobo/Kobo/Kobo eReader.conf'"
        ;;
    eject)
        simulate_eject
        ;;
    remove)
        adb_shell "rm -rf '${FAKE_ROOT}'"
        echo "Removed ${FAKE_ROOT}."
        ;;
    *)
        sed -n '3,19p' "$0" | sed 's/^# \{0,1\}//'
        exit 2
        ;;
esac

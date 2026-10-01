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
#   eject                         Simulate ejecting + unplugging: Nickel extracts .kobo/KoboRoot.tgz over "/"
#                                 (onboard part to the volume, the rest to a fake root filesystem), deletes
#                                 it and reboots; boot scripts then run (see scripts/fake_kobo_eject.py)
#   remove                        Delete /sdcard/FakeKobo (and its fake root filesystem) entirely
#
# Environment: ANDROID_SERIAL selects the device when several are connected (standard adb).
#              ADB overrides the adb binary (default: adb on PATH, else the Android Studio SDK).
#
set -euo pipefail

FAKE_ROOT="/sdcard/FakeKobo"
# The Kobo's root filesystem, which isn't visible over USB (the app never sees it); used by `eject`.
FAKE_ROOTFS="/sdcard/FakeKoboRootfs"
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
        echo "--- fake root filesystem (not visible to the app)"
        adb_shell "cd '${FAKE_ROOTFS}' 2>/dev/null && find . -type f | sort || echo '(empty: nothing installed by an eject yet)'"
        ;;
    eject)
        python3 "$(dirname "$0")/fake_kobo_eject.py" "${ADB_BIN}" "${FAKE_ROOT}" "${FAKE_ROOTFS}"
        ;;
    remove)
        adb_shell "rm -rf '${FAKE_ROOT}' '${FAKE_ROOTFS}'"
        echo "Removed ${FAKE_ROOT} and ${FAKE_ROOTFS}."
        ;;
    *)
        sed -n '3,20p' "$0" | sed 's/^# \{0,1\}//'
        exit 2
        ;;
esac

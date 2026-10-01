#!/usr/bin/env bash
#
# Drives the app on a connected emulator/phone via adb + uiautomator, for quick manual checks.
#
#   scripts/emulator-ui.sh texts               List on-screen text
#   scripts/emulator-ui.sh tap "<regex>"       Tap the first element whose text matches (exit 1 if none)
#   scripts/emulator-ui.sh shot <file.png>     Save a screenshot
#
# Environment: ANDROID_SERIAL selects the device; ADB overrides the adb binary.
set -euo pipefail

ADB_BIN="${ADB:-$(command -v adb || echo "${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb")}"

dump() {
    "${ADB_BIN}" shell 'uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml'
}

case "${1:-}" in
    texts)
        dump | grep -oE 'text="[^"]+"' | sed 's/^text=//'
        ;;
    tap)
        [[ $# -ge 2 ]] || { echo "usage: $0 tap <regex>" >&2; exit 2; }
        coords="$(dump | python3 -c '
import re, sys
xml, pattern = sys.stdin.read(), re.compile(sys.argv[1])
for m in re.finditer(r"<node [^>]*?text=\"([^\"]*)\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", xml):
    if pattern.search(m.group(1)):
        x1, y1, x2, y2 = map(int, m.groups()[1:])
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
' "$2")"
        [[ -n "${coords}" ]] || { echo "not found: $2" >&2; exit 1; }
        # shellcheck disable=SC2086
        "${ADB_BIN}" shell input tap ${coords}
        echo "tapped '$2' at ${coords}"
        ;;
    shot)
        [[ $# -ge 2 ]] || { echo "usage: $0 shot <file.png>" >&2; exit 2; }
        "${ADB_BIN}" exec-out screencap -p > "$2"
        echo "$2"
        ;;
    *)
        sed -n '3,10p' "$0" | sed 's/^# \{0,1\}//'
        exit 2
        ;;
esac

#!/system/bin/sh
# Keep the selected OPlus report_rate mode after another system component changes it.
BASE=/data/adb/onextbox
TARGET="$BASE/touch_rate_target"
EXPECTED="$BASE/touch_rate_expected"
PID="$BASE/touch_rate.pid"
SERVICE=vendor.oplus.hardware.touch.IOplusTouch/default

if [ "$1" != "--watch" ]; then
    nohup sh "$0" --watch >/dev/null 2>&1 &
    exit 0
fi

if [ -f "$PID" ]; then
    previous=$(cat "$PID" 2>/dev/null)
    case "$previous" in
        ''|*[!0-9]*) ;;
        *)
            if kill -0 "$previous" 2>/dev/null &&
                tr '\000' ' ' < "/proc/$previous/cmdline" 2>/dev/null | grep -q 'onextbox_touch_rate.sh'; then
                exit 0
            fi
            ;;
    esac
fi

echo "$$" > "$PID"
trap 'rm -f "$PID"' EXIT INT TERM

while [ -f "$TARGET" ]; do
    if dumpsys power 2>/dev/null | grep -q 'mWakefulness=Awake'; then
        expected=$(cat "$EXPECTED" 2>/dev/null)
        current=$(service call "$SERVICE" 3 i32 0 i32 182 2>/dev/null)
        if [ -n "$expected" ] && [ "$current" != "$expected" ]; then
            index=$(cat "$TARGET" 2>/dev/null)
            case "$index" in
                ''|*[!0-9]*) ;;
                *) service call "$SERVICE" 4 i32 0 i32 182 s16 "$index" >/dev/null 2>&1 ;;
            esac
        fi
        sleep 1
    else
        sleep 4
    fi
done

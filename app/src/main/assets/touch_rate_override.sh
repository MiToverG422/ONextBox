#!/system/bin/sh
# Keep a verified OPlus mode while its endpoint and configuration stay unchanged.
BASE=/data/adb/onextbox
SCRIPT=/data/adb/service.d/onextbox_touch_rate.sh
ENDPOINT="$BASE/touch_rate_endpoint"
MODES="$BASE/touch_rate_modes"
CONFIGS="$BASE/touch_rate_configs"
TARGET="$BASE/touch_rate_target"
STATUS="$BASE/touch_rate_status"
PID="$BASE/touch_rate.pid"
umask 077
set -f

# Parcel decoder
uint() {
    case "$1" in ''|*[!0-9]*) return 1 ;; esac
    [ "${#1}" -le 10 ] || return 1
    case "$1" in 0) ;; 0*) return 1 ;; esac
    [ "$1" -le "$2" ] 2>/dev/null
}

parcel_words() {
    case "$1" in *'Parcel('*')'*) ;; *) return 1 ;; esac
    [ "${#1}" -le 4096 ] || return 1
    body=${1#*'Parcel('}
    words=$(printf '%s\n' "$body" | /system/bin/toybox sed -E \
        -e "s/'.*//" -e 's/^[[:space:]]*0x[0-9a-fA-F]+:[[:space:]]*//' \
        -e 's/\)[[:space:]]*$//') || return 1
    set -- $words
    [ "$#" -ge 2 ] && [ "$#" -le 20 ] || return 1
    for word do
        [ "${#word}" -eq 8 ] || return 1
        case "$word" in *[!0-9a-fA-F]*) return 1 ;; esac
    done
    printf '%s\n' "$words"
}

supported() {
    words=$(parcel_words "$1") || return 1
    set -- $words
    [ "$#" -eq 2 ] && [ "$1" = 00000000 ] && [ "$2" = 00000001 ]
}

decode_mode() {
    words=$(parcel_words "$1") || return 1
    set -- $words
    [ "$1" = 00000000 ] || return 1
    length=$((0x$2))
    [ "$length" -ge 3 ] && [ "$length" -le 32 ] || return 1
    shift 2
    [ "$#" -eq "$(((length + 2) / 2))" ] || return 1
    decoded= position=0
    for word do
        number=$((0x$word))
        for unit in "$((number & 65535))" "$(((number >> 16) & 65535))"; do
            if [ "$position" -lt "$length" ]; then
                case "$unit" in 32|44|48|49|50|51|52|53|54|55|56|57) ;; *) return 1 ;; esac
                case "$unit" in 32) character=' ' ;; 44) character=, ;; *) character=$((unit - 48)) ;; esac
                decoded="$decoded$character"
            else
                [ "$unit" -eq 0 ] || return 1
            fi
            position=$((position + 1))
        done
    done
    compact=$(printf '%s' "$decoded" | /system/bin/toybox sed -n -E \
        's/^[ ]*([0-9]+)[ ]*,[ ]*([0-9]+)[ ]*$/\1,\2/p')
    index=${compact%%,*} chip=${compact#*,}
    [ "$compact" != "$index" ] || return 1
    uint "$index" 255 && uint "$chip" 2147483647 || return 1
    printf '%s,%s\n' "$index" "$chip"
}

# Daemon binding
regular_file() {
    [ -f "$1" ] && [ ! -L "$1" ] || return 1
    size=$(/system/bin/toybox stat -c %s "$1" 2>/dev/null) || return 1
    uint "$size" "$2"
}

file_hash() {
    regular_file "$1" "$2" || return 1
    hash=$(/system/bin/toybox sha256sum "$1" 2>/dev/null) || return 1
    printf '%s\n' "${hash%% *}"
}

set_status() { printf '%s\n' "$1" > "$STATUS"; }

config_path_valid() {
    case "$1" in
        /data/vendor/touchconfig/*) root=/data/vendor/touchconfig ;;
        /vendor/etc/touchconfig/*) root=/vendor/etc/touchconfig ;;
        /odm/etc/touchconfig/*) root=/odm/etc/touchconfig ;;
        *) return 1 ;;
    esac
    relative=${1#"$root"/}
    [ "${#1}" -le 256 ] || return 1
    case "$relative" in ''|*[!A-Za-z0-9_./-]*|/*|*/|*//*) return 1 ;; esac
    old_ifs=$IFS; IFS=/; set -- $relative; IFS=$old_ifs
    [ "$#" -le 3 ] && [ ! -L "$root" ] || return 1
    check_path=$root
    for segment do
        case "$segment" in .|..|''|[!A-Za-z0-9_-]*) return 1 ;; esac
        [ "${#segment}" -le 96 ] || return 1
        check_path="$check_path/$segment"
        [ ! -L "$check_path" ] || return 1
    done
    case "$segment" in *.xml) return 0 ;; esac
    return 1
}

valid_hash() {
    case "$1" in *[!0-9a-f]*) return 1 ;; esac
    [ "${#1}" -eq 64 ]
}

known_daemon() {
    uint "$1" 2147483647 && [ "$1" -gt 1 ] || return 1
    [ "$1" != "$$" ] && kill -0 "$1" 2>/dev/null || return 1
    commandline=$(/system/bin/toybox tr '\000' ' ' < "/proc/$1/cmdline" 2>/dev/null)
    case "$commandline" in "/system/bin/sh $SCRIPT --watch "|"sh $SCRIPT --watch ") return 0 ;; esac
    return 1
}

[ "$(/system/bin/id -u)" = 0 ] && [ -d "$BASE" ] && [ ! -L "$BASE" ] || exit 1
if [ "$1" != --watch ]; then
    previous=$(/system/bin/toybox cat "$PID" 2>/dev/null)
    if known_daemon "$previous"; then
        kill "$previous" 2>/dev/null
        tries=0
        while known_daemon "$previous" && [ "$tries" -lt 5 ]; do
            /system/bin/toybox sleep 1
            tries=$((tries + 1))
        done
        if known_daemon "$previous"; then set_status busy; exit 1; fi
    fi
    regular_file "$SCRIPT" 16384 || exit 1
    /system/bin/toybox nohup /system/bin/sh "$SCRIPT" --watch >/dev/null 2>&1 &
    exit 0
fi

previous=$(/system/bin/toybox cat "$PID" 2>/dev/null)
known_daemon "$previous" && exit 0
printf '%s\n' "$$" > "$PID" || exit 1
cleanup() {
    [ "$(/system/bin/toybox cat "$PID" 2>/dev/null)" = "$$" ] && /system/bin/toybox rm -f "$PID"
}
trap cleanup EXIT
trap 'set_status stopped; exit 0' INT TERM

endpoint_hash=$(file_hash "$ENDPOINT" 512) && modes_hash=$(file_hash "$MODES" 4096) &&
    target_hash=$(file_hash "$TARGET" 4) && configs_hash=$(file_hash "$CONFIGS" 8192) || {
        set_status missing_binding; exit 1;
    }
{
    IFS= read -r service && IFS= read -r panel && IFS= read -r config && IFS= read -r config_hash &&
        ! IFS= read -r extra && [ -z "$extra" ]
} < "$ENDPOINT" || { set_status invalid_binding; exit 1; }
case "$service" in vendor.oplus.hardware.touch.IOplusTouch/*) instance=${service##*/} ;; *) exit 1 ;; esac
[ "$service" = "vendor.oplus.hardware.touch.IOplusTouch/$instance" ] || { set_status invalid_binding; exit 1; }
case "$instance" in ''|*[!A-Za-z0-9_.-]*|*..*) set_status invalid_binding; exit 1 ;; esac
case "$instance" in [A-Za-z0-9]*) ;; *) set_status invalid_binding; exit 1 ;; esac
[ "${#instance}" -le 64 ] || { set_status invalid_binding; exit 1; }
case "$panel" in 0|1) ;; *) set_status invalid_binding; exit 1 ;; esac
config_path_valid "$config" && valid_hash "$config_hash" || { set_status invalid_binding; exit 1; }
config_table=$(/system/bin/toybox cat "$CONFIGS") source_count=0 primary_found=0
while IFS= read -r row || [ -n "$row" ]; do
    expected_hash=${row%% *} file_path=${row#* }
    [ "$row" != "$expected_hash" ] && valid_hash "$expected_hash" && config_path_valid "$file_path" || {
        set_status invalid_binding; exit 1;
    }
    source_count=$((source_count + 1))
    [ "$source_count" -le 24 ] || { set_status invalid_binding; exit 1; }
    [ "$file_path" = "$config" ] && [ "$expected_hash" = "$config_hash" ] && primary_found=1
done < "$CONFIGS"
[ "$primary_found" = 1 ] || { set_status invalid_binding; exit 1; }
desired=$(/system/bin/toybox cat "$TARGET")
uint "$desired" 255 || { set_status invalid_target; exit 1; }
mode_table=$(/system/bin/toybox cat "$MODES") expected_index=0 desired_chip=
while IFS= read -r row || [ -n "$row" ]; do
    index=${row%%,*} chip=${row#*,}
    [ "$row" != "$index" ] && uint "$index" 255 && uint "$chip" 2147483647 &&
        [ "$index" -eq "$expected_index" ] || { set_status invalid_modes; exit 1; }
    [ "$index" = "$desired" ] && desired_chip=$chip
    expected_index=$((expected_index + 1))
done < "$MODES"
[ "$expected_index" -ge 2 ] && [ -n "$desired_chip" ] || { set_status invalid_target; exit 1; }

binding_files_match() {
    [ "$(file_hash "$ENDPOINT" 512)" = "$endpoint_hash" ] &&
        [ "$(file_hash "$MODES" 4096)" = "$modes_hash" ] &&
        [ "$(file_hash "$TARGET" 4)" = "$target_hash" ] &&
        [ "$(file_hash "$CONFIGS" 8192)" = "$configs_hash" ]
}

binding_matches() { binding_files_match && configs_match; }

configs_match() {
    while IFS= read -r row || [ -n "$row" ]; do
        expected_hash=${row%% *} file_path=${row#* }
        config_path_valid "$file_path" && [ "$(file_hash "$file_path" 1048576)" = "$expected_hash" ] || return 1
    done <<EOF
$config_table
EOF
}

mode_matches() {
    case "
$mode_table
" in *"
$1
"*) return 0 ;; esac
    return 1
}

call_service() { /system/bin/toybox timeout 3 /system/bin/service call "$service" "$1" i32 "$panel" i32 182; }
set_status starting
binding_matches || { set_status binding_changed; exit 1; }
reply=$(call_service 2 2>/dev/null) && supported "$reply" || { set_status unsupported; exit 1; }
reply=$(call_service 3 2>/dev/null) && current=$(decode_mode "$reply") && mode_matches "$current" || {
    set_status unknown_mode; exit 1;
}
set_status active
validation_ticks=0
while :; do
    binding_files_match || { set_status binding_changed; exit 1; }
    if [ "$validation_ticks" -ge 30 ]; then
        binding_matches || { set_status binding_changed; exit 1; }
        validation_ticks=0
    fi
    if /system/bin/dumpsys power 2>/dev/null | /system/bin/toybox grep -q 'mWakefulness=Awake'; then
        reply=$(call_service 2 2>/dev/null) && supported "$reply" || { set_status unsupported; exit 1; }
        reply=$(call_service 3 2>/dev/null) && current=$(decode_mode "$reply") && mode_matches "$current" || {
            set_status unknown_mode; exit 1;
        }
        if [ "$current" != "$desired,$desired_chip" ]; then
            binding_matches || { set_status binding_changed; exit 1; }
            validation_ticks=0
            /system/bin/toybox timeout 3 /system/bin/service call "$service" 4 i32 "$panel" i32 182 s16 "$desired" >/dev/null 2>&1 || {
                set_status write_failed; exit 1;
            }
            /system/bin/toybox sleep 0.15
            reply=$(call_service 3 2>/dev/null) && after=$(decode_mode "$reply") &&
                [ "$after" = "$desired,$desired_chip" ] || { set_status verify_failed; exit 1; }
        fi
        set_status active
        /system/bin/toybox sleep 1
        validation_ticks=$((validation_ticks + 1))
    else
        set_status sleeping
        /system/bin/toybox sleep 4
        validation_ticks=$((validation_ticks + 4))
    fi
done

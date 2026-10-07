package com.mi.onextbox.lsp

/** Private, verified DB/WAL copy, never changes the source. */
internal object LspDbSnapshotScript {
    fun command(sourcePath: String, targetPath: String, uid: Int): String {
        require(uid >= 0)
        val source = quote(sourcePath)
        val target = quote(targetPath)
        val dollar = '$'
        val script = """
            set -eu
            src=$source
            dst=$target
            if [ ! -f "$dollar{src}" ]; then printf 'missing\n'; exit 1; fi
            [ -s "$dollar{src}" ] || exit 1
            fingerprint() {
                base="$dollar{1}"
                [ ! -s "$dollar{base}-journal" ] || return 1
                for suffix in '' '-wal'; do
                    file="$dollar{base}$dollar{suffix}"
                    if [ -f "$dollar{file}" ]; then
                        digest=$dollar(/system/bin/toybox sha256sum "$dollar{file}") || return 1
                        printf '%s:%s\n' "$dollar{suffix}" "$dollar{digest%% *}"
                    else
                        printf '%s:absent\n' "$dollar{suffix}"
                    fi
                done
            }
            for attempt in 1 2; do
                before=$dollar(fingerprint "$dollar{src}") || exit 1
                /system/bin/toybox cp "$dollar{src}" "$dollar{dst}" || exit 1
                if [ -f "$dollar{src}-wal" ]; then
                    /system/bin/toybox cp "$dollar{src}-wal" "$dollar{dst}-wal" || exit 1
                else
                    /system/bin/toybox rm -f "$dollar{dst}-wal" || exit 1
                fi
                copied=$dollar(fingerprint "$dollar{dst}") || exit 1
                after=$dollar(fingerprint "$dollar{src}") || exit 1
                if [ "$dollar{before}" = "$dollar{copied}" ] && [ "$dollar{before}" = "$dollar{after}" ]; then
                    /system/bin/toybox chown $uid:$uid "$dollar{dst}" || exit 1
                    /system/bin/toybox chmod 600 "$dollar{dst}" || exit 1
                    if [ -f "$dollar{dst}-wal" ]; then
                        /system/bin/toybox chown $uid:$uid "$dollar{dst}-wal" || exit 1
                        /system/bin/toybox chmod 600 "$dollar{dst}-wal" || exit 1
                    fi
                    exit 0
                fi
            done
            exit 1
        """.trimIndent()
        return "/system/bin/toybox timeout 5 /system/bin/sh -c " + quote(script)
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"
}

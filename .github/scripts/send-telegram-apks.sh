#!/usr/bin/env bash
set -euo pipefail

: "${TG_BOT_TOKEN:?Missing TELEGRAM_BOT_TOKEN}"
if [[ "$BUILD_CHANNEL" == ci ]]; then
    channel_chat="${TG_CI_CHAT_ID:-}"
    thread="${TG_CI_THREAD_ID:-}"
    channel=CI
else
    channel_chat="${TG_RELEASE_CHAT_ID:-}"
    thread="${TG_RELEASE_THREAD_ID:-}"
    channel=Release
fi

if [[ -n "$channel_chat" ]]; then
    destination=(--form-string "chat_id=$channel_chat")
else
    : "${TG_CHAT_ID:?Set a Telegram channel chat ID or TELEGRAM_CHAT_ID}"
    [[ "$thread" =~ ^[0-9]+$ ]] || { echo "::error::Missing Telegram topic ID for $channel."; exit 1; }
    destination=(--form-string "chat_id=$TG_CHAT_ID" --form-string "message_thread_id=$thread")
fi

release_json=
changelog=
if [[ "$channel" == Release ]]; then
    notes_dir=$(mktemp -d)
    release_json="$notes_dir/release.json"
    changelog="$notes_dir/ONextBox-$VERSION_NAME-changelog.md"
    trap 'rm -f -- "$release_json" "$changelog"; rmdir -- "$notes_dir"' EXIT

    read_release() {
        gh release view "$1" --repo "$GITHUB_REPOSITORY" --json body,url,isDraft
    }

    if [[ "$GITHUB_REF_TYPE" == tag ]]; then
        read_release "$GITHUB_REF_NAME" >"$release_json"
    elif ! read_release "V$VERSION_NAME" >"$release_json" 2>/dev/null &&
        ! read_release "v$VERSION_NAME" >"$release_json" 2>/dev/null; then
        printf '{}\n' >"$release_json"
    fi
fi

media=$(python3 - "$channel" "$release_json" "$changelog" <<'PY'
import json
import os
import re
import sys
from html import escape, unescape
from pathlib import Path
from subprocess import check_output


def git(*args):
    return check_output(("git", *args), encoding="utf-8", errors="replace").rstrip("\n")


def commits_for_build(event):
    if event.get("commits"):
        return event["commits"]
    if event.get("head_commit"):
        return [event["head_commit"]]
    message, author = git("log", "-1", "--format=%B%x00%an").split("\0", 1)
    return [{"message": message, "author": {"name": author}}]


def commit_summary(commits):
    lines = []
    remaining = 600
    for commit in reversed(commits):
        title, separator, _ = (commit.get("message") or "").strip().partition("\n")
        title = title.strip() or "(no commit title)"
        if separator:
            title += " [..]"
        if len(title) > 100:
            title = title[:97] + "..."

        author = commit.get("author") or {}
        name = author.get("username") or author.get("name") or ""
        name = " ".join(name.split())[:60]
        line = f"{title} by {name}" if name else title
        length = len((line + "\n").encode("utf-16-le")) // 2
        if length > remaining:
            lines.append(f"(other {len(commits) - len(lines)} commits)")
            break
        lines.append(line)
        remaining -= length

    return "\n".join(reversed(lines))


def caption_length(markup):
    text = unescape(re.sub(r"<[^>]*>", "", markup))
    return len(text.encode("utf-16-le")) // 2


def release_caption(header, release, run_link, changelog_path):
    if release.get("isDraft"):
        release = {}
    release_url = release.get("url")
    links = [run_link]
    if release_url:
        links.insert(0, f'<a href="{escape(release_url)}">GitHub Release</a>')
    footer = "\n\n" + " · ".join(links)

    body = release.get("body") or ""
    notes = re.sub(r"<!--.*?-->", "", body, flags=re.DOTALL)
    notes = re.sub(r"(?m)^ {0,3}#{1,6}[ \t]+", "", notes)
    notes = re.sub(r"(?m)^([ \t]*)[-*+] +", r"\1• ", notes).strip()
    if not notes:
        return header + footer

    prefix = header + "\n\n<b>Release notes</b>\n<blockquote expandable>"
    full_caption = prefix + escape(notes) + "</blockquote>" + footer
    if caption_length(full_caption) <= 1024:
        return full_caption

    Path(changelog_path).write_bytes(body.encode("utf-8"))
    suffix = "…</blockquote>\n\nFull notes attached." + footer
    available = 1024 - caption_length(prefix + suffix)
    preview = notes.encode("utf-16-le")[:2 * available].decode("utf-16-le", errors="ignore")
    return prefix + escape(preview.rstrip()) + suffix


env = os.environ
channel = sys.argv[1]
repository = f"{env['GITHUB_SERVER_URL']}/{env['GITHUB_REPOSITORY']}"
ref_label = "Tag" if env["GITHUB_REF_TYPE"] == "tag" else "Branch"
ref_name = env["GITHUB_REF_NAME"][:80]
header = [f"<b>ONextBox {channel}</b>", f"{ref_label}: {escape(ref_name)}"]
run_url = f"{repository}/actions/runs/{env['GITHUB_RUN_ID']}"
run_link = f'<a href="{escape(run_url)}">Workflow run</a>'
version = escape(f"{env['VERSION_NAME']} ({env['VERSION_CODE']})")

if channel == "CI":
    event_path = env.get("GITHUB_EVENT_PATH")
    event = json.loads(Path(event_path).read_text(encoding="utf-8")) if event_path else {}
    header.extend(("#ci_" + git("rev-list", "--count", "HEAD"), version))
    summary = escape(commit_summary(commits_for_build(event)))
    compare_url = event.get("compare") or ""
    if compare_url.startswith(f"{repository}/compare/"):
        changes_url, changes_label = compare_url, "Compare"
    else:
        changes_url, changes_label = f"{repository}/commit/{env['GITHUB_SHA']}", "Commit"
    changes_link = f'<a href="{escape(changes_url)}">{changes_label}</a>'
    caption = "\n".join((*header, f"\n<pre>{summary}</pre>\n", f"{changes_link} · {run_link}"))
else:
    release = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
    caption = release_caption("\n".join((*header, version)), release, run_link, sys.argv[3])

media = [
    {
        "type": "document",
        "media": "attach://release",
        "caption": caption,
        "parse_mode": "HTML",
    },
    {"type": "document", "media": "attach://debug"},
]
if sys.argv[3] and Path(sys.argv[3]).is_file():
    media.append({"type": "document", "media": "attach://changelog"})
print(json.dumps(media))
PY
)
notes_upload=()
expected_files=2
if [[ -f "$changelog" ]]; then
    notes_upload=(--form "changelog=@$changelog;type=text/markdown")
    expected_files=3
fi
response=$(curl --silent --show-error --max-time 240 \
    "${destination[@]}" \
    --form-string "media=$media" \
    --form "release=@dist/onextbox-$VERSION_NAME-EX01-release.apk" \
    --form "debug=@dist/onextbox-$VERSION_NAME-EX01-debug.apk" \
    "${notes_upload[@]}" \
    "https://api.telegram.org/bot$TG_BOT_TOKEN/sendMediaGroup")
if ! jq -e --argjson expected "$expected_files" '.ok and (.result | length == $expected)' > /dev/null <<<"$response"; then
    jq -r '"::error::Telegram upload failed: " + (.description // "invalid response")' <<<"$response"
    exit 1
fi

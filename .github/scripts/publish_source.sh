#!/usr/bin/env bash
set -euo pipefail

SOURCE="$(realpath "${1:?Source checkout required}")"
DESTINATION="$(realpath "${2:?Public checkout required}")"
PYTHON="${PYTHON:-python3}"

# Restrict operations to two separate, dedicated runner checkouts.
for CHECKOUT in "${SOURCE}" "${DESTINATION}"; do
  if [[ "$(realpath "$(git -C "${CHECKOUT}" rev-parse --show-toplevel)")" != "${CHECKOUT}" ]]; then
    echo "A dedicated repository checkout is required." >&2
    exit 1
  fi
done
if [[ "${SOURCE}" == "${DESTINATION}" || "${SOURCE}" == "${DESTINATION}/"* || "${DESTINATION}" == "${SOURCE}/"* ]]; then
  echo "Checkouts must be separate." >&2
  exit 1
fi

git -C "${DESTINATION}" config user.name "MiToverG422"
git -C "${DESTINATION}" config user.email "59342133+MiToverG422@users.noreply.github.com"

for ATTEMPT in 1 2 3; do
  # Establish the public baseline before reading the latest source revision.
  # This prevents an older in-flight job from overwriting a newer snapshot.
  git -C "${DESTINATION}" fetch --quiet origin main
  git -C "${DESTINATION}" checkout --quiet -B main origin/main
  if ! git -C "${SOURCE}" fetch --quiet origin main 2>/dev/null; then
    echo "Unable to refresh source checkout; review credentials privately." >&2
    exit 1
  fi
  git -C "${SOURCE}" checkout --quiet --detach FETCH_HEAD

  "${PYTHON}" "${DESTINATION}/.github/scripts/sync_source.py" --source "${SOURCE}" --destination "${DESTINATION}"
  git -C "${DESTINATION}" add -A
  if git -C "${DESTINATION}" diff --cached --quiet; then
    echo "Source snapshot is already up to date."
    exit 0
  fi
  git -C "${DESTINATION}" -c core.whitespace=-blank-at-eof diff --cached --check
  git -C "${DESTINATION}" commit -m "chore: update public source snapshot"
  if git -C "${DESTINATION}" push origin HEAD:main; then
    exit 0
  fi
  echo "Push attempt ${ATTEMPT} failed; refreshing both snapshots before retrying."
  sleep 3
done

echo "Unable to publish after three attempts; no force push was used." >&2
exit 1

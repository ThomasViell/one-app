#!/usr/bin/env bash
# Mirror DrainQ.ONE releases from private GitHub to local webroot.
# Reads PAT from /etc/drainq/github-pat-one (operator-managed, never in repo).
# Run as oneshot via drainq-one-mirror.service / drainq-one-mirror.timer.
set -euo pipefail

REPO="ThomasViell/one-app"
DEST="/var/www/drainq-updates/one"
PAT_FILE="/etc/drainq/github-pat-one"
API_BASE="https://api.github.com"
LOCK_FILE="/run/drainq-mirror-one.lock"
LOG_TAG="drainq-one-mirror"

# ---------- helpers ----------

log()  { echo "$(date -u +%FT%TZ) [${LOG_TAG}] $*"; }
err()  { log "ERROR: $*" >&2; }
die()  { err "$*"; exit 1; }

# ---------- pre-flight ----------

[[ -f "${PAT_FILE}" ]] || die "PAT file not found: ${PAT_FILE}"
[[ -r "${PAT_FILE}" ]] || die "PAT file not readable: ${PAT_FILE}"

PAT="$(< "${PAT_FILE}")"
PAT="${PAT//[$'\t\r\n ']}"   # strip whitespace
[[ -n "${PAT}" ]]             || die "PAT file is empty: ${PAT_FILE}"
[[ "${PAT}" =~ ^(ghp_|github_pat_)[A-Za-z0-9_]{20,} ]] \
  || die "PAT does not look like a valid GitHub token (wrong prefix or length)"

[[ -d "${DEST}" ]] || { log "Creating destination ${DEST}"; mkdir -p "${DEST}"; }

# ---------- lock ----------

exec 9>"${LOCK_FILE}"
flock -n 9 || die "Another mirror run is already active (lock: ${LOCK_FILE})"

# ---------- fetch latest release ----------

log "Fetching latest release for ${REPO}"
RELEASE_JSON="$(curl -fsSL \
  -H "Authorization: Bearer ${PAT}" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "${API_BASE}/repos/${REPO}/releases/latest")"

TAG="$(printf '%s' "${RELEASE_JSON}" | python3 -c \
  "import sys,json; r=json.load(sys.stdin); print(r['tag_name'])")"
log "Latest release tag: ${TAG}"

# ---------- collect assets ----------

ASSETS="$(printf '%s' "${RELEASE_JSON}" | python3 -c "
import sys, json
release = json.load(sys.stdin)
wanted_exts = ('.apk', '.sha256', '.json')
for a in release.get('assets', []):
    name = a['name']
    if any(name.endswith(ext) for ext in wanted_exts):
        print(a['url'], name)
")"

if [[ -z "${ASSETS}" ]]; then
  log "No relevant assets found in release ${TAG} — nothing to do"
  exit 0
fi

# ---------- download assets ----------

DL_COUNT=0
while IFS=' ' read -r ASSET_URL ASSET_NAME; do
  DEST_FILE="${DEST}/${ASSET_NAME}"

  # Skip if already present — immutable release assets don't change
  if [[ -f "${DEST_FILE}" ]]; then
    log "Already present, skipping: ${ASSET_NAME}"
    continue
  fi

  log "Downloading ${ASSET_NAME}"
  TMP_FILE="$(mktemp "${DEST}/.tmp.XXXXXX")"
  trap 'rm -f "${TMP_FILE}"' EXIT

  curl -fsSL \
    -H "Authorization: Bearer ${PAT}" \
    -H "Accept: application/octet-stream" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "${ASSET_URL}" \
    -o "${TMP_FILE}"

  chmod 0644 "${TMP_FILE}"
  mv "${TMP_FILE}" "${DEST_FILE}"
  trap - EXIT
  log "Saved: ${DEST_FILE}"
  DL_COUNT=$(( DL_COUNT + 1 ))
done <<< "${ASSETS}"

# ---------- manifest symlink (latest always readable) ----------
# releases.stable.json and releases.beta.json are downloaded by name above;
# no symlink needed — clients address them by their canonical name.

# ---------- cleanup old versions (keep last 3 APKs) ----------
# Keeps the webroot lean; sha256 files of kept APKs are preserved automatically
# because they share the same basename.
APK_COUNT="$(find "${DEST}" -maxdepth 1 -name '*.apk' | wc -l)"
if (( APK_COUNT > 3 )); then
  log "Pruning old APKs (found ${APK_COUNT}, keeping newest 3)"
  find "${DEST}" -maxdepth 1 -name '*.apk' -printf '%T@ %p\n' \
    | sort -n \
    | head -n $(( APK_COUNT - 3 )) \
    | while read -r _ OLD_APK; do
        SHA="${OLD_APK%.apk}.apk.sha256"
        log "Removing: $(basename "${OLD_APK}")"
        rm -f "${OLD_APK}" "${SHA}"
      done
fi

log "Done. ${DL_COUNT} new file(s) downloaded."

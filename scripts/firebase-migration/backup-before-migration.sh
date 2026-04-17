#!/usr/bin/env bash
#
# backup-before-migration.sh
#
# Run this BEFORE Phase 2 (OAuth client migration) to capture everything
# needed for a rollback. Produces a timestamped backup directory with:
#   - All current google-services.json files
#   - OAuth client metadata exported from sq-bitkey-prod via gcloud
#   - A manifest summarizing what was captured
#
# Prerequisites:
#   - gcloud CLI installed and authenticated
#   - Access to sq-bitkey-prod project
#   - Run from the wallet repo root
#
# Usage:
#   ./scripts/firebase-migration/backup-before-migration.sh
#
set -euo pipefail

# ─── Prerequisites check ──────────────────────────────────────────────────
if ! command -v gcloud &>/dev/null; then
  echo "⚠ gcloud CLI not found."
  echo "  Install: brew install --cask google-cloud-sdk"
  echo ""
  echo "  The script will still capture the critical data (OAuth client details"
  echo "  from google-services.json), but will skip the optional GCP API export."
  echo ""
fi

if command -v gcloud &>/dev/null && ! gcloud auth print-access-token --project=sq-bitkey-prod &>/dev/null; then
  echo "⚠ Not authenticated to GCP. To enable the optional API export, run:"
  echo ""
  echo "  gcloud auth login"
  echo "  gcloud config set project sq-bitkey-prod"
  echo ""
  echo "  Continuing without API export — local file backup will still work."
  echo ""
fi

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="scripts/firebase-migration/backups/${TIMESTAMP}"
PROD_PROJECT="sq-bitkey-prod"

echo "=== Firebase Migration Backup ==="
echo "Timestamp: ${TIMESTAMP}"
echo "Backup dir: ${BACKUP_DIR}"
echo ""

mkdir -p "${BACKUP_DIR}/google-services-json"
mkdir -p "${BACKUP_DIR}/oauth-clients"

# ─── 1. Snapshot all google-services.json files ────────────────────────────
echo "▸ Backing up google-services.json files..."

# Root-level (production fallback)
if [ -f "app/android/app/google-services.json" ]; then
  cp "app/android/app/google-services.json" \
     "${BACKUP_DIR}/google-services-json/app-root-google-services.json"
  echo "  ✓ app/android/app/google-services.json"
fi

# Variant-specific (if they exist pre-migration)
for variant_dir in app/android/app/src/*/; do
  variant=$(basename "${variant_dir}")
  if [ -f "${variant_dir}google-services.json" ]; then
    cp "${variant_dir}google-services.json" \
       "${BACKUP_DIR}/google-services-json/${variant}-google-services.json"
    echo "  ✓ ${variant_dir}google-services.json"
  fi
done

# ─── 2. Export OAuth 2.0 client IDs from sq-bitkey-prod ────────────────────
echo ""
echo "▸ Exporting OAuth client metadata from ${PROD_PROJECT}..."

# Use gcloud to list all OAuth brand + clients.
# The "OAuth brand" (consent screen) is needed to recreate clients.
# NOTE: gcloud alpha iap oauth-brands is the only CLI path to list these.

# Export the full credentials page data via REST API
ACCESS_TOKEN=$(gcloud auth print-access-token --project="${PROD_PROJECT}" 2>/dev/null || true)

if [ -n "${ACCESS_TOKEN}" ]; then
  echo "  ✓ Authenticated to GCP"

  # List all OAuth 2.0 client IDs via the API
  curl -s -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    "https://oauth2.googleapis.com/v1/projects/${PROD_PROJECT}/oauthClients?pageSize=100" \
    > "${BACKUP_DIR}/oauth-clients/all-oauth-clients-api.json" 2>/dev/null || true

  # Also grab via the Cloud Resource Manager / IAM approach
  # The most reliable way: list credentials via the serviceusage/credentials endpoint
  curl -s -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    "https://content-cloudresourcemanager.googleapis.com/v1/projects/${PROD_PROJECT}" \
    > "${BACKUP_DIR}/oauth-clients/project-metadata.json" 2>/dev/null || true

  echo "  ✓ API export attempted"
else
  echo "  ⚠ Could not get access token. Skipping API export."
  echo "    Run: gcloud auth login --project=${PROD_PROJECT}"
fi

# ─── 3. Extract and save the specific OAuth clients we care about ──────────
echo ""
echo "▸ Extracting OAuth client details from google-services.json..."

# Parse the production google-services.json to get the exact OAuth client
# details that would need to be re-created during rollback.
export BACKUP_DIR
python3 - <<'PYEOF'
import json
import os
import sys

backup_dir = os.environ["BACKUP_DIR"]

with open("app/android/app/google-services.json") as f:
    data = json.load(f)

rollback_clients = []

for client in data.get("client", []):
    client_info = client.get("client_info", {})
    android_info = client_info.get("android_client_info", {})
    package_name = android_info.get("package_name", "unknown")

    for oauth in client.get("oauth_client", []):
        oauth_android = oauth.get("android_info", {})
        entry = {
            "package_name": package_name,
            "mobilesdk_app_id": client_info.get("mobilesdk_app_id"),
            "oauth_client_id": oauth.get("client_id"),
            "client_type": oauth.get("client_type"),
            "sha1_fingerprint": oauth_android.get("certificate_hash"),
            "api_key": client.get("api_key", [{}])[0].get("current_key"),
            "project_id": data["project_info"]["project_id"],
            "project_number": data["project_info"]["project_number"],
        }
        rollback_clients.append(entry)

        # Only flag the ones being migrated away
        if package_name in ("world.bitkey.debug", "world.bitkey.team"):
            entry["_migration_note"] = "This client will be DELETED from prod and recreated in a new project. Rollback = recreate in prod."

output_path = os.path.join(backup_dir, "oauth-clients", "rollback-oauth-clients.json")
with open(output_path, "w") as f:
    json.dump(rollback_clients, f, indent=2)

print(f"  ✓ Saved {len(rollback_clients)} OAuth client records to rollback-oauth-clients.json")

# Also create a human-readable summary
summary_path = os.path.join(backup_dir, "oauth-clients", "rollback-reference.txt")
with open(summary_path, "w") as f:
    f.write("=== OAuth Clients That Must Be Recreated During Rollback ===\n\n")
    for c in rollback_clients:
        if c["package_name"] in ("world.bitkey.debug", "world.bitkey.team"):
            f.write(f"Package:      {c['package_name']}\n")
            f.write(f"OAuth Client: {c['oauth_client_id']}\n")
            f.write(f"SHA-1:        {c['sha1_fingerprint']}\n")
            f.write(f"API Key:      {c['api_key']}\n")
            f.write(f"App ID:       {c['mobilesdk_app_id']}\n")
            f.write(f"Project:      {c['project_id']} ({c['project_number']})\n")
            f.write(f"\n")

print(f"  ✓ Saved human-readable reference to rollback-reference.txt")
PYEOF

# ─── 4. Record git state ──────────────────────────────────────────────────
echo ""
echo "▸ Recording git state..."

git rev-parse HEAD > "${BACKUP_DIR}/git-head-sha.txt"
git log --oneline -1 > "${BACKUP_DIR}/git-head-description.txt"
git diff --stat HEAD > "${BACKUP_DIR}/git-uncommitted-changes.txt" 2>/dev/null || true

echo "  ✓ Git SHA: $(cat ${BACKUP_DIR}/git-head-sha.txt)"

# ─── 5. Generate manifest ─────────────────────────────────────────────────
echo ""
echo "▸ Generating manifest..."

cat > "${BACKUP_DIR}/MANIFEST.md" <<EOF
# Firebase Migration Backup - ${TIMESTAMP}

## What's in this backup

| File | Purpose |
|------|---------|
| \`google-services-json/app-root-google-services.json\` | Production fallback config (the file that currently serves ALL variants) |
| \`google-services-json/{variant}-google-services.json\` | Any variant-specific configs that existed pre-migration |
| \`oauth-clients/rollback-oauth-clients.json\` | Machine-readable OAuth client details extracted from prod config |
| \`oauth-clients/rollback-reference.txt\` | Human-readable reference for manual rollback |
| \`oauth-clients/all-oauth-clients-api.json\` | Raw API export of all OAuth clients (if API call succeeded) |
| \`oauth-clients/project-metadata.json\` | GCP project metadata |
| \`git-head-sha.txt\` | Git commit SHA at time of backup |

## How to use for rollback

See \`rollback-migration.sh\` in the parent directory, or follow manual steps in the PR description.

## Projects involved

| Project | ID | Number | Role |
|---------|-----|--------|------|
| Production | sq-bitkey-prod | 496981653612 | Current home of ALL OAuth clients |
| Dev | sq-bitkey-dev | 236544073680 | Target for debug build |
| Team | sq-bitkey-team-prod | 1058718539500 | Target for team build |
EOF

echo "  ✓ Manifest written"

# ─── Done ──────────────────────────────────────────────────────────────────
echo ""
echo "=== Backup complete ==="
echo "Location: ${BACKUP_DIR}"
echo ""
echo "Contents:"
find "${BACKUP_DIR}" -type f | sort | while read -r f; do
  echo "  ${f}"
done
echo ""
echo "NEXT: Proceed with Phase 2 (OAuth migration) when ready."
echo "      If anything goes wrong, run: ./scripts/firebase-migration/rollback-migration.sh ${BACKUP_DIR}"

#!/usr/bin/env bash
#
# rollback-migration.sh
#
# Rolls back the Firebase/GCP project migration by:
#   1. Restoring the original google-services.json files
#   2. Deleting OAuth clients from the new projects (if they were created)
#   3. Recreating OAuth clients in sq-bitkey-prod
#   4. Re-downloading the prod google-services.json
#
# Usage:
#   ./scripts/firebase-migration/rollback-migration.sh <backup-dir>
#
# Example:
#   ./scripts/firebase-migration/rollback-migration.sh scripts/firebase-migration/backups/20250115_143022
#
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <backup-directory>"
  echo ""
  echo "Available backups:"
  ls -d scripts/firebase-migration/backups/*/ 2>/dev/null || echo "  (none found)"
  exit 1
fi

BACKUP_DIR="$1"

if [ ! -d "${BACKUP_DIR}" ]; then
  echo "ERROR: Backup directory not found: ${BACKUP_DIR}"
  exit 1
fi

if [ ! -f "${BACKUP_DIR}/oauth-clients/rollback-oauth-clients.json" ]; then
  echo "ERROR: rollback-oauth-clients.json not found in backup. Is this a valid backup?"
  exit 1
fi

echo "=== Firebase Migration Rollback ==="
echo "Using backup: ${BACKUP_DIR}"
echo ""

# ─── Phase 1: Restore google-services.json files ──────────────────────────
echo "▸ Phase 1: Restoring google-services.json files..."

# Restore root-level production config
if [ -f "${BACKUP_DIR}/google-services-json/app-root-google-services.json" ]; then
  cp "${BACKUP_DIR}/google-services-json/app-root-google-services.json" \
     "app/android/app/google-services.json"
  echo "  ✓ Restored app/android/app/google-services.json"
fi

# Remove variant-specific files that the migration added
echo ""
echo "  Removing variant-specific google-services.json files added by migration..."
for variant in debug team; do
  target="app/android/app/src/${variant}/google-services.json"
  if [ -f "${target}" ]; then
    rm "${target}"
    echo "  ✓ Removed ${target}"
  else
    echo "  ⊘ ${target} (not present, nothing to remove)"
  fi
done

# ─── Phase 2: Delete OAuth clients from new projects ──────────────────────
echo ""
echo "▸ Phase 2: Clean up OAuth clients in new projects..."
echo ""
echo "  ⚠ MANUAL STEP REQUIRED"
echo ""
echo "  If OAuth clients were created in the new projects, delete them now:"
echo ""
echo "  1. sq-bitkey-dev → https://console.cloud.google.com/apis/credentials?project=sq-bitkey-dev"
echo "     Delete any OAuth 2.0 Client ID for 'world.bitkey.debug'"
echo ""
echo "  2. sq-bitkey-team-prod → https://console.cloud.google.com/apis/credentials?project=sq-bitkey-team-prod"
echo "     Delete any OAuth 2.0 Client ID for 'world.bitkey.team'"
echo ""
read -p "  Press ENTER when done (or if no clients were created in new projects)... "

# ─── Phase 3: Recreate OAuth clients in sq-bitkey-prod ─────────────────────
echo ""
echo "▸ Phase 3: Recreate OAuth clients in sq-bitkey-prod..."
echo ""
echo "  The following OAuth clients need to be recreated in sq-bitkey-prod."
echo "  All values are from your pre-migration backup."
echo ""

export BACKUP_DIR
python3 - <<PYEOF
import json
import os

backup_dir = os.environ["BACKUP_DIR"]
with open(os.path.join(backup_dir, "oauth-clients", "rollback-oauth-clients.json")) as f:
    clients = json.load(f)

migrated = [c for c in clients if c["package_name"] in ("world.bitkey.debug", "world.bitkey.team")]

if not migrated:
    print("  ✓ No migrated clients found in backup — nothing to recreate.")
else:
    print("  Go to: https://console.cloud.google.com/apis/credentials?project=sq-bitkey-prod")
    print("  Click '+ CREATE CREDENTIALS' → 'OAuth client ID' for each:")
    print()
    for c in migrated:
        print(f"  ┌─ {c['package_name']}")
        print(f"  │  Application type: Android")
        print(f"  │  Package name:     {c['package_name']}")
        print(f"  │  SHA-1:            {c['sha1_fingerprint']}")
        print(f"  │  Original client:  {c['oauth_client_id']}")
        print(f"  └─")
        print()

    print("  ⚠ Wait 5-30 minutes after creation for propagation.")
PYEOF

echo ""
read -p "  Press ENTER when OAuth clients have been recreated in sq-bitkey-prod... "

# ─── Phase 4: Re-download production google-services.json ──────────────────
echo ""
echo "▸ Phase 4: Re-download production google-services.json"
echo ""
echo "  After recreating OAuth clients, the prod google-services.json needs"
echo "  to be re-downloaded to include the new OAuth client IDs."
echo ""
echo "  1. Go to: https://console.firebase.google.com/project/sq-bitkey-prod/settings/general"
echo "  2. Under 'Your apps' → Android app"
echo "  3. Click 'Download google-services.json'"
echo "  4. Replace: app/android/app/google-services.json"
echo ""
echo "  Alternatively, if the backup copy already has the correct OAuth clients"
echo "  (which it should — it was taken pre-migration), the restored file from"
echo "  Phase 1 should already be correct."
echo ""

# ─── Phase 5: Verify ──────────────────────────────────────────────────────
echo "▸ Phase 5: Verification"
echo ""

# Check that variant-specific files are gone
ISSUES=0
for variant in debug team; do
  if [ -f "app/android/app/src/${variant}/google-services.json" ]; then
    echo "  ✗ PROBLEM: app/android/app/src/${variant}/google-services.json still exists!"
    ISSUES=$((ISSUES + 1))
  fi
done

# Check that root config exists and has OAuth clients
if [ -f "app/android/app/google-services.json" ]; then
  OAUTH_COUNT=$(python3 -c "
import json
with open('app/android/app/google-services.json') as f:
    d = json.load(f)
count = sum(len(c.get('oauth_client', [])) for c in d.get('client', []))
print(count)
")
  echo "  Root google-services.json has ${OAUTH_COUNT} OAuth client(s)"
  if [ "${OAUTH_COUNT}" -lt 4 ]; then
    echo "  ⚠ Expected 4 OAuth clients in prod config. You may need to re-download."
    ISSUES=$((ISSUES + 1))
  fi
else
  echo "  ✗ PROBLEM: app/android/app/google-services.json is missing!"
  ISSUES=$((ISSUES + 1))
fi

echo ""
if [ "${ISSUES}" -eq 0 ]; then
  echo "=== Rollback complete ✓ ==="
  echo ""
  echo "Next steps:"
  echo "  1. Build and test debug/team variants to confirm they work"
  echo "  2. Commit the restored files:"
  echo "     git add app/android/app/google-services.json"
  echo "     git add -u app/android/app/src/"
  echo "     git commit -m 'Rollback: restore original firebase config'"
  echo "     git push"
else
  echo "=== Rollback completed with ${ISSUES} warning(s) — review above ==="
fi

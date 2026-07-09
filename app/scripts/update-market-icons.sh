#!/usr/bin/env bash
set -euo pipefail

# This script reports which Market drawables in the wallet app correspond to
# icons in the squareup/market repository.
#
# It no longer generates MarketIcon / MarketIcons Kotlin sources — those have
# been removed in favor of mapping individual `Icon` enum entries to drawable
# resources in `StyleDictionaryIcons.kt`. New market icons should be wired up
# manually there.

if ! command -v node >/dev/null; then
  echo "node is required to inspect the market repo." >&2
  exit 1
fi

if ! command -v npm >/dev/null; then
  echo "npm is required to inspect the market repo." >&2
  exit 1
fi

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
TMP_DIR=$(mktemp -d)
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

echo "Cloning squareup/market repository..."
MARKET_REPO="$TMP_DIR/market"
git clone --depth 1 https://github.com/squareup/market "$MARKET_REPO" >/dev/null 2>&1

echo "Installing dependencies..."
(
  cd "$MARKET_REPO/common/icons"
  npm ci --silent 2>/dev/null || npm install --silent
)

DEST_DRAWABLE_DIR="$REPO_ROOT/ui/framework/public/src/commonMain/composeResources/drawable"

echo "Reconciling Market icons against bundled drawables..."

node - "$MARKET_REPO/common/icons" "$DEST_DRAWABLE_DIR" <<'JS'
const fs = require('fs');
const path = require('path');

const iconsDir = process.argv[2];
const drawableDir = process.argv[3];

const manifestPath = path.join(iconsDir, 'manifest.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));

function snakeCase(str) {
  return str
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .replace(/[\s-]+/g, '_')
    .toLowerCase();
}

const svgDir = path.join(iconsDir, 'svg');
const existingSvgs = new Set();
if (fs.existsSync(svgDir)) {
  fs.readdirSync(svgDir).forEach(file => {
    if (file.endsWith('.svg')) {
      existingSvgs.add(file.replace('.svg', ''));
    }
  });
}

const icons = Object.entries(manifest.icons)
  .filter(([, icon]) => existingSvgs.has(icon.name))
  .sort((a, b) => a[1].name.localeCompare(b[1].name));

const existingDrawables = new Set();
if (fs.existsSync(drawableDir)) {
  fs.readdirSync(drawableDir).forEach(file => {
    if (file.startsWith('market_') && file.endsWith('.xml')) {
      existingDrawables.add(file.replace('.xml', ''));
    }
  });
}

let matched = 0;
let missingDrawable = 0;
icons.forEach(([, icon]) => {
  const drawableName = `market_${snakeCase(icon.name)}`;
  if (existingDrawables.has(drawableName)) {
    matched++;
  } else {
    missingDrawable++;
  }
});

console.log(`Manifest icons with SVGs: ${icons.length}`);
console.log(`Drawables already bundled: ${matched}`);
if (missingDrawable > 0) {
  console.log(`Manifest icons without a matching drawable: ${missingDrawable}`);
}
JS

echo "Done."
echo "Note: Drawable XML files must be generated separately using svg2vectordrawable or similar tool."
echo "To use a new market drawable, add an Icon enum entry in Icon.kt and map it in StyleDictionaryIcons.kt."

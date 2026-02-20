#!/usr/bin/env bash
set -euo pipefail

# This script updates Market icons from the squareup/market repository.
# It generates drawable XMLs and MarketIcons.generated.kt for use in the wallet app.

if ! command -v node >/dev/null; then
  echo "node is required to build icons from market repo." >&2
  exit 1
fi

if ! command -v npm >/dev/null; then
  echo "npm is required to build icons from market repo." >&2
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

# Generate drawables and Kotlin file
DEST_DRAWABLE_DIR="$REPO_ROOT/ui/framework/public/src/commonMain/composeResources/drawable"
DEST_KT_DIR="$REPO_ROOT/ui/framework/public/src/commonMain/kotlin/build/wallet/ui/tokens/market"
mkdir -p "$DEST_KT_DIR"
KOTLIN_DEST="$DEST_KT_DIR/MarketIcons.generated.kt"

echo "Generating Market icons..."

# Use node to generate the icons
node - "$MARKET_REPO/common/icons" "$DEST_DRAWABLE_DIR" "$KOTLIN_DEST" <<'JS'
const fs = require('fs');
const path = require('path');

const iconsDir = process.argv[2];
const drawableDir = process.argv[3];
const kotlinDest = process.argv[4];

// Load manifest
const manifestPath = path.join(iconsDir, 'manifest.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));

// Helper functions
function snakeCase(str) {
  return str
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .replace(/[\s-]+/g, '_')
    .toLowerCase();
}

function pascalCase(str) {
  return str
    .split(/[-_\s]+/)
    .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join('');
}

// Check which SVG files exist
const svgDir = path.join(iconsDir, 'svg');
const existingSvgs = new Set();
if (fs.existsSync(svgDir)) {
  fs.readdirSync(svgDir).forEach(file => {
    if (file.endsWith('.svg')) {
      existingSvgs.add(file.replace('.svg', ''));
    }
  });
}

// Filter icons that have SVG files
const icons = Object.entries(manifest.icons)
  .filter(([key, icon]) => existingSvgs.has(icon.name))
  .sort((a, b) => a[1].name.localeCompare(b[1].name));

if (icons.length === 0) {
  console.error('No icons found with SVG files');
  process.exit(1);
}

console.log(`Found ${icons.length} icons with SVG files`);

// Check which drawables already exist
const existingDrawables = new Set();
if (fs.existsSync(drawableDir)) {
  fs.readdirSync(drawableDir).forEach(file => {
    if (file.startsWith('market_') && file.endsWith('.xml')) {
      existingDrawables.add(file.replace('.xml', ''));
    }
  });
}

// Generate Kotlin file
const kotlinLines = [
  '// Don\'t edit manually.',
  '// Generated from market repo manifest.json',
  '',
  'package build.wallet.ui.tokens.market',
  '',
  'import bitkey.ui.framework_public.generated.resources.Res',
  'import bitkey.ui.framework_public.generated.resources.*',
  '',
  '/**',
  ' * Collection of [MarketIcon]s generated from the Market design system.',
  ' */',
  '@Suppress("LargeClass")',
  'public object MarketIcons {',
];

let skippedCount = 0;
let includedCount = 0;

icons.forEach(([key, icon], index) => {
  const drawableName = `market_${snakeCase(icon.name)}`;
  const propertyName = pascalCase(icon.name);
  
  // Only include icons that have drawable files
  if (!existingDrawables.has(drawableName)) {
    skippedCount++;
    return;
  }
  
  includedCount++;
  
  if (includedCount > 1) {
    kotlinLines.push('');
  }
  
  kotlinLines.push('  /**');
  kotlinLines.push(`   * The Market icon named '${icon.name}'.`);
  if (icon.description) {
    kotlinLines.push('   *');
    icon.description.split('\n').forEach(line => {
      kotlinLines.push(`   * ${line}`);
    });
  }
  kotlinLines.push('   */');
  kotlinLines.push(`  public val ${propertyName}: MarketIcon =`);
  kotlinLines.push(`    MarketIcon(Res.drawable.${drawableName}, ${icon.multicolor})`);
});

kotlinLines.push('}');
kotlinLines.push('');

fs.writeFileSync(kotlinDest, kotlinLines.join('\n'));

console.log(`Generated MarketIcons.generated.kt with ${includedCount} icons`);
if (skippedCount > 0) {
  console.log(`Skipped ${skippedCount} icons without drawable files`);
}
JS

echo "Market icons updated successfully!"
echo "Note: Drawable XML files must be generated separately using svg2vectordrawable or similar tool."
echo "The MarketIcons.generated.kt file has been updated to match existing drawable files."

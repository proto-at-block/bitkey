#!/usr/bin/env bash
set -euo pipefail

if ! command -v npm >/dev/null; then
  echo "npm is required to build icons via cash-design-system." >&2
  exit 1
fi

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
TMP_DIR=$(mktemp -d)
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

CASH_DESIGN_SYSTEM_REPO="$TMP_DIR/cash-design-system"
git clone --depth 1 https://github.com/squareup/cash-design-system "$CASH_DESIGN_SYSTEM_REPO" >/dev/null

(
  cd "$CASH_DESIGN_SYSTEM_REPO"
  if [[ -f package-lock.json ]]; then
    npm ci
  else
    npm install
  fi
  npm run build:tokens:android
)

SRC_DRAWABLE_DIR="$CASH_DESIGN_SYSTEM_REPO/generators/android/lib/arcade/src/main/res/drawable"
DEST_DRAWABLE_DIR="$REPO_ROOT/ui/framework/public/src/commonMain/composeResources/drawable"

if [[ ! -d "$SRC_DRAWABLE_DIR" ]]; then
  echo "Drawable directory not found after build: $SRC_DRAWABLE_DIR" >&2
  exit 1
fi

shopt -s nullglob
icon_drawables=("$SRC_DRAWABLE_DIR"/icon_*.xml)
if (( ${#icon_drawables[@]} == 0 )); then
  echo "No icon drawables were produced by cash-design-system." >&2
  exit 1
fi

find "$DEST_DRAWABLE_DIR" -maxdepth 1 -type f \( -name 'market_*' -o -name 'icon_*' \) -delete
cp "${icon_drawables[@]}" "$DEST_DRAWABLE_DIR"/
shopt -u nullglob

DEST_KT_DIR="$REPO_ROOT/ui/framework/public/src/commonMain/kotlin/build/wallet/ui/tokens/market"
mkdir -p "$DEST_KT_DIR"
KOTLIN_DEST="$DEST_KT_DIR/MarketIcons.generated.kt"

python3 - "$CASH_DESIGN_SYSTEM_REPO/assets/icons/icon.json" "$DEST_DRAWABLE_DIR" "$KOTLIN_DEST" <<'PY'
import json
import re
import sys
from pathlib import Path

icons_manifest = Path(sys.argv[1])
drawable_dir = Path(sys.argv[2])
output_path = Path(sys.argv[3])

data = json.loads(icons_manifest.read_text())
icons = data.get("asset", {}).get("icon", {})

if not icons:
  raise SystemExit("No icon metadata found in assets/icons/icon.json")

def camel_to_snake(name: str) -> str:
  name = re.sub("(.)([A-Z][a-z]+)", r"\1_\2", name)
  name = re.sub("([a-z0-9])([A-Z])", r"\1_\2", name)
  name = re.sub("([A-Za-z])([0-9])", r"\1_\2", name)
  name = re.sub("([0-9])([A-Za-z])", r"\1_\2", name)
  name = name.replace("__", "_")
  return name.lower()

def snake_to_pascal(name: str) -> str:
  return "".join(part.capitalize() for part in name.split("_") if part)

def is_multi_color(xml_path: Path) -> bool:
  text = xml_path.read_text()
  colors = set(color.lower() for color in re.findall(r"#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})", text))
  colors.discard("#00000000")
  return len(colors) > 1

entries = []
missing = []

for key in sorted(icons.keys()):
  entry = icons[key]
  drawable_name = f"icon_{camel_to_snake(key)}"
  drawable_path = drawable_dir / f"{drawable_name}.xml"
  if not drawable_path.exists():
    missing.append(drawable_name)
    continue
  property_name = snake_to_pascal(camel_to_snake(key))
  description = entry.get("description", "").strip()
  is_multicolor = is_multi_color(drawable_path)
  entries.append(
    {
      "property_name": property_name,
      "icon_name": entry.get("name", key),
      "description": description,
      "drawable": drawable_name,
      "multicolor": is_multicolor,
    }
  )

if missing:
  sys.stderr.write(
    "Warning: skipped {} icons with no drawable representation (e.g. {}).\n".format(
      len(missing),
      ", ".join(missing[:5])
    )
  )

if not entries:
  raise SystemExit("No icons were generated; aborting MarketIcons file creation.")

lines = [
  "// Don't edit manually.",
  "// Generated via scripts/update-market-icons.sh using cash-design-system.",
  "",
  "package build.wallet.ui.tokens.market",
  "",
  "import bitkey.ui.framework_public.generated.resources.Res",
  "import bitkey.ui.framework_public.generated.resources.*",
  "",
  "/**",
  " * Collection of [MarketIcon]s generated from cash-design-system.",
  " */",
  "@Suppress(\"LargeClass\")",
  "public object MarketIcons {",
]

for idx, entry in enumerate(entries):
  if idx != 0:
    lines.append("")
  lines.append("  /**")
  lines.append(f"   * The Market icon named '{entry['icon_name']}'.")
  if entry["description"]:
    lines.append("   *")
    for desc_line in entry["description"].splitlines():
      lines.append(f"   * {desc_line}")
  lines.append("   */")
  lines.append(f"  public val {entry['property_name']}: MarketIcon =")
  multicolor = "true" if entry["multicolor"] else "false"
  lines.append(f"    MarketIcon(Res.drawable.{entry['drawable']}, {multicolor})")

lines.append("}")
lines.append("")

output_path.write_text("\n".join(lines))
PY

echo "Market drawables and icons metadata updated from cash-design-system."

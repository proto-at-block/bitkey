import json
import os
from pathlib import Path
from typing import List, Optional

from invoke import task

from bitkey.meson import MesonBuild

from .lib.config import get_defaults
from .lib.paths import *
from .lib.platforms import Platforms


def _set_chip_id(c, chip_id: Optional[str]) -> None:
    if chip_id:
        c.chip_id = chip_id


def _set_sysview(c, sysview: bool) -> None:
    c.enable_sysview = sysview


def _infer_platform(target: str) -> Optional[str]:
    """Infer the platform from a target name using partitions prefixes in platforms.yaml."""
    defaults = get_defaults() or {}
    for platform_name, platform_config in defaults.items():
        partitions = platform_config.get("partitions", "")
        if target.startswith(partitions + "-"):
            return platform_name
    return None


@task(default=True,
      help={
          "platform": "Platform to build (e.g. w1, w3-core, w3-uxc)",
          "target": "Specific target to build (e.g. w3a-core-pdvt-app-a-prod). Requires --platform unless platform can be inferred from the target name.",
          "verbose": "Set to true for more build output",
          "ignore_codegen_cache": "Set to true always re-generate code, ignoring the cache",
          "chip_id": "Optional per-device chip ID (hex)",
          "sysview": "Alias for USE_SYSVIEW=1; enable SEGGER SystemView tracing for this build",
      })
def build(
    c,
    platform: Optional[str] = None,
    target: Optional[str] = None,
    verbose: bool = False,
    ignore_codegen_cache: bool = False,
    chip_id: Optional[str] = None,
    sysview: bool = False,
) -> None:
    """Builds the configured target and platform.

    With --platform only: builds all targets for that platform (same as build.targets).
    With --platform --target: builds just the specified target.
    With --target only: infers platform from target name and builds that target.
    With neither: uses invoke.json defaults.
    """
    _set_chip_id(c, chip_id)
    _set_sysview(c, sysview)

    if platform and target:
        # Explicit platform and target: build just that target.
        m = MesonBuild(c, platform, target=target,
                       ignore_codegen_cache=ignore_codegen_cache)
        m.setup()
        m.build_firmware(False, verbose)
    elif platform:
        # Platform only: build all targets for that platform (same as build.targets).
        defaults = get_defaults() or {}
        if platform not in defaults:
            raise RuntimeError(
                f"Unknown platform: {platform}. Known platforms: {', '.join(defaults.keys())}")
        target = defaults[platform].get("target")
        m = MesonBuild(c, platform, target=target,
                       ignore_codegen_cache=ignore_codegen_cache)
        m.setup()
        m.build_firmware(True, verbose)
    elif target:
        # Target only: infer platform from target name.
        inferred = _infer_platform(target)
        if not inferred:
            raise RuntimeError(
                f"Cannot infer platform from target '{target}'. "
                f"Please pass --platform explicitly."
            )
        m = MesonBuild(c, inferred, target=target,
                       ignore_codegen_cache=ignore_codegen_cache)
        m.setup()
        m.build_firmware(False, verbose)
    else:
        # No overrides: use invoke.json defaults.
        m = MesonBuild(c, ignore_codegen_cache=ignore_codegen_cache)
        m.setup()
        m.build_firmware(False, verbose)


@task(name='targets',
      iterable=["targets"], help={
          "platform": "Platform to build",
          "verbose": "Set to true for more build output",
          "chip_id": "Optional per-device chip ID (hex)",
          "sysview": "Alias for USE_SYSVIEW=1; enable SEGGER SystemView tracing for this build",
      })
def build_all_targets(
    c,
    platform: Optional[str] = None,
    verbose: bool = False,
    chip_id: Optional[str] = None,
    sysview: bool = False,
):
    """Builds firmware targets for the configured or supplied platform"""
    platform = platform or c.platform
    _set_chip_id(c, chip_id)
    _set_sysview(c, sysview)
    target = (get_defaults() or {}).get(platform, {}).get("target", c.target)
    m = MesonBuild(c, platform, target=target)
    m.setup()
    m.build_firmware(True, verbose)


@task(name='platforms', iterable=["platforms"], help={
    "platforms": "List of platforms to build",
    "verbose": "Set to true for more build output",
    "chip_id": "Optional per-device chip ID (hex)",
    "sysview": "Alias for USE_SYSVIEW=1; enable SEGGER SystemView tracing for this build",
})
def build_platforms(
    c,
    platforms: Optional[List[str]] = None,
    verbose: bool = False,
    chip_id: Optional[str] = None,
    sysview: bool = False,
):
    """Builds all firmware platforms and targets"""

    if not platforms:
        platforms = next(os.walk(APPS_DIR))[1]

    # Remove excluded platforms
    platforms = [p for p in platforms if p not in Platforms.EXCLUDED_PLATFORMS]

    for p in platforms:
        build_all_targets(
            c,
            platform=p,
            verbose=verbose,
            chip_id=chip_id,
            sysview=sysview,
        )


@task(name='commands', help={
    "output": "Output path for merged compile_commands.json (default: firmware root)"
})
def compile_commands(c, output: Optional[str] = None):
    """Merge all compile_commands.json files for LSP support."""
    firmware_root = ROOT_DIR
    merged = []
    seen_files = set()

    # Find all compile_commands.json in build directories
    patterns = ["build*/**/compile_commands.json",
                "_build/**/compile_commands.json"]
    for pattern in patterns:
        for cc_file in firmware_root.glob(pattern):
            try:
                with open(cc_file) as f:
                    commands = json.load(f)
                for entry in commands:
                    file_path = entry.get("file")
                    directory = entry.get("directory")
                    if not file_path or not directory:
                        continue
                    # Resolve to absolute path for deduplication across build dirs
                    abs_file = (Path(directory) / file_path).resolve()
                    if abs_file not in seen_files:
                        merged.append(entry)
                        seen_files.add(abs_file)
            except (json.JSONDecodeError, IOError) as e:
                print(f"Warning: Could not read {cc_file}: {e}")

    # Write merged file
    output_path = Path(output) if output else firmware_root / \
        "compile_commands.json"
    with open(output_path, "w") as f:
        json.dump(merged, f, indent=2)
        f.write("\n")

    print(f"Merged {len(merged)} compile commands to {output_path}")


@task(name='clangd')
def generate_clangd(c):
    """Generate .clangd config with ARM toolchain sysroot for LSP support."""
    import subprocess

    # Detect ARM toolchain sysroot
    try:
        result = subprocess.run(
            ["arm-none-eabi-gcc", "-print-sysroot"],
            capture_output=True, text=True, check=True
        )
        arm_sysroot = result.stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("Warning: arm-none-eabi-gcc not found, skipping .clangd generation")
        return

    if not arm_sysroot or not Path(arm_sysroot).is_dir():
        print("Warning: ARM sysroot not found, skipping .clangd generation")
        return

    clangd_config = f"""# Auto-generated by inv build.clangd - do not edit manually
# Regenerate with: inv build.clangd

CompileFlags:
  CompilationDatabase: .
  Add:
    - -isystem{arm_sysroot}/include
  Remove:
    - -fstack-protector-strong
    - -fno-builtin
    - -mno-unaligned-access

Diagnostics:
  UnusedIncludes: None
  MissingIncludes: None
  Suppress:
    - drv_unknown_argument
    - drv_unsupported_opt
"""

    output_path = ROOT_DIR / ".clangd"
    output_path.write_text(clangd_config)
    print(f"Generated {output_path}")

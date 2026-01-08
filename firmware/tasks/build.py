import os
from typing import List, Optional

from invoke import task

from bitkey.meson import MesonBuild

from .lib.config import get_defaults
from .lib.paths import *
from .lib.platforms import Platforms


@task(default=True,
      help={
          "verbose": "Set to true for more build output",
          "ignore_codegen_cache": "Set to true always re-generate code, ignoring the cache",
      })
def build(c, verbose: bool = False, ignore_codegen_cache: bool = False) -> None:
    """Builds the configured target and platform"""

    m = MesonBuild(c, ignore_codegen_cache=ignore_codegen_cache)
    m.setup()
    m.build_firmware(False, verbose)


@task(name='targets',
      iterable=["targets"], help={
          "platform": "Platform to build",
          "verbose": "Set to true for more build output"
      })
def build_all_targets(c, platform: Optional[str] = None, verbose: bool = False):
    """Builds firmware targets for the configured or supplied platform"""

    platform = platform or c.platform
    target = (get_defaults() or {}).get(platform, {}).get("target", c.target)
    m = MesonBuild(c, platform, target=target)
    m.setup()
    m.build_firmware(True, verbose)


@task(name='platforms', iterable=["platforms"], help={
    "platforms": "List of platforms to build",
    "verbose": "Set to true for more build output"
})
def build_platforms(c, platforms: Optional[List[str]] = None, verbose=False):
    """Builds all firmware platforms and targets"""
    if not platforms:
        platforms = next(os.walk(APPS_DIR))[1]

    # Remove excluded platforms
    platforms = [p for p in platforms if p not in Platforms.EXCLUDED_PLATFORMS]

    for p in platforms:
        build_all_targets(c, platform=p, verbose=verbose)

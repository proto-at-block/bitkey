import os

from invoke import task

from bitkey.meson import MesonBuild

from .lib.paths import *
from .lib.platforms import Platforms


@task(default=True,
      help={
          "verbose": "Set to true for more build output",
          "ignore_codegen_cache": "Set to true always re-generate code, ignoring the cache",
      })
def build(c, verbose=False, ignore_codegen_cache=False):
    """Builds the configured target and platform"""

    m = MesonBuild(c, ignore_codegen_cache=ignore_codegen_cache)
    m.setup()
    m.build_firmware(False, verbose)


@task(name='targets',
      iterable=["targets"], help={
          "platform": "Platform to build",
          "verbose": "Set to true for more build output"
      })
def build_all_targets(c, platform=None, verbose=False):
    """Builds firmware targets for the configured or supplied platform"""

    platform = platform or c.platform
    m = MesonBuild(c, platform, BUILD_FW_DIR)
    m.setup()
    m.build_firmware(True, verbose)


@task(name='platforms', iterable=["platforms"], help={
    "platforms": "List of platforms to build",
    "verbose": "Set to true for more build output"
})
def build_platforms(c, platforms, verbose=False):
    """Builds all firmware platforms and targets"""
    if not platforms:
        platforms = next(os.walk(APPS_DIR))[1]

    # Remove excluded platforms
    platforms = [p for p in platforms if p not in Platforms.EXCLUDED_PLATFORMS]

    for p in platforms:
        build_all_targets(c, platform=p, verbose=verbose)

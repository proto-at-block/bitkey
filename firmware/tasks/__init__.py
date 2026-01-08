import glob
import shlex
import click
import os
import subprocess

from invoke import Collection, Config, task

from .lib.paths import *
from .lib.config import get_default_platform, get_defaults, update_config
from bitkey import fw_version

from . import (build, install, generate, fwup, lfs,
               release, test, memfault, status)

from .mcu import (chipinfo, flash, debug, monitor, secinfo)
from .simulator import ui_sim

# This hack is needed for pyinvoke version >=2.1.1
# fmt: off
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))
from bitkey.meson import MesonBuild
# fmt: on


@task(help={
    "subdir": "Clean only the specified sub-directory."
})
def clean(c, subdir=""):
    """Empties the build directory and generated sources"""
    d = BUILD_ROOT_DIR.joinpath(subdir) if subdir else BUILD_ROOT_DIR
    c.run(f"rm -rf {d}")
    for d in GENERATED_CODE_DIRS:
        c.run(f"rm -rf {d}/*")
        c.run(f"rm -rf {d}/.*cache")


@task(help={
    "verbose": "Set to true for more build output",
})
def fuzz(c, verbose=False):
    """Builds fuzz targets"""
    m = MesonBuild(c, "posix", BUILD_HOST_DIR)
    m.setup()
    m.build_fuzzers(verbose)


@task(help={
    "names_only": "Prints only the target names",
})
def targets(c, names_only=False):
    """List available targets"""
    if names_only:
        for target in MesonBuild(c).targets:
            print(target["name"])
    else:
        build_dir = BUILD_FW_DIR
        for subdir in os.listdir(build_dir):
            with c.cd(build_dir.joinpath(subdir)):
                c.run("meson introspect --targets | jq -M", pty=True)


@task
def clang_format(c):
    """Run clang-format on the entire codebase, except for third-party"""
    def g(path): return glob.glob(path, recursive=True)
    files = set(g("./**/*.c") + g("./**/*.h")) \
        - set(g("./third-party/**/*")) \
        - set(g("./venv/**/*")) \
        - set(g("./build/**/*")) \
        - set(g("./lib/ipc/templates/**/*"))
    for file in files:
        subprocess.check_call(shlex.split(f"clang-format -i {file}"))


@task
def codespell(c):
    """Run codespell"""
    ignored_words = ",".join(["sate"])
    with c.cd(ROOT_DIR):
        c.run(f"codespell . -L {ignored_words}")


@task
def bump(c):
    """Bump the local firmware version number for development purposes.
    """
    fw_version.bump()


@task(help={
    "platform": "Platform to analyze (e.g., w1, w3-core, w3-uxc)",
    "target": "Specific target to analyze (defaults to platform's default target)"
})
def puncover(c, platform=None, target=None):
    """Run puncover code size analysis tool"""
    platform = platform or c.platform

    # If platform is specified but target isn't, get the default target for that platform
    if platform and not target:
        defaults = get_defaults()
        if platform in defaults:
            target = defaults[platform].get("target")

    mb = MesonBuild(c, platform=platform, target=target)
    elf_path = mb.target_path(mb.target.elf)

    if not elf_path or not elf_path.exists():
        click.echo(click.style(
            f'Error: ELF file not found for target {target or c.target}. Please build first.', fg='red'))
        return

    click.echo(click.style(
        f'Puncover analyzing {elf_path.name}', fg='cyan'))
    click.echo(click.style(
        'Puncover running on http://127.0.0.1:8000/ (Press CTRL+C to quit)', fg='green'))
    c.run(
        # hide=True, warn=True
        f"puncover --elf_file {elf_path} --src_root {ROOT_DIR} --build_dir {BUILD_ROOT_DIR}")


@task
def version(c, bl=False):
    """Print the currently set firmware version number"""
    if bl:
        click.echo(fw_version.get_bl())
    else:
        click.echo(fw_version.get())


# Check and update config file if needed
update_config()

# root namespace
ns = Collection(clean, test, fuzz, targets, clang_format,
                codespell, bump, puncover, version)

# add single tasks to the root
ns.add_task(chipinfo)
ns.add_task(flash)
ns.add_task(debug)
ns.add_task(monitor)
ns.add_task(secinfo)
ns.add_task(ui_sim)

# add namespaced tasks to the root
ns.add_collection(build)
ns.add_collection(generate, name="gen")
ns.add_collection(install)
ns.add_collection(fwup)
ns.add_collection(lfs, name="fs")
ns.add_collection(test)
ns.add_collection(memfault)
ns.add_collection(status)
ns.add_collection(release)

# Configure every task to act as a shell command (will print colours, allow interactive CLI)
config = Config(
    defaults={
        "run": {"pty": True},
        "platform": get_default_platform(),
        "target": get_defaults()[get_default_platform()]["target"],
    }
)
ns.configure(config)

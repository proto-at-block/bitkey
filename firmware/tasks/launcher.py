"""Emulator launcher daemon build and run tasks"""

from invoke import task

from .lib.paths import FIRMWARE_ROOT_DIR

LAUNCHER_DIR = FIRMWARE_ROOT_DIR / "tools" / "emulator-launcher"
LAUNCHER_BINARY = LAUNCHER_DIR / "target" / "release" / "emulator-launcher"


@task(help={"release": "Build in release mode (default: True)"})
def build_launcher(c, release=True):
    """Build the emulator launcher daemon"""
    mode = "--release" if release else ""
    with c.cd(str(LAUNCHER_DIR)):
        c.run(f"cargo build {mode}", pty=True)


@task(help={"ensure": "Ensure daemon is running (for Gradle)"})
def launcher(c, ensure=False):
    """Run the emulator launcher daemon"""
    if not LAUNCHER_BINARY.exists():
        print("Building emulator launcher...")
        build_launcher(c, release=True)

    args = "--ensure" if ensure else ""
    with c.cd(str(FIRMWARE_ROOT_DIR)):
        c.run(f"{LAUNCHER_BINARY} {args}".strip(), pty=True)


@task
def launcher_ensure(c):
    """Ensure emulator launcher daemon is running (for Gradle Before Launch)"""
    launcher(c, ensure=True)

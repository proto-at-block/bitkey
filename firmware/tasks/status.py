import os
import sys
import click
import distro
import platform

from .lib.paths import CONFIG_FILE

from datetime import datetime
from invoke import task


@task(default=True)
def status(c):
    """Prints local development environment information to aid in debugging"""

    # Context
    t = datetime.utcnow()
    click.echo(f"Current Time (UTC):\t{t}")

    cwd = os.getcwd()
    click.echo(f"Working directory:\t{cwd}")

    # Versions
    if sys.platform == "darwin":
        macos_ver, _, _ = platform.mac_ver()
        click.echo(f"OS version:\t\tMacOS {macos_ver} ({platform.machine()})")
    elif sys.platform == "linux":
        click.echo(
            f"OS version:\t\t{distro.name()} {distro.version(best=True)} ({platform.machine()})")

    hermit = c.run("hermit version", hide='both')
    click.echo(f"Hermit version:\t\t{hermit.stdout.strip()}")

    click.echo(f"Python version:\t\t{sys.version}")
    pip = c.run("pip --version", hide='both')
    click.echo(f"Pip version:\t\t{pip.stdout.strip()}")

    from invoke import _version as inv_version
    click.echo(f"Invoke version:\t\t{inv_version.__version__}")

    # Environment
    if CONFIG_FILE.exists():
        click.echo(f"Invoke Config:\t\tSource:\t\t{CONFIG_FILE}")
        click.echo(f"\t\t\tPlatform:\t{c.platform}")
        click.echo(f"\t\t\tTarget:\t\t{c.target}")
        click.echo(f"\t\t\tMonitor port:\t{c.monitor_port}")
    else:
        click.echo(f"Invoke Config:\t\tNot found")

    # Firmware version
    from bitkey.fw_version import get as get_version
    fw_version = get_version()
    click.echo(f"Firmware Version:\t{fw_version}")

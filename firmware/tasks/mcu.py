import sys
import click
from invoke import task
from pathlib import Path
from typing import Optional

from bitkey.gdb import JLinkGdbServer
from bitkey.meson import (MesonBuild, Target)
from bitkey import fw_version

from .lib.paths import (CONFIG_DIR, COMMANDER_BIN)
from .lib.config import (check_config, get_defaults)
from .lfs import do_backup

GDB_FLASH_CONFIG = CONFIG_DIR.joinpath("gdb_flash.txt")


@task(help={
    "chip": "Name of the target chip"
})
def chipinfo(c, chip: Optional[str] = None):
    """Print the info of the chip attached to the debugger"""
    if "darwin" in sys.platform:
        device = chip or "EFR32MG"
        c.run(f"{COMMANDER_BIN} device info --device={device}")


@task(help={
    "target": "Name of target to flash. Only need this or `path`.",
    "platform": "Target platform",
    "image": "Path to image to flash. Only need this or `target`.",
    "erase": "Erase before flashing",
    "force": "Skip flash erase warnings",
    "no-backup": "Skip backup",
    "jlink": "J-Link serial number to use",
})
def flash(c, target=None, image=None, platform=None, erase=False, force=False, no_backup=False, jlink=None):
    """Flashes the firmware"""
    if not no_backup:
        backup = do_backup(c, None)

    platform = platform or c.platform
    if not target:
        target = get_defaults()[platform]["target"]

    mb = MesonBuild(c, target=target, platform=platform)
    elf = mb.target_path(mb.target.elf)
    chip = mb.platform["jlink_gdb_chip"]

    # Print message if specific J-Link selected
    if jlink:
        click.echo(click.style(f"Using J-Link with S/N: {jlink}", fg='green'))

    if image:
        with JLinkGdbServer(chip, GDB_FLASH_CONFIG, jlink_serial=jlink) as gdb:
            gdb.flash(Path(image))
            return

    if not hasattr(c, 'ignore_local_fw_version') or c.ignore_local_fw_version == False:
        if fw_version.get() == '0.0.0':
            click.echo(click.style(
                "WARNING: local firmware version is set to 0.0.0. This will not boot if the target has been fwup'd.", fg='yellow'))
            click.echo(click.style(
                "  To hide this warning, add the following line to invoke.json\n  \"ignore_local_fw_version\": true", fg='yellow'))

    with JLinkGdbServer(chip, GDB_FLASH_CONFIG, jlink_serial=jlink) as gdb:
        if erase:
            if not force and not click.confirm(click.style("[WARNING] Erasing flash will delete the "
                                                           "fingerprint sensor key, bricking this unit!\nYou must first backup your filesystem. "
                                                           "If you're unsure what to do, do not proceed.", fg='red')):
                return
            gdb.erase(elf)

        # Load the bootloader if it is required for platform targets to run
        if mb.platform['bootloader_required'] == True and target != mb.platform['bootloader_image']:
            loader_target = Target(target).loader(
                mb.platform["bootloader_image"])
            if loader_target:
                bl_elf = mb.target_path(loader_target.elf)
                gdb.flash(bl_elf)
        gdb.flash(elf)


@task(help={
    "target": "Target application to debug with gdb",
    "platform": "Target platform to debug",
    "jlink": "J-Link serial number to use",
})
def debug(c, target=None, platform=None, jlink=None):
    """Debug firmware using gdb"""
    platform = platform or c.platform
    if not target:
        target = get_defaults()[platform]["target"]

    # Print message if specific J-Link selected
    if jlink:
        click.echo(click.style(f"Using J-Link with S/N: {jlink}", fg='green'))

    mb = MesonBuild(c, target=target, platform=platform)
    with JLinkGdbServer(mb.platform["jlink_gdb_chip"], jlink_serial=jlink) as gdb:
        command = gdb.debug_command(mb.target_path(mb.target.elf))
        c.run(' '.join(command), pty=True)


@task(help={
    "port": "Serial port to monitor (overrides config)",
})
def monitor(c, port=None):
    """Monitor serial debug output using screen"""
    if not check_config():
        exit(1)

    # Use provided port or fall back to config
    monitor_port = port or (
        c.monitor_port if hasattr(c, 'monitor_port') else None)

    if monitor_port is None:
        print(
            "'monitor_port' not set in invoke.json config and no --port argument provided")
        exit(1)

    c.run("clear")
    c.run(
        f"python3 -m serial.tools.miniterm --raw --eol CR --encoding ascii {monitor_port} 115200 | tee monitor.log")


@task(help={
    "verbose": "Print security keys and config",
})
def secinfo(c, verbose=False):
    """Print the security status of the chip"""
    if "darwin" in sys.platform:
        chip = MesonBuild(c).platform['jlink_gdb_chip']
        c.run(f"{COMMANDER_BIN} security status --device {chip}")
        if verbose:
            c.run(f"{COMMANDER_BIN} security readkey --sign --device {chip}")
            c.run(f"{COMMANDER_BIN} security readconfig --device {chip}")
    else:
        click.echo(click.style("Error: Unsupported OS", fg='red'))

import sys
import click
from invoke import task
from pathlib import Path

from bitkey.gdb import JLinkGdbServer
from bitkey.meson import (MesonBuild, Target)
from bitkey import fw_version

from .lib.paths import (CONFIG_DIR, COMMANDER_BIN)
from .lib.config import (check_config)
from .lfs import do_backup

GDB_FLASH_CONFIG = CONFIG_DIR.joinpath("gdb_flash.txt")


@task
def chipinfo(c):
    """Print the info of the chip attached to the debugger"""
    if "darwin" in sys.platform:
        c.run(f"{COMMANDER_BIN} device info --device=EFR32MG")


@task(help={
    "target": "Name of target to flash. Only need this or `path`.",
    "path": "Path to file flash. Only need this or `target`.",
    "erase": "Erase before flashing",
    "force": "Skip flash erase warnings",
    "no-backup": "Skip backup",
})
def flash(c, target=None, path=None, erase=False, force=False, no_backup=False):
    """Flashes the firmware"""
    if not no_backup:
        backup = do_backup(c, None)

    if path:
        with JLinkGdbServer("EFR32MG24BXXXF1536", GDB_FLASH_CONFIG) as gdb:
            gdb.flash(Path(path))
            return

    target = target if target else c.target

    mb = MesonBuild(c, target=target)
    elf = mb.target_path(mb.target.elf)

    if not hasattr(c, 'ignore_local_fw_version') or c.ignore_local_fw_version == False:
        if fw_version.get() == '0.0.0':
            click.echo(click.style(
                "WARNING: local firmware version is set to 0.0.0. This will not boot if the target has been fwup'd.", fg='yellow'))
            click.echo(click.style(
                "  To hide this warning, add the following line to invoke.json\n  \"ignore_local_fw_version\": true", fg='yellow'))

    with JLinkGdbServer(mb.platform["jlink_gdb_chip"], GDB_FLASH_CONFIG) as gdb:
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
    "target": "Target to debug with gdb",
})
def debug(c, target=None):
    """Debug firmware using gdb"""
    target = target if target else c.target
    mb = MesonBuild(c, target=target)
    with JLinkGdbServer(mb.platform["jlink_gdb_chip"]) as gdb:
        command = gdb.debug_command(mb.target_path(mb.target.elf))
        c.run(' '.join(command), pty=True)


@task
def monitor(c):
    """Monitor serial debug output using screen"""
    if not check_config():
        exit(1)

    if not hasattr(c, 'monitor_port') or c.monitor_port == None:
        print("'monitor_port' not set in invoke.json config")
        exit(1)

    c.run("clear")
    c.run(
        f"python3 -m serial.tools.miniterm --raw --eol CR --encoding ascii {c.monitor_port} 115200 | tee monitor.log")


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

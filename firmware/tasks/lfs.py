import re
import click
from os import listdir
from invoke import task
from datetime import datetime
from os.path import isfile, join
from pathlib import Path

from bitkey.walletfs import (WalletFS, GDBFs)

from .lib.paths import FS_BACKUPS


def do_backup(c, target, jlink_serial=None, platform=None):
    """Saves the target's filesystem under FS_BACKUPS; returns the path, or None if there was nothing to save."""
    gdbfs = GDBFs(c, target=target, jlink_serial=jlink_serial,
                  platform=platform)
    fs = gdbfs.fetch()
    if fs.fs is None:
        # A full-size image that littlefs cannot mount: blank flash (e.g. an
        # earlier run was killed between erase and restore) or a corrupt one.
        # Report it instead of crashing, so the device can still be flashed and
        # the app formats the filesystem on first boot. A failed read never gets
        # here; fetch() raises on a short dump.
        click.echo(click.style(
            'Filesystem is blank or unmountable; nothing to back up', fg='yellow'))
        click.echo('Resetting target')
        gdbfs.reset()
        return None
    filename = fs.save(FS_BACKUPS)

    click.echo('Resetting target')
    gdbfs.reset()

    click.echo(click.style(
        f'Filesystem saved as {str(filename)}', fg='green'))

    return filename


def do_restore(c, file=None, jlink_serial=None, target=None, platform=None):
    if not file:
        raise click.UsageError("Missing file argument: --file <path>")

    GDBFs(c, target=target, jlink_serial=jlink_serial,
          platform=platform).restore(file)
    click.echo(click.style('Filesystem restored', fg='green'))


@task(help={
    "file_name": "path to backup file",
    "output_dir": "output directory",
})
def cp_from_hardware(c, file_name, output_dir):
    gdbfs = GDBFs(c, c.target)
    fs = gdbfs.fetch()
    contents = fs.read_file(file_name).getbuffer().tobytes()
    target_file = Path(output_dir) / Path(file_name)
    target_file.write_bytes(contents)


@task(help={
    "target": "Build target to backup",
    "platform": "Platform of the MCU to backup (default: configured platform)",
    "jlink": "J-Link serial number to use",
})
def backup(c, target=None, platform=None, jlink=None):
    """Create a local backup of the targets filesystem using gdb"""
    target = target if target else c.target
    do_backup(c, target, jlink_serial=jlink, platform=platform)


@task(help={
    "file": "path to backup file",
    "target": "Build target of the MCU to restore",
    "platform": "Platform of the MCU to restore (default: configured platform)",
    "jlink": "J-Link serial number to use",
})
def restore(c, file=None, target=None, platform=None, jlink=None):
    """Restores a local backup filesystem to the target using gdb"""
    do_restore(c, file, jlink_serial=jlink, target=target, platform=platform)


@task(help={
    "file": "path to backup file",
})
def ls(c, file=None):
    """Restores a local backup filesystem to the target using gdb"""
    fs = WalletFS(file)

    files = fs.ls(".")
    for file in files:
        print(file)


@task
def saved(c):
    backup_files = [f for f in listdir(
        FS_BACKUPS) if isfile(join(FS_BACKUPS, f))]

    # Sort into list of backups per device
    devices = {}
    for f in backup_files:
        serial = f.split("-")[0]
        if serial not in devices:
            devices[serial] = [f]
        else:
            devices[serial].append(f)

    for device, backups in devices.items():
        click.echo(click.style(f'Device: {device}', fg='green'))
        for b in backups:
            match = re.search(r"-(\d+-\d+).", b)
            if not match:
                continue

            timestamp = datetime.strptime(
                match.group(1), WalletFS.TIMESTAMP_FORMAT)
            backupFile = FS_BACKUPS.joinpath(b)

            click.echo(click.style(
                f'  {timestamp}', fg='magenta') + click.style(
                f' - {backupFile}', fg='black'))

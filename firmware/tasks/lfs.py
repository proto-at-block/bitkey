import re
import click
from os import listdir
from invoke import task
from datetime import datetime
from os.path import isfile, join
from pathlib import Path

from bitkey.walletfs import (WalletFS, GDBFs)
from bitkey.meson import MesonBuild

from .lib.paths import (FS_BACKUPS, COMMANDER_BIN)


def do_backup(c, target):
    gdbfs = GDBFs(c, target=target)
    fs = gdbfs.fetch()
    filename = fs.save(FS_BACKUPS)

    click.echo('Resetting target')
    reset_cmd = f"{COMMANDER_BIN} device reset --device={gdbfs.meson.platform['jlink_gdb_chip']}"
    c.run(reset_cmd, hide=True)

    click.echo(click.style(
        f'Filesystem saved as {str(filename)}', fg='green'))

    return filename


def do_restore(c, file=None):
    chip = MesonBuild(c).platform['jlink_gdb_chip']

    # Commander weirdness:
    # The 'flash' command will fail if the target is not halted already
    # The target gets halted after the first try and then the second try succeeds
    # There's no way to halt the device with commander so this hack is done instead
    flash_cmd = f"{COMMANDER_BIN} flash --device={chip} --address={WalletFS.LFS_START:02x} --binary {file}"
    reset_cmd = f"{COMMANDER_BIN} device reset --device={chip}"
    output = c.run(flash_cmd + " --halt", hide=False, warn=True)
    if output.exited != 0:
        output = c.run(flash_cmd, hide=False)
        if output.exited == 0 and 'DONE' in output.stdout:
            c.run(reset_cmd, hide=False)  # Reset the target
            click.echo(click.style('Filesystem restored', fg='green'))
        else:
            click.echo(click.style('Error restoring filesystem', fg='red'))


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
    "target": "Build target to backup"
})
def backup(c, target=None):
    """Create a local backup of the targets filesystem using gdb"""
    target = target if target else c.target
    do_backup(c, target)


@task(help={
    "file": "path to backup file",
})
def restore(c, file=None):
    """Restores a local backup filesystem to the target using gdb"""
    do_restore(c, file)


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

import sys
import json
import glob
import click
import serial
import shutil
import semver
import invoke as inv

from tasks.lib.paths import (CONFIG_FILE, CONFIG_FILE_TASKS)

# This hack is needed for pyinvoke version >=2.1.1
# fmt: off
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent.parent))
from bitkey.meson import Target
# fmt: on


DEFAULTS = {
    "PLATFORM": "w1",
    "TARGET": "w1a-evt-app-a-dev",
}

BAD_INVOKE_VERSIONS = ["2.1.1", "2.1.2"]


def write_config(config):
    with open(CONFIG_FILE, "w") as f:
        f.write(json.dumps(config, indent=4))


def update_config(config):
    # Read the existing config file
    with open(CONFIG_FILE, "r") as f:
        existing_config = json.load(f)
    existing_config.update(config)
    write_config(existing_config)


def move_config():
    if CONFIG_FILE_TASKS.exists():
        shutil.move(CONFIG_FILE_TASKS, CONFIG_FILE)
        click.echo(click.style(
            f"invoke.json file detected in {CONFIG_FILE_TASKS.parent}", fg='yellow'))
        click.echo(click.style(
            f"invoke.json moved to {CONFIG_FILE.parent}", fg='green'))
        click.echo("You can now re-run the command: " +
                   click.style(f"inv {' '.join(sys.argv[1:])}", fg="blue"))
        exit()


def update_config():
    """Updates config files that may use an old format"""

    # Check and exit when bad invoke versions are found
    workaround = inv_workaround_needed()
    if workaround:
        click.echo(click.style(f"invoke version {inv.__version__} is not supported, please update with:",
                               fg="red"))
        click.echo(click.style(f"pip install -r requirements.txt",
                               fg="blue"))
        exit(1)

    # Move config files from directories supported by the bad invoke versions
    move_config()

    # Attempt to correct any config file errors
    remove_invalid_config()
    fix_config_file()


# Checks for invoke version 2.1.1 or 2.1.2 which loaded invoke.json from the tasks/ subdirectory.
# The config file is moved into the correct directory depending on the invoke version.
# TODO: Remove this once majority of users are on invoke >=2.1.3
def inv_workaround_needed() -> bool:
    parsed_version = semver.VersionInfo.parse(inv.__version__)
    return parsed_version in map(semver.VersionInfo.parse, BAD_INVOKE_VERSIONS)


def remove_invalid_config():
    """Removes invalid config files since it will cause a JSON decoding error"""
    config_files = [CONFIG_FILE, CONFIG_FILE_TASKS]
    for config in config_files:
        if config.exists():
            try:
                with open(config, "r") as f:
                    json.load(f)
            except json.JSONDecodeError:
                click.echo(click.style(f"{config} has an invalid format, removing {config}...",
                                       fg="yellow"))
                config.unlink(True)


def fix_config_file():
    """Updates the old versions of config files to the latest format"""
    if CONFIG_FILE.exists():
        with open(CONFIG_FILE, 'r+') as f:
            data = json.load(f)

            # Removes the '.signed' part of target strings
            if 'target' in data and isinstance(data['target'], str):
                data['target'] = data['target'].replace('.signed', '')

                f.seek(0)
                json.dump(data, f, indent=4)
                f.truncate()


def check_config() -> bool:
    if CONFIG_FILE.exists():
        return True

    def prompt(message) -> str:
        click.echo(f'{message} ', nl=False)
        c = click.getchar()
        click.echo(c)
        return c

    if prompt('No invoke.json found, would you like to create one? [Yn]') == 'n':
        return False

    target = Target(DEFAULTS["TARGET"]).elf

    click.echo(click.style('Configuring using these defaults:', fg='green'))
    click.echo('\tPlatform: ' + click.style(DEFAULTS["PLATFORM"], fg='blue'))
    click.echo('\tTarget:   ' + click.style(target, fg='blue'))

    if sys.platform.startswith('darwin'):
        ports = glob.glob('/dev/cu.*')
    else:
        raise EnvironmentError('Unsupported platform')

    click.echo(click.style(
        'Available serial ports:', fg='green'))

    availble_ports = []
    for port in ports:
        try:
            s = serial.Serial(port)
            s.close()
            click.echo(f'\t{len(availble_ports)}: ' +
                       click.style(f'{port}', fg='blue'))
            availble_ports.append(port)
        except (OSError, serial.SerialException):
            pass

    port_idx = int(prompt('Enter the number of the port to use:'))
    if 0 >= port_idx > len(availble_ports):
        click.echo(click.style(
            'Port \'{port_idx}\' is not valid, exiting.', fg='red'))

    config = {
        "platform": DEFAULTS["PLATFORM"],
        "target": str(target),
        "monitor_port": availble_ports[port_idx],
    }

    write_config(config)

    click.echo(click.style(
        'Config file has been created.', fg='green'))
    click.echo("You can now run the command: " +
               click.style(f"inv {' '.join(sys.argv[1:])}", fg="blue"))

    return False

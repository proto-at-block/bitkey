"""fw_version.py

This module provides a way to get and set the firmware version.

The firmware and bootloader versions are optionally set in invoke.json. If not,
the most recent git tag will be used.
"""


from pathlib import Path
from .git import Git
import json
import unittest
import semver
import click

cli = click.Group()

CONFIG_FILE = Path(
    __file__).parent.parent.parent.absolute().joinpath("invoke.json")

_FW_VERSION = "fw_version"
_BL_VERSION = "bl_version"


def _get_semver(field: str) -> semver.VersionInfo:
    try:
        with open(CONFIG_FILE, "r") as f:
            config = json.load(f)
            if field in config:
                return semver.VersionInfo.parse(config[field])
    except FileNotFoundError:
        pass
    return Git().semver_tag


def _set(field: str, new_version: str):
    with open(CONFIG_FILE, "r+") as f:
        config = json.load(f)
        config[field] = new_version
        f.seek(0)
        f.truncate()
        json.dump(config, f, indent=2)


def _bump(field: str):
    with open(CONFIG_FILE, "r+") as f:
        config = json.load(f)
        current = semver.VersionInfo.parse(config[field])
        bumped = current.bump_patch()
        config[field] = str(bumped)
        f.seek(0)
        f.truncate()
        json.dump(config, f, indent=2)


def get() -> str:
    return str(_get_semver(_FW_VERSION))


def get_bl() -> str:
    return str(_get_semver(_BL_VERSION))


def set(new_version: str):
    return _set(_FW_VERSION, new_version)


def bump():
    return _bump(_FW_VERSION)


def metadata(image_type):
    git = Git()

    if image_type == "app":
        version = _get_semver(_FW_VERSION)
    elif image_type == "bl":
        version = _get_semver(_BL_VERSION)
    else:
        raise AssertionError(f"invalid image type {image_type}")

    return {
        "git_id": git.identity,
        "git_branch": git.branch,
        "ver_major": version.major,
        "ver_minor": version.minor,
        "ver_patch": version.patch,
    }


class TestFirmwareVersion(unittest.TestCase):
    def test_local_firmware_version(self):
        get("0.0.1")
        self.assertEqual(get(), "0.0.1")
        bump()
        self.assertEqual(get(), "0.0.2")


@cli.command()
def get_fw_version():
    click.echo(get(), nl=False)


@cli.command()
def get_bl_version():
    click.echo(get_bl(), nl=False)


@cli.command()
def run_tests():
    unittest.main()


if __name__ == "__main__":
    cli()

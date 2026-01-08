from invoke import task
from pathlib import Path
from semver import VersionInfo

from bitkey.wallet import Wallet
from bitkey.comms import WalletComms, NFCTransaction
from bitkey.fwup import FirmwareUpdater
from bitkey.gdb import JLinkGdbServer, gdb_flash_txt
from .lfs import do_backup, do_restore
from bitkey.artifacts import ArtifactStore

import zipfile
import tempfile


def flash_build_zip(build_zip: Path, chip_name: str, bootloader: bool = False) -> None:
    build_dir = tempfile.TemporaryDirectory()
    with zipfile.ZipFile(build_zip, "r") as zip_ref:
        zip_ref.extractall(build_dir.name)

    gdb_flash_cfg = tempfile.NamedTemporaryFile()
    with open(gdb_flash_cfg.name, "w+") as f:
        f.write(gdb_flash_txt)

    application_a = (
        build_dir.name + "/firmware-build/w1/application/w1a-dvt-app-a-dev.signed.elf"
    )
    application_b = (
        build_dir.name + "/firmware-build/w1/application/w1a-dvt-app-b-dev.signed.elf"
    )
    bootloader_path = (
        build_dir.name + "/firmware-build/w1/loader/w1a-dvt-loader-dev.signed.elf"
    )

    # Flash both A and B slots to ensure this version gets picked up. Otherwise, there may
    # be a higher version resident in one of the slots, and the flash will appear to
    # 'not have worked' because the BL picked the higher version.
    with JLinkGdbServer(
        chip_name, gdb_flash_cfg.name, "JLinkGDBServer", "arm-none-eabi-gdb"
    ) as gdb:
        if bootloader:
            if not gdb.flash(Path(bootloader_path)):
                raise Exception("Failed to flash bootloader.")
        if not gdb.flash(Path(application_a)):
            raise Exception("Failed to flash application a.")
        if not gdb.flash(Path(application_b)):
            raise Exception("Failed to flash application b.")


@task(
    default=True,
    help={
        "build-zip": "Flash from the build .zip from Github Releases",
        "version": "Version number to flash, formatted like 1.0.80",
        "fwup": "Firmware update to the lastest version for your device using a USB NFC reader",
        "bootloader": "Update the bootloader too",
        "chip": "Target device chip",
    },
)
def apply(
    c,
    build_zip: str = None,
    version: str = None,
    fwup: str = None,
    bootloader: bool = False,
    chip: str = "EFR32MG24BXXXF1536",
):
    """Apply a firmware release to your device. Must specify ONE OF (build_zip, version, fwup)."""

    # Check if `gh` is installed and that you're logged in.
    if not c.run("gh --version", hide=True).ok:
        raise Exception("gh is not installed. Please `brew install gh`")

    if not c.run("gh auth status", hide=True).ok:
        raise Exception(
            "You're not logged in to Github. Please run `gh auth login`"
        )

    assert (
        sum([bool(build_zip), bool(version), bool(fwup)]) == 1
    ), "Must specify exactly one of build-zip, version, or fwup"

    if build_zip:
        build_zip = Path(build_zip)
    elif version:
        build_zip = ArtifactStore().fetch(VersionInfo.parse(version))

    assert build_zip.exists(), f"File {build_zip} does not exist"

    if build_zip:
        # Backup the filesystem to ensure devices can't be bricked by accidentally
        # deleting the fingerprint sensor MAC key without something to restore to.
        # The key is stored encrypted in flash and paired with a given fingerprint sensor,
        # so if it's lost, that sensor can never be talked to again.
        backup = do_backup(c, None)
        assert backup, "Backup failed"
        flash_build_zip(build_zip, chip, bootloader=bootloader)
    elif fwup:
        wallet = Wallet(WalletComms(NFCTransaction()))
        FirmwareUpdater(wallet).fwup()

"""Wrapper helper for the project's ``invoke`` CLI.

This module provides helper functions for the automation tests to use the
build system commands provided by ``invoke``.
"""

from __future__ import annotations

import logging
import re
import subprocess
from typing import NamedTuple

import allure
import pytest
import sh
from bitkey.walletfs import WalletFS
from bitkey.partition_info import PartitionInfo
from bitkey import fw_version
from bitkey.fwup import get_fwup_order_for_product
from bitkey.wallet import Wallet
from bitkey.gdb import detect_w3_jlinks
from tasks.lib.paths import BUILD_FWUP_BUNDLE_DIR

from .conftest import PlatformConfig

logging.getLogger("sh").setLevel(logging.WARNING)
logger = logging.getLogger(__name__)

# W3 flashes the UXC before the Core.
W3_FLASH_ORDER = {"w3-uxc": 0, "w3-core": 1}


class FwupResult(NamedTuple):
    """Result of a FWUP invocation (single MCU or aggregated multi-MCU).

    :attr succeeded: True iff every ``inv fwup.fwup`` invocation exited 0.
    :attr output: combined stdout (and stderr on failure) from the invocation(s).
    """
    succeeded: bool
    output: str


class Inv:
    """Wrapper class for running ``invoke`` commands."""

    def __init__(self, request: pytest.FixtureRequest, platform_config: PlatformConfig) -> None:
        """Initializes the command instance.

        :param request: PyTest fixture request object for command-line arguments.
        :param platform_config: Platform config instance.
        :returns: ``None``
        """
        self.request: pytest.FixtureRequest = request
        self.platform_config: PlatformConfig = platform_config
        self._jlinks: dict[str, str] | None = None

    @property
    def jlinks(self) -> dict[str, str]:
        """Maps each W3 platform to the J-Link attached to it.

        The mapping is read from the silicon rather than configured per bench,
        so swapping the two probes cannot misroute a flash. Detection runs once
        per session, and only where several probes are in play: a single-MCU
        product, or a run that asks the operator to switch probes by hand, does
        not need `--jlink` at all.

        :returns: mapping of platform name to J-Link serial number.
        """
        if self._jlinks is None:
            switching_by_hand = bool(
                self.request and self.request.config.option.no_multiple_jlinks)
            self._jlinks = (detect_w3_jlinks()
                            if self.platform_config.product == "w3" and not switching_by_hand
                            else {})
        return self._jlinks

    def _jlink_args(self, platform: str | None) -> list[str]:
        """Returns the ``--jlink`` argument for the probe attached to a platform, if known."""
        serial = self.jlinks.get(platform) if platform else None
        return ["--jlink", serial] if serial else []

    @allure.step("Clean")
    def clean(self) -> str:
        """Deletes all build files.

        :returns: output from ``invoke`` for the ``clean`` task.
        """
        if self.request and self.request.config.option.skip_build:
            return "skipped"

        logger.info("Cleaning build files.")
        result: str = sh.inv.clean()
        return result

    @allure.step("Build")
    def build(self) -> str:
        """Builds default firmware for the target MCUs of the device under test.

        :returns: output from ``invoke`` for the ``build`` task.
        """
        if self.request and self.request.config.option.skip_build:
            return "skipped"

        logger.info("Building firmware.")
        result: str = ""
        for name in self.platform_config.chips.keys():
            logger.info(f"Building firmware for {name}")
            result += sh.inv("build.platforms", "-p", f"{name}")
        return result

    @allure.step("Build Platforms")
    def build_platforms(self) -> str:
        """Builds firmware for all platforms.

        :returns: output from ``invoke`` for the ``build.platforms`` task.
        """
        if self.request and self.request.config.option.skip_build:
            return "skipped"

        logger.info("Building firmware for all platforms.")
        result: str = sh.inv("build.platforms")
        return result

    @allure.step("Erase")
    def erase(self, platform: None | str = None, keep: tuple[str, ...] = ()) -> str:
        """Erases a target MCU.

        If ``platform`` is specified, then the specified platform is targetted
        by the erase command; this is important when working with multi-platform
        products.

        :param platform: target platform to erase (default: w1).
        :param keep: partitions to leave intact, e.g. ``("bio_flash",)``.
        :returns: output from the ``invoke`` for the erase task.
        """
        erase_cmd = ["inv", "erase", "-f"]
        if platform:
            erase_cmd += ["-p", platform]
        for partition in keep:
            erase_cmd += ["--keep", partition]
        erase_cmd += self._jlink_args(platform)

        decoded_result: str = ""

        logger.info("Erasing firmware.")
        erase_result: bytes = subprocess.check_output(erase_cmd)
        decoded_result += erase_result.decode("utf-8")

        logger.info(f"{decoded_result}")
        return decoded_result

    @allure.step("Flash")
    def flash_mcu(self, target: None | str = None, platform: None | str = None) -> str:
        """Flashes an image to an MCU.

        If ``target`` is specified then the specified target image is
        programmed tot he MCU. If ``platform`` is specified, then the specified
        platform is targetted by the flashing command; this is important when
        working with multi-platform products.

        :param target: target image to flash to the device.
        :param platform: target platform to flash (default: w1).
        :returns: output from the ``invoke`` for the ``flash`` task.
        """
        flash_cmd = ["inv", "flash", "-f", "--no-backup", "--no-bootloader"]
        if target:
            flash_cmd += ["-t", target]
        if platform:
            flash_cmd += ["-p", platform]
        flash_cmd += self._jlink_args(platform)

        decoded_result: str = ""

        logger.info("Flashing firmware.")
        flash_result: bytes = subprocess.check_output(flash_cmd)
        decoded_result += flash_result.decode("utf-8")

        logger.info(f"{decoded_result}")
        return decoded_result

    @allure.step("Bundle")
    def fwup_bundle(self) -> str:
        """Generates a FWUP bundle for the product under test.

        For single-MCU products (W1), invokes ``fwup.bundle`` once with the
        chip's partition name.  For multi-MCU products (W3), invokes it once
        with the base product name so the bundler collects assets for every
        MCU into a single unified bundle directory.

        :returns: output from the ``fwup.bundle`` task.
        """
        partitions = [
            c.partition for c in self.platform_config.chips.values() if c.partition
        ]

        if len(partitions) > 1:
            # Multi-MCU (W3): single unified bundle call.
            # Partition names follow "{product}-{role}" (e.g. "w3a-core").
            # Extract the base product by stripping the role suffix.
            product = partitions[0].rsplit("-", 1)[0]
            return sh.inv(
                "fwup.bundle",
                "-p",
                product,
                "--platform",
                self.platform_config.product,
                "-i",
                self.platform_config.type,
                "-h",
                self.platform_config.revision,
            )

        # Single-MCU (W1): pass partition directly.
        return sh.inv(
            "fwup.bundle",
            "-p",
            partitions[0],
            "-i",
            self.platform_config.type,
            "-h",
            self.platform_config.revision,
        )

    @allure.step("Fwup MCU: {mcu}")
    def fwup_fwup_mcu(
        self,
        mcu: str,
        bundle_dir: str | None = None,
        deferred: bool = False,
    ) -> FwupResult:
        """Performs a firmware update for a single MCU.

        :param mcu: MCU name to update (e.g. "efr32", "stm32u5").
        :param bundle_dir: optional override for the FWUP bundle directory.
        :param deferred: when True, use deferred-commit mode for atomic updates.
        :returns: :class:`FwupResult` with ``succeeded=False`` when
            ``inv fwup.fwup`` exits non-zero (e.g. ``tasks/fwup.py`` raises
            ``Exit(code=1)``).  Other exceptions (``sh.CommandNotFound``,
            ``KeyboardInterrupt``, etc.) propagate.
        """
        fwup_dir = str(bundle_dir or BUILD_FWUP_BUNDLE_DIR)
        args = ["fwup.fwup", "-f", fwup_dir]

        # For multi-MCU products, pass explicit product and MCU flags.
        # For W1, omit them to preserve existing default behavior
        # (tasks/fwup.py defaults: product="w1", mcu="efr32").
        if self.platform_config.product != "w1":
            args += ["--product", self.platform_config.product, "--mcu", mcu]

        if deferred:
            args += ["--deferred"]

        try:
            return FwupResult(succeeded=True, output=str(sh.inv(*args)))
        except sh.ErrorReturnCode as e:
            return FwupResult(
                succeeded=False,
                output=str(e.stdout or "") + str(e.stderr or ""),
            )

    @allure.step("Fwup")
    def fwup_fwup(
        self,
        bundle_dir: str | None = None,
        deferred: bool = False,
    ) -> FwupResult:
        """Performs a firmware update for all MCUs on the target platform.

        For W1, this updates the single EFR32 MCU. For W3, this updates
        UXC (stm32u5) first, then Core (efr32), which is a firmware
        requirement enforced by the device.  Execution stops at the first
        failing MCU.

        :param bundle_dir: optional override for the FWUP bundle directory.
        :param deferred: when True, use deferred-commit mode for atomic updates.
        :returns: :class:`FwupResult` aggregating per-MCU output.  ``succeeded``
            is False if any executed MCU failed.
        """
        roles = get_fwup_order_for_product(self.platform_config.product)
        # Multi-MCU products require deferred-commit mode for atomic updates
        # without intermediate device resets (fwup_task_port.c clears
        # reset_pending immediately in deferred mode).
        use_deferred = deferred or len(roles) > 1
        parts: list[str] = []
        for role in roles:
            mcu = Wallet.role_to_chip_name(self.platform_config.product, role)
            r = self.fwup_fwup_mcu(
                mcu, bundle_dir=bundle_dir, deferred=use_deferred,
            )
            parts.append(r.output)
            if not r.succeeded:
                return FwupResult(succeeded=False, output="".join(parts))
        return FwupResult(succeeded=True, output="".join(parts))

    @allure.step("Bump version")
    def bump(self) -> None:
        """Increments the firmware version in the ``invoke.json`` file.

        Subsequent firmware builds will use the updated version.

        :returns: ``None``
        """
        fw_version.bump()

    @allure.step("Set local fw version to: {version}")
    def set_version(self, version: str) -> None:
        """Hardcodes the firmware version in the ``invoke.json`` file to the specified version.

        :param version: semantic version string.
        :returns: ``None``
        """
        fw_version.set(version)

    @allure.step("Backup Filesystem")
    def backup_filesystem(self, target: str | None = None,
                          platform: str | None = None) -> str:
        """Saves the file system of the MCU under test for restoration.

        This method is useful when wanting to perserve the filesystem of a
        device under test for restoration after a flashing operation.

        :param target: build target of the MCU to back up (default: configured target).
        :param platform: platform of the MCU (selects its partition table and its provisioned J-Link).
        :returns: output of the ``fs.backup`` command.
        """
        command = ["inv", "fs.backup"]
        if target:
            command += ["-t", target]
        if platform:
            command += ["-p", platform]
        command += self._jlink_args(platform)
        return subprocess.check_output(command, text=True)

    @allure.step("Restore Filesystem")
    def restore_filesystem(self, file: str, target: str | None = None,
                           platform: str | None = None) -> str:
        """Restores the ``littlefs`` filesystem of an MCU under test.

        :param file: path to the saved filesystem binary file.
        :param target: build target of the MCU to restore (default: configured target).
        :param platform: platform of the MCU (selects its partition table and its provisioned J-Link).
        :returns: output of the ``fs.restore`` command.
        """
        command = ["inv", "fs.restore", "--file", file]
        if target:
            command += ["-t", target]
        if platform:
            command += ["-p", platform]
        command += self._jlink_args(platform)
        return subprocess.check_output(command, text=True)

    @allure.step("Backup, Flash, and Recover")
    def flash(self, targets: str | None | list[tuple[str, str]] = None) -> str:
        """Flashes a device under test, preserving the filesystem across flashing.

        :param targets: optional target image to flash or list of ``(platform, target)``.
        :returns: output from invoking all the necessary task commands.
        """
        if self.request and self.request.config.option.skip_flash:
            # User has specified to skip flashing.
            return "skipped"

        platforms_and_targets: list[tuple[None | str, str]] = []
        if isinstance(targets, str):
            platforms_and_targets.append((None, targets))
        elif targets is None:
            platforms_and_targets = list((c.name, c.target)
                                         for c in self.platform_config.chips.values())
        else:
            platforms_and_targets = targets[:]

        if self.platform_config.product == "w3":
            platforms_and_targets.sort(
                key=lambda item: W3_FLASH_ORDER.get(item[0], len(W3_FLASH_ORDER)))

        if not platforms_and_targets:
            raise RuntimeError(f"Nothing to flash.")

        persist_filesystem: bool = not self.request or not self.request.config.option.no_persist_filesystem

        result: str = ""
        for idx, (platform, target) in enumerate(platforms_and_targets):
            if len(platforms_and_targets) > 1 and self.request.config.option.no_multiple_jlinks:
                _ = input(f"Please switch J-Link to {platform} > ")

            fs_backup_file: str = ""
            if persist_filesystem:
                # Backup the filesystem to persist across flashing. A blank or
                # unmountable filesystem (e.g. after an aborted run) saves nothing;
                # the device is still flashed and the app formats it on first boot.
                backup_result: str = self.backup_filesystem(target, platform)
                result += backup_result
                saved = re.search(r"saved as (.*\.bin)", backup_result)
                fs_backup_file = saved.group(1) if saved else ""

            # Persisting the filesystem also means keeping the enrolled fingerprints:
            # `inv erase` wipes every non-bootloader partition, and only the
            # filesystem is backed up and restored, so bio_flash is skipped instead.
            chip = self.platform_config.chips.get(platform) if platform else None
            keep = ()
            if persist_filesystem and chip and chip.partition:
                if "bio_flash" in PartitionInfo(chip.partition).partition_names:
                    keep = ("bio_flash",)

            try:
                result += self.erase(platform=platform, keep=keep)
                result += self.flash_mcu(target=target, platform=platform)
            finally:
                # Restore the filesystem even when flashing fails, so a transient
                # programming error does not leave the device with an erased filesystem.
                if fs_backup_file:
                    # Restore the filesystem without the previous PIN binary (if present).
                    chip = self.platform_config.chips.get(platform) if platform else None
                    partition_info = PartitionInfo(chip.partition) if chip and chip.partition else None
                    fs = WalletFS(fs_backup_file, partition_info=partition_info)
                    if getattr(fs, "fs", None) is not None:
                        fs.remove_file("unlock-secret.bin")
                        fs.sync()
                    result += self.restore_filesystem(fs_backup_file, target, platform)

        return result

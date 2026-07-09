"""Wrapper helper for the project's ``invoke`` CLI.

This module provides helper functions for the automation tests to use the
build system commands provided by ``invoke``.
"""

from __future__ import annotations

import logging
import re
import subprocess
from typing import Any, NamedTuple

import allure
import pytest
import sh
from bitkey.walletfs import WalletFS
from bitkey import fw_version
from bitkey.fwup import get_fwup_order_for_product
from bitkey.wallet import Wallet
from tasks.lib.paths import BUILD_FWUP_BUNDLE_DIR

from .conftest import PlatformConfig

logging.getLogger("sh").setLevel(logging.WARNING)
logger = logging.getLogger(__name__)


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
    def erase(self, platform: None | str = None) -> str:
        """Erases a target MCU.

        If ``platform`` is specified, then the specified platform is targetted
        by the erase command; this is important when working with multi-platform
        products.

        :param platform: target platform to erase (default: w1).
        :returns: output from the ``invoke`` for the erase task.
        """
        erase_cmd: str = "inv erase -f"
        if platform:
            erase_cmd = f"{erase_cmd} -p {platform}"

        decoded_result: str = ""

        logger.info("Erasing firmware.")
        erase_result: bytes = subprocess.check_output(erase_cmd, shell=True)
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
        flash_cmd: str = "inv flash -f --no-backup --no-bootloader"
        if target:
            flash_cmd = f"{flash_cmd} -t {target}"

        if platform:
            flash_cmd = f"{flash_cmd} -p {platform}"

        decoded_result: str = ""

        logger.info("Flashing firmware.")
        flash_result: bytes = subprocess.check_output(flash_cmd, shell=True)
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
    def backup_filesystem(self) -> str:
        """Saves the file system of the MCU under test for restoration.

        This method is useful when wanting to perserve the filesystem of a
        device under test for restoration after a flashing operation.

        :returns: output of the ``fs.backup`` command.
        """
        result: Any = subprocess.check_output(
            "inv fs.backup", shell=True, text=True)
        return result

    @allure.step("Restore Filesystem")
    def restore_filesystem(self, file: str) -> str:
        """Restores the ``littlefs`` filesystem of an MCU under test.

        :param file: path to the saved filesystem binary file.
        :returns: output of the ``fs.restore`` command.
        """
        result = sh.inv("fs.restore", "--file=%s" % file)
        return result

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

        if not platforms_and_targets:
            raise RuntimeError(f"Nothing to flash.")

        persist_filesystem: bool = not self.request or not self.request.config.option.no_persist_filesystem

        result: str = ""
        for idx, (platform, target) in enumerate(platforms_and_targets):
            if len(platforms_and_targets) > 1 and self.request.config.option.no_multiple_jlinks:
                _ = input(f"Please switch J-Link to {platform} > ")

            fs_backup_file: str = ""
            if persist_filesystem:
                # Backup the filesystem to persist across flashing.
                backup_result: str = self.backup_filesystem()
                result += backup_result
                fs_backup_file = re.search(
                    "saved as (.*).bin", backup_result).group(1) + ".bin"

            result += self.erase(platform=platform)
            result += self.flash_mcu(target=target, platform=platform)

            if fs_backup_file:
                # Restore the filesystem without the previous PIN binary (if present).
                fs = WalletFS(fs_backup_file)
                fs.remove_file("unlock-secret.bin")
                result += self.restore_filesystem(fs_backup_file)

        return result

"""Wrapper helper for the project's ``invoke`` CLI.

This module provides helper functions for the automation tests to use the
build system commands provided by ``invoke``.
"""

from __future__ import annotations

import logging
import re
import subprocess
from typing import Any

import allure
import pytest
import sh
from bitkey.walletfs import WalletFS
from bitkey import fw_version
from tasks.lib.paths import BUILD_FWUP_BUNDLE_DIR

from .conftest import PlatformConfig

logging.getLogger("sh").setLevel(logging.WARNING)
logger = logging.getLogger(__name__)


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

        :returns: output from the ``fwup.bundle`` task.
        """
        result: str = ""
        for chip_config in self.platform_config.chips.values():
            partition: str | None = chip_config.partition
            if not partition:
                continue

            result += sh.inv(
                "fwup.bundle",
                "-p",
                partition,
                "-i",
                self.platform_config.type,
                "-h",
                self.platform_config.revision,
            )
        return result

    @allure.step("Fwup")
    def fwup_fwup(self) -> str:
        """Performs a firmware update using the FWUP bundle directory.

        :returns: command output of the ``fwup.fwup`` task.
        """
        # TODO(ESW-20950): Support multiple MCUs for FWUP testing.
        result = sh.inv("fwup.fwup", "-f", str(BUILD_FWUP_BUNDLE_DIR))
        return result

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

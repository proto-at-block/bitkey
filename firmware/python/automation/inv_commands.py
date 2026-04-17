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
from python.automation.conftest import PlatformConfig
from python.bitkey import fw_version
from tasks.lib.paths import BUILD_FWUP_BUNDLE_DIR

logging.getLogger("sh").setLevel(logging.WARNING)


class Inv:
    """Wrapper class for running ``invoke`` commands."""

    @allure.step("Clean")
    def clean(self, request: pytest.FixtureRequest | None = None) -> str:
        """Deletes all build files.

        :param request: PyTest fixture request object for command-line arguments.
        :returns: output from ``invoke`` for the ``clean`` task.
        """
        if request and request.config.option.skip_build:
            return "skipped"

        result: str = sh.inv.clean()
        return result

    @allure.step("Build")
    def build(self, request: pytest.FixtureRequest | None = None) -> str:
        """Builds default firmware for W1.

        :param request: PyTest fixture request object for command-line arguments.
        :returns: output from ``invoke`` for the ``build`` task.
        """
        if request and request.config.option.skip_build:
            return "skipped"

        result: str = sh.inv.build()
        return result

    @allure.step("Build Platforms")
    def build_platforms(self, request: pytest.FixtureRequest | None = None) -> str:
        """Builds firmware for all platforms.

        :param request: PyTest fixture request object for command-line arguments.
        :returns: output from ``invoke`` for the ``build.platforms`` task.
        """
        if request and request.config.option.skip_build:
            return "skipped"

        result: str = sh.inv("build.platforms")
        return result

    @allure.step("Flash -e")
    def flash(self, target: None | str = None, platform: None | str = None) -> str:
        """Flashes an image to an MCU.

        If ``target`` is specified then the specified target image is
        programmed tot he MCU. If ``platform`` is specified, then the specified
        platform is targetted by the flashing command; this is important when
        working with multi-platform products.

        :param target: target image to flash to the device.
        :parma platform: target platform to flash (default: w1).
        :returns: output from the ``invoke`` for the ``flash`` task.
        """
        cmd = "inv flash -e -f"
        if target:
            cmd = f"{cmd} -t {target}"

        if platform:
            cmd = f"{cmd} -p {platform}"

        result: bytes = subprocess.check_output(cmd, shell=True)
        decoded_result: str = result.decode("utf-8")
        print(decoded_result)
        return decoded_result

    @allure.step("Bundle")
    def fwup_bundle(self, platform_config: PlatformConfig) -> str:
        """Generates a FWUP bundle for the product under test.

        :param platform_config: platform configuration for the target under test.
        :returns: output from the ``fwup.bundle`` task.
        """
        result: str = ""
        for chip_config in platform_config.chips.values():
            partition: str | None = chip_config.partition
            if not partition:
                continue

            result += sh.inv(
                "fwup.bundle",
                "-p",
                partition,
                "-i",
                platform_config.type,
                "-h",
                platform_config.revision,
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
        result: Any = subprocess.check_output("inv fs.backup", shell=True, text=True)
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
    def flash_with_filesystem_recovery(
        self,
        target: str | None = None,
        request: pytest.FixtureRequest | None = None,
    ) -> str:
        """Flashes a device under test, preserving the filesystem across flashing.

        :param target: optional target image to flash to the MCU under test.
        :param request: PyTest fixture request object for command-line arguments.
        :returns: output from invoking all the necessary task commands.
        """
        if request and request.config.option.skip_flash:
            # User has specified to skip flashing.
            return "skipped"

        # TODO(ESW-20951): Multi-MCU flashing support.
        platform: str | None = None
        result: str = self.backup_filesystem()
        # re search output string for filename
        fs_backup_file: str = re.search(
            "saved as (.*).bin", result).group(1) + ".bin"
        result += self.flash(target=target, platform=platform)
        fs = WalletFS(fs_backup_file)
        fs.remove_file("unlock-secret.bin")
        result += self.restore_filesystem(fs_backup_file)
        return result

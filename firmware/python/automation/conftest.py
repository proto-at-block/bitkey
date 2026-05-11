"""PyTest text fixtures for automation testing.

This file is used to configure the automation tests with variables.
It also includes globally accessible pytest fixtures.

:file: conftest.py
"""

from __future__ import annotations

import hashlib
import logging
import pytest
import yaml
from typing import Any, Generator, NamedTuple

import wallet_pb2
from bitkey.gdb import GdbCapture, JLinkGdbServer
from bitkey.comms import NFCTransaction, WalletComms
from bitkey.secure_channel import SecureChannel
from bitkey.wallet import Wallet
from tasks.lib.paths import PLATFORM_FILE

from .util import convert_target_app_name

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)


class ChipConfig(NamedTuple):
    # Chip canonical name (e.g. `w1`, `w3-core`).
    name: str

    # Default target to program for this chip (e.g. `w1a-evt-app-a-dev`).
    target: str | None

    # J-Link GDB chip name (e.g. `EFR32MG24BXXXF1536`).
    chip_name: str | None

    # Partition name (e.g. `w3a-core`, `w1a-core`).
    partition: str | None


class PlatformConfig(NamedTuple):
    # Product/platform under test (e.g. `w1`, `w3`).
    product: str

    # Product revision (e.g. `evt`, `dvt`).
    revision: str

    # Build environment (e.g. `dev`, `prod`).
    type: str

    # Mapping of chip canonical names to their configurations.
    chips: dict[str, ChipConfig]


def pytest_addoption(parser: pytest.Parser) -> None:
    """Adds command-line arguments to the PyTest test runner.

    Command line argument values can be accessed via ``request.config.option.<NAME>``.

    :param parser: PyTest parser to add arguments to for the CLI.
    :returns: ``None``
    """
    parser.addoption("-P", "--platform", default="w1", choices=("w1", "w3"),
                     action="store", help="target platform under test")
    parser.addoption("-E", "--environment", default="dev", choices=("dev",),
                     help="firmware build environment")
    parser.addoption("-B", "--build", default="dvt", choices=("dvt", "pdvt", "evt"),
                     help="firmware build configuration")
    parser.addoption("--skip-flash", default=False, action="store_true",
                     help="skip firmware flashing")
    parser.addoption("--skip-build", default=False, action="store_true",
                     help="skip firmware building")
    parser.addoption("--no-persist-filesystem", default=False, action="store_true",
                     help="do not persist filesystem across flashing")
    parser.addoption("--no-multiple-jlinks", default=False, action="store_true",
                     help="for multi MCU flashing, indicates user must manually switch their J-Link")


@pytest.fixture(scope="session")
def platform_config(request: pytest.FixtureRequest) -> Generator[PlatformConfig, None, None]:
    """Yields a fixture specifying the platform configuration for the device
    under test.

    :param request: the PyTest fixture request object.
    :returns: ``PlatformConfig`` instance based on the specified test configuration.
    """
    with open(PLATFORM_FILE, "r") as config_file:
        _config = yaml.safe_load(config_file)

    platform: str = request.config.option.platform
    env: str = request.config.option.environment
    build: str = request.config.option.build
    matching: dict[str, dict[str, Any]] = {
        k: v for k, v in _config.items() if k.startswith(platform)}

    # Generate a mapping of chip names using their canonical names (e.g.
    # `w3-core`) to their configuration.
    chips: dict[str, ChipConfig] = {}
    for canonical_name, config in matching.items():
        chip_name: str | None = config.get("jlink_gdb_chip")
        partition: str | None = config.get("partitions")
        target: str | None = config.get("target")

        canonical_target: str | None
        if target:
            canonical_target = convert_target_app_name(target, env, build)
        else:
            canonical_target = None

        chips[canonical_name] = ChipConfig(
            canonical_name, canonical_target, chip_name, partition)

    # We use `platform` as the product here (e.g. `w3`), which is passed in the
    # configuration. The actual individual platforms that make up a product are
    # available in the partitions.
    yield PlatformConfig(platform, build, env, chips)


@pytest.fixture
def gdb_capture(
    request: pytest.FixtureRequest,
    platform_config: PlatformConfig,
    chip_name: str | None = None,
) -> Generator[None, None, None]:
    """Configures a GDB server session for capturing backtraces on breakpoints.

    :param request: the PyTest fixture request object.
    :param platform_config: Platform specific configuration.
    :param chip_name: optional target chip to debug.
    :returns: ``None``
    """
    mcu_name: str = ""
    if chip_name is None:
        chip_configs: list[ChipConfig] = list(platform_config.chips.values())
        for chip_config in chip_configs:
            if chip_config.chip_name and chip_config.chip_name.lower().startswith("efr32"):
                chip_name = chip_config.chip_name
                mcu_name = chip_config.name
                break
        else:
            if not chip_configs:
                raise RuntimeError(
                    f"No chips found for {platform_config.product=}")

            mcu_name = "w1"
            chip_name = chip_configs[0].chip_name
            logger.warning(
                f"No EFR32 target chip found, defaulting to: {chip_name=}, {mcu_name=}")

    breakpoints = request.node.get_closest_marker("breakpoints")
    with JLinkGdbServer(chip_name) as gdb:
        gdb_capture: GdbCapture = GdbCapture(breakpoints, mcu_name)
        yield
        gdb_capture.get_backtrace()


@pytest.fixture(scope="function")
def wallet(platform_config: PlatformConfig) -> Generator[Wallet, None, None]:
    """Yields an instance of a Wallet device connection.

    :param platform_config: Platform specific configuration.
    :returns: ``Wallet`` instance.
    """
    transport: NFCTransaction = NFCTransaction()
    comms: WalletComms = WalletComms(transport)
    with Wallet(comms=comms, product=platform_config.product) as wallet:
        yield wallet
    comms.close()


@pytest.fixture(scope="function")
def secure_channel(wallet: Wallet, test_pin: str = "itysl") -> Generator[SecureChannel, None, None]:
    """Automatically provisions a secret for use as a device PIN.

    :param wallet: test ``Wallet`` instance.
    :param test_pin: PIN to use for unlock for on-device testing.
    :returns: ``SecureChannel`` instance.
    """
    # Use the development test command to by-pass fingerprint authentication.
    success: bool = wallet.unlock_device()
    assert success, "Failed to unlock device."

    with SecureChannel(wallet) as sc:
        secret_hash = hashlib.sha256(test_pin.encode("ascii")).digest()

        # Provision a new PIN to use for PIN-based authentication.
        logger.info("Provisioning PIN for unlock.")
        provision_cmd = wallet_pb2.wallet_cmd()
        provision_msg = wallet_pb2.provision_unlock_secret_cmd()
        provision_msg.secret.CopyFrom(sc.encrypt(secret_hash))
        provision_cmd.provision_unlock_secret_cmd.CopyFrom(provision_msg)
        logger.info(provision_msg)

        provision_rsp = wallet.comms.transceive(provision_cmd)
        logger.info(provision_rsp)
        assert provision_rsp.status == wallet_pb2.status.SUCCESS, "Failed to provision PIN."

        # Use the new PIN to finish the unlock process.
        logger.info("Validating PIN")
        unlock_cmd = wallet_pb2.wallet_cmd()
        unlock_msg = wallet_pb2.send_unlock_secret_cmd()
        unlock_msg.secret.CopyFrom(sc.encrypt(secret_hash))
        unlock_cmd.send_unlock_secret_cmd.CopyFrom(unlock_msg)
        logger.info(unlock_msg)

        unlock_rsp = wallet.comms.transceive(unlock_cmd)
        logger.info(unlock_rsp)
        assert unlock_rsp.status == wallet_pb2.status.SUCCESS, "Failed to unlock device."
        logger.info("Authentication complete.")

        yield sc

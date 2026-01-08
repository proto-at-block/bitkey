"""
conftest.py
This file is used to configure the automation tests with variables.
It also includes globally accessible pytest fixtures.
"""

import logging
import pytest
import typing
import yaml

from tasks.lib.paths import PLATFORM_FILE

from bitkey.gdb import GdbCapture, JLinkGdbServer
from bitkey.comms import NFCTransaction, WalletComms
from bitkey.wallet import Wallet

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)


class PlatformConfig(typing.NamedTuple):
    platform: str
    product: str
    revision: str
    type: str
    chip: str


def pytest_addoption(parser: pytest.Parser) -> None:
    """Adds command-line arguments to the PyTest test runner.

    Command line argument values can be accessed via ``request.config.option.<NAME>``.

    :param parser: PyTest parser to add arguments to for the CLI.
    :returns: ``None``
    """
    parser.addoption("-p", "--platform", default="w1", action="store",
                     help="target platform under test")
    parser.addoption("-e", "--environment", default="dev", choices=("dev",),
                     help="firmware build environment")
    parser.addoption("-b", "--build", default="dvt", choices=("dvt",),
                     help="firmware build configuration")


@pytest.fixture
def platform_config(request: pytest.FixtureRequest) -> Generator[PlatformConfig, None, None]:
    """Yields a fixture specifying the platform configuration for the device
    under test.

    :param request: the PyTest fixture request object.
    :returns: ``PlatformConfig`` instance based on the specified test configuration.
    """
    with open(PLATFORM_FILE, "r") as config_file:
        _config = yaml.safe_load(config_file)

    platform = request.config.option.platform
    config = _config.get(platform)
    partitions = config.get("partitions")

    product = partitions[0] if isinstance(partitions, list) else partitions
    chip_name = config.get("jlink_gdb_chip")
    env = request.config.option.environment
    build = request.config.option.build

    yield PlatformConfig(platform, product, build, env, chip_name)


@pytest.fixture
def gdb_capture(request, platform_config):
    breakpoints = request.node.get_closest_marker("breakpoints")
    with JLinkGdbServer(platform_config.chip) as gdb:
        gdb_capture = GdbCapture(breakpoints, platform_config.platform)
        yield
        gdb_capture.get_backtrace()


@pytest.fixture
def wallet():
    return Wallet(WalletComms(NFCTransaction()))


@pytest.fixture
def auth_with_pin(wallet):
    logger.info("Authenticating with PIN")
    logger.info(wallet.provision_unlock_secret("foobar"))
    logger.info(wallet.unlock_secret("foobar"))

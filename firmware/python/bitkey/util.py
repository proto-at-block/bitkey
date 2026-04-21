from __future__ import annotations

import importlib.resources
import logging
import os
import platform
import re
from typing import Any

import yaml

logger = logging.getLogger(__name__)


def get_partition_config(product: str) -> dict[str, Any] | None:
    """Retrieves the flash / RAM partition configuration for the specified product.

    The ``product`` names must correspond to names underneath the
    'config/partitions' directory.

    :param product: product to fetch the partition configuration for.
    :returns: ``dict`` on success, otherwise ``None``.
    """
    try:
        try:
            path = importlib.resources.files("bitkey_config").joinpath(
                f"partitions/{product}/partitions.yml"
            )
        except ModuleNotFoundError:
            # If the `bitkey_config` is installed as editable, then the path
            # will not contain the `bitkey_config`.
            path = importlib.resources.files("partitions").joinpath(
                f"{product}/partitions.yml"
            )

        with path.open("rb") as f:
            config = yaml.safe_load(f)
            return config
    except (FileNotFoundError, yaml.YAMLError):
        return None


def bytes_to_size(num_bytes: int) -> str:
    """Converts an integral byte count value to a size string.

    E.g. `196608 -> 192 KB`. Supports at most GB (gigabyte).

    :param num_bytes: number of bytes.
    :returns: size string.
    """
    sizes = ["B", "KB", "MB", "GB"]
    div: int = 1024
    n: int | float = num_bytes

    while sizes and n >= div:
        n = n / div
        _ = sizes.pop(0)

    n_str: str = f"{int(n)}" if isinstance(
        n, int) or n.is_integer() else str(n)
    unit: str = sizes[0] if sizes else "GB"
    return f"{n_str} {unit}"


def size_to_bytes(size: str | int) -> int:
    """Converts a size parameter to its integral value.

    :param size: the size specifier.
    :returns: size in bytes.
    """
    if isinstance(size, int):
        return size

    m = re.search(r"\D", size)
    if m:
        index = m.start()
        integral = int(size[:index])
        modifier = size[index:]
        if modifier.lower() in ["k", "kb"]:
            return integral * 1024
        elif modifier.lower() in ["m", "mb"]:
            return integral * (1024 * 1024)
        raise NotImplementedError(f"Unsupported size modifier: {modifier=}")
    return int(size)


def usb_dev_from_port(port_spec: str) -> tuple[int, int] | None:
    """Retrieves a bus and device number given a physical USB device port
    specification.

    :param port_spec: the physical port specification.
    :returns: tuple of BUS and device number on success, otherwise ``None``.
    """
    if not platform.system().lower().startswith("linux"):
        logger.warning("Only Linux is supported by this API.")
        return None

    # Try to resolve physical port to bus:device.
    sysfs_path = f"/sys/bus/usb/devices/{port_spec}"

    if not os.path.exists(sysfs_path):
        logger.warning(
            f"Path {sysfs_path} does not exist. Check 'ls /sys/bus/usb/devices/' for available devices."
        )
        return None

    try:
        # Read bus number
        with open(os.path.join(sysfs_path, "busnum"), "r") as f:
            bus = int(f.read().strip())

        # Read device number
        with open(os.path.join(sysfs_path, "devnum"), "r") as f:
            dev = int(f.read().strip())

        return bus, dev
    except (IOError, ValueError) as e:
        logger.error(f"Failed to read USB device info from {sysfs_path}: {e}")
        return None

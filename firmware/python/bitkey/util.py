import logging
import os
import platform
import re
from typing import Optional, Tuple, Union

logger = logging.getLogger(__name__)


def size_to_bytes(size: Union[str, int]) -> int:
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


def usb_dev_from_port(port_spec: str) -> Optional[Tuple[int, int]]:
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

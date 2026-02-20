from pathlib import Path
from typing import Optional, Union

import yaml

from . import util


def get_application_partition_size_from_config(config_path: Union[str, Path]) -> int:
    config_path = Path(config_path)

    if not config_path.exists():
        raise FileNotFoundError(f"Partition config file not found: {config_path}")

    with open(config_path, "r") as f:
        config = yaml.safe_load(f)

    partitions = config.get("flash", {}).get("partitions", [])

    for partition in partitions:
        name = partition.get("name", "")
        if name.startswith("application"):
            size_value = partition.get("size")
            if size_value is None:
                raise ValueError(f"Application partition missing 'size' field in {config_path}")
            return util.size_to_bytes(size_value)

    raise ValueError(f"No application partition found in {config_path}")


def get_application_partition_size(product: str, config_dir: Optional[Path] = None) -> int:
    if config_dir is None:
        # Get the directory of this file (firmware/python/bitkey/)
        bitkey_dir = Path(__file__).parent
        # Navigate to firmware/config/partitions
        config_dir = bitkey_dir.parent.parent / "config" / "partitions"

    partition_file = config_dir / product / "partitions.yml"
    return get_application_partition_size_from_config(partition_file)


class PartitionInfo:
    """Provides information about partition layout from partition YAML configs.

    This class parses partition configuration files to determine memory layout
    for firmware partitions, particularly the filesystem location.
    """

    # Default littlefs block size
    DEFAULT_BLOCK_SIZE = 8192

    def __init__(self, partition_name: str, config_dir: Optional[Path] = None):
        """Initialize PartitionInfo for a given partition configuration.

        Args:
            partition_name: Name of the partition config (e.g., "w1a", "w3a-core", "w3a-uxc")
            config_dir: Optional path to the config directory. If None, uses default location.
        """
        self.partition_name = partition_name

        # Default to firmware/config/partitions if not specified
        if config_dir is None:
            # Get the directory of this file (firmware/python/bitkey/)
            bitkey_dir = Path(__file__).parent
            # Navigate to firmware/config/partitions
            config_dir = bitkey_dir.parent.parent / "config" / "partitions"

        self.config_dir = config_dir
        self.partition_file = self.config_dir / partition_name / "partitions.yml"

        if not self.partition_file.exists():
            raise FileNotFoundError(f"Partition config file not found: {self.partition_file}")

        # Load and parse the partition configuration
        with open(self.partition_file, "r") as f:
            self._config = yaml.safe_load(f)

        # Parse the filesystem partition info
        self._parse_filesystem_info()

    def _parse_filesystem_info(self):
        """Parse the partition config to find filesystem location and size."""
        # Validate top-level configuration structure
        if not isinstance(self._config, dict):
            raise ValueError(
                f"Invalid partition config in {self.partition_file}: " f"top-level YAML structure must be a mapping"
            )

        flash = self._config.get("flash")
        if not isinstance(flash, dict):
            raise ValueError(
                f"Invalid partition config in {self.partition_file}: " f"'flash' section is missing or not a mapping"
            )

        flash_origin = flash.get("origin", 0)

        partitions = flash.get("partitions")
        if not isinstance(partitions, list):
            raise ValueError(f"Invalid partition config in {self.partition_file}: " f"'flash.partitions' must be a list")

        # Track the current address as we iterate through partitions
        current_address = flash_origin
        filesystem_partition = None

        for partition in partitions:
            partition_name = partition.get("name", "")

            # Validate size field exists and is non-zero
            size_value = partition.get("size")
            if size_value is None:
                raise ValueError(f"Partition '{partition_name}' in {self.partition_file} " f"is missing required 'size' field")

            partition_size = util.size_to_bytes(size_value)
            if partition_size <= 0:
                raise ValueError(
                    f"Partition '{partition_name}' in {self.partition_file} " f"has non-positive size: {size_value}"
                )

            if partition_name == "filesystem":
                # Found the filesystem partition
                filesystem_partition = partition
                self._filesystem_start = current_address
                self._filesystem_size = partition_size
                break

            # Move to next partition
            current_address += partition_size

        if filesystem_partition is None:
            raise ValueError(f"No 'filesystem' partition found in {self.partition_file}")

    @property
    def filesystem_start_address(self) -> int:
        """Get the start address of the filesystem partition."""
        return self._filesystem_start

    @property
    def filesystem_end_address(self) -> int:
        """Get the end address of the filesystem partition."""
        return self._filesystem_start + self._filesystem_size

    @property
    def filesystem_size(self) -> int:
        """Get the size of the filesystem partition in bytes."""
        return self._filesystem_size

    @property
    def filesystem_block_size(self) -> int:
        """Get the block size for the filesystem (littlefs default: 8192)."""
        return self.DEFAULT_BLOCK_SIZE

    @property
    def filesystem_block_count(self) -> int:
        """Get the number of blocks in the filesystem."""
        if self._filesystem_size % self.filesystem_block_size != 0:
            raise ValueError(
                f"Filesystem size {self._filesystem_size} is not a multiple of " f"block size {self.filesystem_block_size}"
            )
        return self._filesystem_size // self.filesystem_block_size

    def __repr__(self) -> str:
        return (
            f"PartitionInfo(partition_name='{self.partition_name}', "
            f"fs_start=0x{self.filesystem_start_address:08x}, "
            f"fs_size={self._filesystem_size}, "
            f"fs_blocks={self.filesystem_block_count})"
        )

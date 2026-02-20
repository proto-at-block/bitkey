import io
import tempfile
import time
import click

from pathlib import Path

from littlefs import LittleFS

from .meson import MesonBuild
from .gdb import JLinkGdbServer
from .partition_info import PartitionInfo


class WalletFS:
    TIMESTAMP_FORMAT = "%Y%m%d-%H%M%S"
    fs: LittleFS = None

    _ASSY_SERIAL_FILENAME = "assy-serial.txt"

    def __init__(self, path: str, partition_info=None) -> None:
        """Initialize WalletFS from a file.

        Args:
            path: Path to the filesystem binary file
            partition_info: Optional PartitionInfo instance for platform-specific config
        """
        try:
            self.path = path
            # Set instance-specific values from partition_info if provided
            if partition_info:
                self._block_size = partition_info.filesystem_block_size
                self._block_count = partition_info.filesystem_block_count
                self._lfs_start = partition_info.filesystem_start_address
                self._lfs_end = partition_info.filesystem_end_address
            else:
                # Fallback for reading old filesystem backups without platform info
                # These values match w1 historical configuration
                click.echo(click.style(
                    'WARNING: No partition_info provided, using hardcoded defaults. '
                    'This may fail for other platforms.', fg='yellow'))
                self._block_size = 8192
                self._block_count = 24
                self._lfs_start = 0x0800c000
                self._lfs_end = 0x0800c000 + (8192 * 24)
            self.fs = self._mount_from_file()
        except:
            pass

    def save(self, dir: Path) -> Path:
        serial = self.get_serial()
        filename = time.strftime(self.TIMESTAMP_FORMAT)

        if serial is not None:
            filename = f"{serial}-{filename}.bin"
        else:
            filename = f"unknown-{filename}.bin"

        backup_file = dir.joinpath(filename)
        dir.mkdir(parents=True, exist_ok=True)

        with open(backup_file, 'wb') as fh:
            fh.write(self.fs.context.buffer)

        return backup_file

    def get_serial(self) -> str:
        try:
            with self.fs.open(self._ASSY_SERIAL_FILENAME, 'r') as fh:
                data = str(fh.read())
                if data == "":
                    return None
                return data
        except FileNotFoundError as e:
            return None

    def read_file(self, filename: str) -> io.BytesIO:
        try:
            buf = io.BytesIO()
            with self.fs.open(filename, 'rb') as fh:
                buf.write(fh.read())
                buf.flush()
            return buf
        except:
            return None

    def remove_file(self, filename: str):
        try:
            self.fs.remove(filename)
        except:
            pass

    def write_file(self):
        with self.fs.open("foobar.txt", 'w') as fh:
            fh.write("hello world!")
        self.sync()

    def sync(self):
        with open(self.path, 'wb') as fh:
            fh.write(self.fs.context.buffer)

    def ls(self, path: str) -> list[str]:
        return self.fs.listdir(path)

    def _mount_from_file(self) -> LittleFS:
        fs = LittleFS(block_size=self._block_size,
                      block_count=self._block_count, mount=False)
        with open(self.path, 'rb') as fh:
            fs.context.buffer = bytearray(fh.read())
        fs.mount()
        return fs


class GDBFs:
    def __init__(self, ctx, target, jlink_serial=None) -> None:
        self.meson = MesonBuild(ctx, target=target)
        self.jlink_serial = jlink_serial

        # Get partition info for the platform - this is required.
        # All platforms must define a non-empty 'partitions' key in platforms.yaml
        platform = self.meson.platform
        if "partitions" not in platform:
            raise ValueError(
                f"Platform '{self.meson._platform}' does not have a 'partitions' "
                f"entry defined in platforms.yaml - cannot determine filesystem location")

        partition_name = platform["partitions"]
        if not partition_name:
            raise ValueError(
                f"Platform '{self.meson._platform}' has an empty 'partitions' "
                f"entry in platforms.yaml - cannot determine filesystem location")

        self.partition_info = PartitionInfo(partition_name)

    def fetch(self) -> WalletFS:
        fs = None
        lfs_start = self.partition_info.filesystem_start_address
        lfs_end = self.partition_info.filesystem_end_address

        with tempfile.NamedTemporaryFile(mode='wb') as tmp:
            with JLinkGdbServer(self.meson.platform["jlink_gdb_chip"], jlink_serial=self.jlink_serial) as gdb:
                gdb.run_command(
                    self.meson.target.elf, f"dump binary memory {tmp.name} 0x{lfs_start:08x} 0x{lfs_end:08x}")
            fs = WalletFS(tmp.name, partition_info=self.partition_info)

        return fs


if __name__ == "__main__":
    from sys import argv
    assert len(argv) == 4, f"usage: {argv[0]} $fs_path $filename $output_dir"

    filesystem_path = Path(argv[1])
    filename = Path(argv[2])
    output_dir = Path(argv[3])

    fs = WalletFS(filesystem_path)
    contents = fs.read_file(filename.name).getbuffer().tobytes()
    target_file = Path(output_dir / filename)
    target_file.write_bytes(contents)
    print(f"Wrote to {target_file}")

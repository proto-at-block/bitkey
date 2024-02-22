import io
import tempfile
import time
import click

from pathlib import Path

from littlefs import LittleFS

from .meson import MesonBuild
from .gdb import JLinkGdbServer


class WalletFS:
    TIMESTAMP_FORMAT = "%Y%m%d-%H%M%S"
    fs: LittleFS = None

    # TODO: Discover these values automatically
    _BLOCK_SIZE = 8192
    _BLOCK_COUNT = 24
    LFS_START = 0x0800c000
    LFS_END = LFS_START + (_BLOCK_SIZE*_BLOCK_COUNT)

    _ASSY_SERIAL_FILENAME = "assy-serial.txt"

    def __init__(self, path: str) -> None:
        try:
            self.path = path
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
        fs = LittleFS(block_size=self._BLOCK_SIZE,
                      block_count=self._BLOCK_COUNT, mount=False)
        with open(self.path, 'rb') as fh:
            fs.context.buffer = bytearray(fh.read())
        fs.mount()
        return fs


class GDBFs:
    def __init__(self, ctx, target) -> None:
        self.meson = MesonBuild(ctx, target=target)

    def fetch(self):
        fs = None
        with tempfile.NamedTemporaryFile(mode='wb') as tmp:
            with JLinkGdbServer(self.meson.platform["jlink_gdb_chip"]) as gdb:
                gdb.run_command(
                    self.meson.target.elf, f"dump binary memory {tmp.name} 0x{WalletFS.LFS_START:02x} 0x{WalletFS.LFS_END:02x}")
            fs = WalletFS(tmp.name)

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

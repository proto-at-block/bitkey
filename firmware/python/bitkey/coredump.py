import time
from pathlib import Path
from typing import Optional, Tuple

from .wallet import Wallet


def _maybe_write(coredump: bytes, out: Optional[Path] = None):
    if not out:
        return
    out.mkdir(exist_ok=True)
    fname = out / f"coredump-{int(time.time_ns())}.bin"
    with open(fname, "wb") as f:
        f.write(coredump)


def fetch_one(wallet: Wallet, out: Optional[Path] = None, mcu: Optional[str] = None) -> Tuple[int, bytes]:
    """Fetch a coredump and return the remaining number
    of coredumps and the coredump data.
    If an output directory is provided, the coredump will be written there.
    """
    if count(wallet, mcu=mcu) == 0:
        print("No coredumps to fetch")
        return 0, b''

    coredump = []
    offset = 0
    while True:
        result = wallet.coredump(offset, mcu=mcu).coredump_get_rsp
        assert result.rsp_status == result.coredump_get_rsp_status.SUCCESS
        frag = result.coredump_fragment
        offset = frag.offset
        coredump.extend(frag.data)
        if frag.complete:
            _maybe_write(bytes(coredump), out)
            return frag.coredumps_remaining, bytes(coredump)


def fetch_all(wallet: Wallet, out: Optional[Path] = None, mcu: Optional[str] = None) -> list[bytes]:
    """Fetch all coredumps on the device, and write them to the output
    directory if provided."""
    coredumps = []

    remaining = 1000  # Something bigger than the max number of coredumps.
    while remaining != 0:
        remaining, coredump = fetch_one(wallet, mcu=mcu)
        _maybe_write(coredump, out)
        coredumps.append(coredump)

    return coredumps


def count(wallet: Wallet, mcu: Optional[str] = None) -> int:
    """Get the coredump count"""
    return wallet.coredump(operation='COUNT', mcu=mcu).coredump_get_rsp.coredump_count

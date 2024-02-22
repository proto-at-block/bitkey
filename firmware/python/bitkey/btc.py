"""Utility classes and functions for working with Bitcoin transactions.

Don't use these for your own projects, as they aren't necessarily robust.
They're only used for simple testing and development purposes for the Bitkey Python CLI.
"""

import struct

from binascii import unhexlify
from functools import cached_property
from dataclasses import dataclass


class DerivationPath:
    def __init__(self, path: str):
        self.raw_path = path

    @cached_property
    def path(self):
        out = []
        for el in self.raw_path.split('/'):
            if el == "m":
                continue
            if "'" in el:
                # Hardened
                out.append(int(el.removesuffix("'")) + 0x80000000)
            else:
                # Unhardened
                out.append(int(el))
        return out


class Sha256Hash:
    def __init__(self, hex_string: str):
        self.hex_string = hex_string
        assert len(hex_string) == (32*2)

    @property
    def bytes(self):
        return unhexlify(self.hex_string)


@dataclass
class BIP32Key:
    version: bytes
    depth: bytes
    fingerprint: bytes
    child_number: bytes
    chain_code: bytes
    key_data: bytes

    def __init__(self, data_bytes: bytes):
        fields = struct.unpack(">4sB4s4s32s33s", data_bytes)
        (self.version,
         self.depth,
         self.fingerprint,
         self.child_number,
         self.chain_code,
         self.key_data) = fields

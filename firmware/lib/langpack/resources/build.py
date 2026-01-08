"""
Script for generating language packs from input YAML files.

Language packs are TLV encoded strings written to binary. Firmware uses a set
of generated enums to look up strings within the packet.
"""

import ctypes
import os
import re
from typing import Dict, List

import click
import yaml

_LANGPACK_HEADER_FILE = os.path.join(os.path.dirname(__file__), os.pardir, "inc", "langpack_ids.h")

# Constants for langpack format
LANGPACK_VERSION_1 = 1
LANGPACK_TYPE_ASCII = 1
MAX_TLV_LENGTH = 65535  # Maximum value for uint16_t length field


class HdrV1(ctypes.LittleEndianStructure):
    """Version 1 of the Langpack header."""
    _pack_ = 1
    _fields_ = [
        ("version", ctypes.c_uint8),
        ("hdr_len", ctypes.c_uint8),
        ("type", ctypes.c_uint8),
    ]


class TLVEntry(ctypes.LittleEndianStructure):
    """Individual TLV (Tag-Length-Value) entry."""
    _pack_ = 1
    _fields_ = [
        ("tag", ctypes.c_uint32),
        ("length", ctypes.c_uint16),
    ]


@click.command(name="generate")
@click.option("-o", "--output-dir",
              type=click.Path(exists=True, dir_okay=True, file_okay=False),
              default=os.path.dirname(__file__),
              help="output directory for the generated language packs")
@click.argument("ifiles", nargs=-1, type=click.Path("r"), metavar="INPUT_FILE")
def generate(ifiles: List[click.Path], output_dir: click.Path) -> None:
    """Generates a language package binary file given an input file."""
    # Step 1: Read each input file into YAML.
    yamls = {}
    for ifile in ifiles:
        with open(ifile, "r", encoding="utf-8") as f:
            yamls[ifile] = yaml.safe_load(f.read())

    # Step 2: Collect the keys from each input file into a unique set of keys.
    all_keys = set()
    for y in yamls.values():
        all_keys.update(y.keys())

    # Step 3: Validate that all keys are present in each YAML.
    for idx, y in enumerate(yamls.values()):
        missing = all_keys - set(y.keys())
        if missing:
            raise click.ClickException(f"File '{ifiles[idx]}' is missing keys: {missing}")

    # Step 4: Load the key -> ID mapping (Note: ID 0 is reserved for the TLV sentinel).
    key_list = sorted(all_keys)
    key_to_id = {}

    with open(_LANGPACK_HEADER_FILE, "r") as f:
        contents = f.read()
        matches = re.findall(r"LANGPACK_ID_([^\s]+)\s+=\s+([0-9]+)", contents)
        for (key, tag) in matches:
            if tag.startswith("0x"):
                value = int(tag, 16)
            else:
                value = int(tag)

            if any(v == value for v in key_to_id.values()):
                existing_keys = list(k for k, v in key_to_id.items() if v == value)
                raise RuntimeError(f"Duplicate enum value found: {key=}, conflicts: {existing_keys}")
            key_to_id[key] = value

    # Step 5 & 6: For each YAML/file, generate a TLV bytestream and write output file.
    for fpath, y in yamls.items():
        binary_stream = bytearray()
        for key in key_list:
            # Normalize the key for lookup
            split_key = re.sub('([A-Z][a-z]+)', r' \1', re.sub('([A-Z]+)', r' \1', key)).split()
            normalized_key = "_".join(s.upper() for s in split_key)

            # Check if the key exists in the header file
            if normalized_key not in key_to_id:
                raise click.ClickException(
                    f"String key '{key}' (normalized as '{normalized_key}') not found in langpack_ids.h. "
                    f"Please add 'LANGPACK_ID_{normalized_key}' to the header file."
                )

            binary_stream += _encode_tlv_entry(key, str(y[key]), key_to_id)

        binary_stream = _encode_v1(binary_stream)
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)

        fname = os.path.basename(fpath)
        lang = os.path.splitext(fname)[0]

        # Generate the binary packed file.
        ofname = f"{lang}.langpack.bin"
        ofpath = os.path.join(output_dir, ofname)
        with open(ofpath, "wb") as of:
            of.write(binary_stream)

        # Generate the language specific file.
        ofname = f"langpack_{lang}.c"
        ofpath = os.path.join(output_dir, ofname)
        with open(ofpath, "w") as of:
            hex_stream = ", ".join(f"0x{b:02X}" for b in binary_stream)
            of.write(f"""#include "langpack.h"

#include <stddef.h>
#include <stdint.h>

#ifdef LANGPACK_LANG_{lang.upper()}
size_t langpack_data_len = {len(binary_stream)};
uint8_t langpack_data[] = {{{hex_stream}}};

void langpack_load_default(void) {{
  langpack_load(langpack_data, langpack_data_len);
}}
#endif
""")

    click.echo(f"Generated {len(yamls)} language packs in {output_dir}.")


def _encode_v1(data: bytearray) -> bytearray:
    """Encodes TLV data with a V1 header.

    :param data: input byte stream.
    :returns: byte stream prepended with header.
    """

    hdr = HdrV1()
    hdr.version = LANGPACK_VERSION_1
    hdr.hdr_len = ctypes.sizeof(hdr)
    hdr.type = LANGPACK_TYPE_ASCII

    return bytes(hdr) + data


def _encode_tlv_entry(key: str, value: str, key_to_id: Dict[str, int]) -> bytearray:
    """TLV encodes a key-value pair into a bytestream.

    :param key: the lookup key.
    :param value: the value stored in the TLV entry.
    :returns: byte encoded TLV entry.
    """
    # Add a trailing `NULL` terminator to the string.
    value_bytes = value.encode("utf-8") + b"\x00"

    # Validate that the string length fits in a uint16_t
    if len(value_bytes) > MAX_TLV_LENGTH:
        raise click.ClickException(
            f"String '{key}' exceeds maximum length of {MAX_TLV_LENGTH} bytes "
            f"(actual: {len(value_bytes)} bytes including null terminator)"
        )

    # Look-up the tag from the dictionary loaded earlier; keys are
    # normalized for C.
    split_key = re.sub('([A-Z][a-z]+)', r' \1', re.sub('([A-Z]+)', r' \1', key)).split()
    normalized_key = "_".join(s.upper() for s in split_key)
    tag = key_to_id[normalized_key]

    # Add the entry to the binary stream.
    entry = TLVEntry(tag=tag, length=len(value_bytes))
    return bytes(entry) + value_bytes


if __name__ == "__main__":
    generate()

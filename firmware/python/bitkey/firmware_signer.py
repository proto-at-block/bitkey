#!/usr/bin/env python3

from bitkey.metadata import Metadata

import click
import logging
import os
import re
import semver
import shutil
import subprocess
import tempfile
import yaml

from binascii import hexlify
from pathlib import Path
from typing import Optional
from abc import ABC, abstractmethod
from elftools.elf.elffile import ELFFile

from Crypto.PublicKey import ECC
from Crypto.Signature import DSS
from Crypto.Hash import SHA256


class FirmwareSignerException(Exception):
    pass

logging.basicConfig(level=logging.WARN, format="[%(levelname)s] %(message)s ")
logger = logging.getLogger("signer")

cli = click.Group()

IMAGE_TYPES = ["bl", "app", "patch"]
KEY_TYPES = ["dev", "prod", "prod-proto"]
PRODUCTS = ["w1a"]

DEFAULT_KEYS_DIR = os.path.join(os.path.dirname(
    __file__), "..", "..", "config", "keys")

CERT_VERSIONS = {
    "app": 2,
    "bl": 1,
    "patch": 1,
}

# The version number of the signing cert for each environment
# localstack app cert will need to be generated for each user
APP_CERT_ENV_VERSIONS = {
    "localstack": 1,
    "development": 1,
    "staging": 1,
    "production": 1,
}


def semver_to_int(ver: semver.VersionInfo) -> int:
    """Convert semver to u32.
    2 digits for major, 2 for minor, and 3 for patch.
    """
    return int(f"{ver.major:02d}{ver.minor:02d}{ver.patch:03d}")

def get_latest_cert(cert_path: str, product: str, image_type: str, security_level: str, env_type: Optional[str] = None) -> Path:
    '''Get the latest signing cert where latest refers to the version number'''

    if env_type is None:
        fmt_env_type = ""
    else:
        fmt_env_type = "-" + env_type

    cert_path = Path(cert_path)
    cert_basename = f"{product}-{image_type}-signing-cert-{security_level}{fmt_env_type}.*.bin"

    possible_certs = cert_path.glob(cert_basename)

    latest_cert = max(possible_certs, key=lambda s: int(
        re.search(r'\.(\d+)\.', s.name).group(1)))

    return latest_cert


class SigningKeys:
    '''Class used to manage the signing keys for local signing'''

    def __init__(self, keys_dir, product, key_type, image_type):
        directory = self._key_directory(keys_dir, product, key_type)

        version = CERT_VERSIONS[image_type]

        self.public_key_path = os.path.join(
            directory, f"{product}-{image_type}-signing-key-{key_type}.{version}.pub.pem")
        self.private_key_path = os.path.join(
            directory, f"{product}-{image_type}-signing-key-{key_type}.{version}.priv.pem")
        self.cert_path = os.path.join(
            directory, f"{product}-{image_type}-signing-cert-{key_type}.{version}.bin")
        self.patch_signing_key = os.path.join(
            directory, f"{product}-patch-signing-key-{key_type}.{version}.priv.pem")

        self.image_type = image_type
        self.key_type = key_type

    def _key_directory(self, keys_dir, product, key_type):
        directory = os.path.join(
            keys_dir, product.lower() + "-" + key_type.lower())
        assert os.path.isdir(directory), directory
        return directory


class KeyManager(ABC):
    @abstractmethod
    def generate_signature(self, digest: SHA256.SHA256Hash) -> bytes:
        pass

    def verify_signature(self, digest: SHA256.SHA256Hash, signature: bytes):
        pass

    def get_signing_cert_path(self) -> str:
        pass


class LocalKeyManager(KeyManager):
    '''Class used to manage the sigining keys for local signing'''

    def __init__(self, signing_keys: SigningKeys):
        self._keys = signing_keys

    def generate_signature(self, digest: SHA256.SHA256Hash) -> bytes:
        with open(self._keys.private_key_path, 'r') as f:
            signing_key = ECC.import_key(f.read())
        signature = DSS.new(signing_key, 'deterministic-rfc6979').sign(digest)
        return signature

    def verify_signature(self, digest: SHA256.SHA256Hash, signature: bytes):
        with open(self._keys.public_key_path, 'rb') as f:
            verification_key = ECC.import_key(f.read())
        try:
            DSS.new(
                verification_key, 'deterministic-rfc6979').verify(digest, signature)
        except ValueError as e:
            raise e

    def get_signing_cert_path(self) -> str:
        return self._keys.cert_path


class AssetInfo:
    def __init__(self, app_version: str, slot: str, product: str, image_type: str):
        self.app_version = app_version
        self.slot = slot
        self.product = product
        self.image_type = image_type

    def get_app_version(self):
        return self.app_version

    def get_slot(self):
        return self.slot

    def get_product(self):
        return self.product

    def get_image_type(self):
        return self.image_type


class ElfSymbol:
    def __init__(self, symtab, name):
        s = symtab.get_symbol_by_name(name)
        assert s, f"couldn't find symbol for {name}"
        self.sym = s[0]
        assert self.sym and self.sym.name == name

    def raw(self):
        return self.sym

    def addr(self):
        return self.sym['st_value']

    def name(self):
        return self.sym.name

    def size(self):
        return self.sym['st_size']


class Efr32ElfSigner:
    """Codesign a firmware ELF."""

    ECC_P256_SIG_SIZE = 64
    FLASH_ERASED_VALUE = 0xff

    def __init__(self, unsigned_elf_path: Path, partitions_config_path: str):
        self.elf_path = f"{unsigned_elf_path.parent}/{unsigned_elf_path.stem}.signed.elf"
        logging.log(logging.INFO, f"unsigned elf path: {unsigned_elf_path}")
        logging.log(logging.INFO, f"elf path: {self.elf_path}")
        shutil.copyfile(unsigned_elf_path, self.elf_path)
        self.elf_file = open(self.elf_path, 'rb+')
        self.elf = ELFFile(self.elf_file)
        self.symtab = self.elf.get_section_by_name('.symtab')
        self.partitions_config_path = partitions_config_path
        assert self.symtab

    def get_elf_path(self) -> str:
        return self.elf_path

    def _resolve_symbol(self, sym):
        """Find the file offset in the ELF where the symbol's data resides.
        """

        # Based on: https://github.com/eliben/pyelftools/issues/227
        # Find the segment where the symbol is loaded to, as the symbol table points to
        # the loaded address, not the offset in the file
        file_offset = None
        for seg in self.elf.iter_segments():
            if seg.header['p_type'] != 'PT_LOAD':
                continue
            # If the symbol is inside the range of a LOADed segment, calculate the file
            # offset by subtracting the virtual start address and adding the file offset
            # of the loaded section(s)
            if sym.addr() >= seg['p_vaddr'] and sym.addr() < seg['p_vaddr'] + seg['p_filesz']:
                file_offset = sym.addr() - seg['p_vaddr'] + seg['p_offset']
                break
        logger.debug(f"{sym.name()} @ {hex(sym.addr())}")
        assert file_offset, 'Error getting file offset from ELF data'
        return file_offset

    def _image_to_sig_sym_name(self, image_type: str) -> str:
        return f'{image_type}_codesigning_signature'

    def _image_to_sig_section(self, image_type: str, slot: str = None) -> str:
        if image_type == 'bl':
            return f'.{image_type}_codesigning_signature_section'
        else:
            assert slot
            return f'.{image_type}_{slot}_codesigning_signature_section'

    def _read_symbol_data(self, sym_name: str) -> bytes:
        sym = ElfSymbol(self.symtab, sym_name)
        off = self._resolve_symbol(sym)
        self.elf.stream.seek(off)
        size = sym.size()
        data = self.elf.stream.read(size)
        self.elf.stream.seek(0)
        return data

    def _write_symbol_data(self, sym_name: str, new_data: bytes):
        off = self._resolve_symbol(ElfSymbol(self.symtab, sym_name))
        self.elf.stream.seek(off)
        self.elf.stream.write(new_data)
        self.elf.stream.seek(0)

    def _prepare_for_signing(self) -> str:
        with tempfile.NamedTemporaryFile() as unsigned_bin:
            unsigned_bin.name += ".bin"
            subprocess.run(["arm-none-eabi-objcopy", "-O", "binary", self.elf_path, unsigned_bin.name,
                            "--gap-fill", str(hex(self.FLASH_ERASED_VALUE))])
            return unsigned_bin.name

    def _get_application_size(self) -> int:
        with open(self.partitions_config_path, 'r') as f:
            partitions_config = yaml.safe_load(f)

        app_size = None
        for partition in partitions_config['flash']['partitions']:
            if 'application' in partition['name']:
                size = partition['size']
                assert 'K' in size
                size = int(size[:-1])  # Remove trailing K
                size = size * 1024
                if app_size != None:
                    assert size == app_size  # Application partitions should all have the same size
                app_size = size

        return app_size

    def _inject_cert(self, cert: bytes, image_type: str):
        sym_name = f'{image_type}_certificate'
        self._write_symbol_data(sym_name, cert)
        assert self._read_symbol_data(sym_name) == cert

    def _inject_signature(self, sig: bytes, image_type: str):
        sym_name = self._image_to_sig_sym_name(image_type)
        self._write_symbol_data(sym_name, sig)
        assert self._read_symbol_data(sym_name) == sig

    def _set_build_id(self, slot: str):
        meta_bytes = self.elf.get_section_by_name(
            f'.app_{slot}_metadata_section').data()
        digest = Metadata.read_from_bytes(meta_bytes)['hash']
        self._write_symbol_data('g_memfault_sdk_derived_build_id', digest)

        # Per eMemfaultBuildIdType:
        # kMemfaultBuildIdType_MemfaultBuildIdSha1 = 3,
        self._write_symbol_data('g_memfault_build_id', b'\x03')

    def _set_version(self, image_type: str, app_version: str):
        version_int = semver_to_int(semver.VersionInfo.parse(app_version))

        sym_name = 'sl_app_properties'
        props = self._read_symbol_data(sym_name)

        PROPERTIES_MAGIC = [0x13, 0xb7, 0x79, 0xfa,
                            0xc9, 0x25, 0xdd, 0xb7,
                            0xad, 0xf3, 0xcf, 0xe0,
                            0xf1, 0xb6, 0x14, 0xb8]
        assert props[:len(PROPERTIES_MAGIC)] == bytes(PROPERTIES_MAGIC)

        off = len(PROPERTIES_MAGIC)

        # Version number for this struct, NOT for the app.
        struct_version = props[off:off+4]

        # If the struct changes, then this code may also need to change. Catch
        # this with an assertion.
        # Current version is major: 1, minor: 1. The major is shifted left 8.
        assert int.from_bytes(
            struct_version, byteorder='little') == (1 << 8) + (1 << 0)

        props = bytearray(props)
        APP_VERSION_OFFSET = 32
        props[APP_VERSION_OFFSET:APP_VERSION_OFFSET +
              4] = version_int.to_bytes(4, byteorder='little')
        props = bytes(props)

        self._write_symbol_data(sym_name, props)
        assert self._read_symbol_data(sym_name) == props

        if image_type == "app":
            # Also need to set sysinfo version for Memfault reporting
            sym_name = "_sysinfo_version_string"
            version_bytes = bytes(app_version, encoding="ascii")
            asset_version_old = self._read_symbol_data(sym_name)
            self._write_symbol_data(sym_name, version_bytes)
            software_version_max_length = 12
            padded_bytes = version_bytes + \
                (software_version_max_length - len(version_bytes)) * b'\x00'

            asset_version = self._read_symbol_data(sym_name)
            if asset_version != padded_bytes:
                raise FirmwareSignerException(f"Version mismatch! Sign Request: {padded_bytes.decode()}, Asset: {asset_version_old.decode()}")

    def codesign(self, key_manager: KeyManager, asset_info: AssetInfo):
        # Compute the presigned hash.
        digest = self.gen_presign_hash(key_manager, asset_info)

        # Pass the pre-signed artifact up to KMS to sign
        signature = key_manager.generate_signature(digest)

        # Stitch it together and finalize.
        self.stitch_and_finalize(key_manager, asset_info, signature, digest)

    def gen_presign_hash(self, key_manager: KeyManager, asset_info: AssetInfo) -> SHA256.SHA256Hash:
        image_type = asset_info.get_image_type()
        slot = asset_info.get_slot()
        app_version = asset_info.get_app_version()

        if image_type == "app" and not slot:
            raise AssertionError("must set slot when signing app")

        # Inject certificate and set version before signing.
        with open(key_manager.get_signing_cert_path(), 'rb') as f:
            cert = f.read()

        self._inject_cert(cert, image_type)

        if app_version != None:
            self._set_version(image_type, app_version)

        # Update the Memfault build ID.
        if image_type == "app":
            self._set_build_id(slot)

        # Convert the ELF to bin to compute the hash over.
        with open(self._prepare_for_signing(), 'rb') as f:
            signing_input = f.read()

        # Verify that image has not been signed already.
        off = len(signing_input) - self.ECC_P256_SIG_SIZE
        signature_data = signing_input[off:]
        signing_input = signing_input[:off]

        assert signature_data == b'\xca\xfe' * \
            (self.ECC_P256_SIG_SIZE //
                2), f'signature in wrong location, or image was already signed. was {hexlify(signature_data)}'

        # Compute the hash
        digest = SHA256.new(signing_input)
        logger.debug(f"sha256: {digest.hexdigest()}")
        print(f"sha256: {digest.hexdigest()}")
        return digest

    def stitch_and_finalize(self, key_manager: KeyManager, asset_info: AssetInfo, signature: bytes, digest: SHA256.SHA256Hash):
        image_type = asset_info.get_image_type()
        slot = asset_info.get_slot()

        if image_type == "app" and not slot:
            raise AssertionError("must set slot when signing app")

        # Update the ELF with the signature.
        self._inject_signature(signature, image_type)

        logger.debug(f"sig: {hexlify(signature)}")

        # Verify after signing.
        key_manager.verify_signature(digest, signature)

        self.elf_file.close()

        bin_path = Path(self.elf_path).with_suffix(".bin")

        sig_path = Path(self.elf_path).with_suffix(
            "").with_suffix(".detached_signature")

        # Create a signed .bin that we can flash, which excludes
        # everything past the last bit of code, including the signature
        command = ["arm-none-eabi-objcopy", "-O", "binary", str(self.elf_path), bin_path,
                   "--gap-fill", str(hex(self.FLASH_ERASED_VALUE)),
                   "--remove-section", self._image_to_sig_section(
            image_type, slot),
            "--remove-section", ".fill"]

        if image_type == "bl":
            command += ["--remove-section", ".bl_metadata_section"]

        subprocess.run(command)

        # Detach the signature as a separate bin
        subprocess.run(["arm-none-eabi-objcopy", "-O", "binary", self.elf_path, sig_path,
                        "--gap-fill", str(hex(self.FLASH_ERASED_VALUE)),
                        "--only-section", self._image_to_sig_section(image_type, slot)])

        # Detach the metadata as well, for the bootloader. This is necessary because the BL
        # metadata is at the *end*, not the start.
        if image_type == "bl":
            detached_meta = Path(self.elf_path).with_suffix(
                "").with_suffix(".detached_metadata")
            subprocess.run(["arm-none-eabi-objcopy", "-O", "binary", self.elf_path, detached_meta,
                            "--only-section", ".bl_metadata_section"])

            # Pad it out to the nearest multiple of 4.
            meta_size = os.path.getsize(detached_meta)
            with open(detached_meta, 'ab') as f:
                f.write(b'\xff' * (((meta_size + 3) & ~0x03) - meta_size))


class FwupDeltaPatchGenerator:
    @staticmethod
    def create_and_sign(signing_key_pem, from_file, to_file, patch_file):
        """Create and sign a delta patch from from_file to to_file, and write it to patch_file.
        """
        import detools  # keep this local so that firmware signer service does not depend on detools
        signing_key = ECC.import_key(signing_key_pem)

        with tempfile.NamedTemporaryFile() as tmp:
            with open(tmp.name, 'wb') as tmp_file_handle, open(from_file, 'rb') as from_file_handle, open(to_file, 'rb') as to_file_handle:
                detools.create_patch(ffrom=from_file_handle, fto=to_file_handle,
                                     fpatch=tmp_file_handle, compression="heatshrink")

            # Sign the patch
            with open(tmp.name, 'rb') as tmp_file:
                signing_input = tmp_file.read()
            digest = SHA256.new(signing_input)
            logger.debug(f"digest: {digest.hexdigest()}")
            signature = DSS.new(
                signing_key, 'deterministic-rfc6979').sign(digest)
            logger.debug(f"signature: {signature.hex()}")

            # Add the signature and write it to patch_file
            with open(patch_file, 'wb') as f, open(tmp.name, 'rb') as tmp_file:
                f.write(tmp_file.read())
                f.write(signature)


@cli.command(help="Sign a firmware image")
@click.option("--elf", required=True, type=click.Path(exists=True, path_type=Path), help="ELF to sign")
@click.option("--product", required=True, type=click.Choice(PRODUCTS), help="Which product to sign for")
@click.option("--key-type", required=True,
              type=click.Choice(KEY_TYPES,
                                case_sensitive=False), help="Development or production keys")
@click.option("--image-type", required=True,
              type=click.Choice(IMAGE_TYPES,
                                case_sensitive=False), help="Bootloader or application")
@click.option("--slot", required=False, type=click.Choice(["a", "b"], case_sensitive=False), help="Application slot")
@click.option("--app-version", required=True, type=click.STRING, help="Application version")
@click.option("--partitions-config", required=True, type=click.STRING, help="Path to partitions.yml")
@click.option("--keys-dir", required=True, type=click.Path(exists=True, path_type=Path), help="Path to keys directory")
@click.option("--verbose", required=False, type=click.BOOL, help="Enable logging")
def sign(elf, product, key_type, image_type, slot, app_version, partitions_config, keys_dir, verbose):
    if verbose:
        logger.setLevel(logging.DEBUG)

    signer = Efr32ElfSigner(elf, partitions_config)
    signing_keys = SigningKeys(keys_dir, product, key_type, image_type)
    signer.codesign(LocalKeyManager(signing_keys),
                    AssetInfo(app_version, slot, product, image_type))


@cli.command(help="Generate and sign a delta patch")
@click.option("--key-file", required=True, type=click.Path(exists=True, path_type=Path), help="Path to key")
@click.option("--from-file", required=True, type=click.Path(exists=True, path_type=Path), help="Path to old firmware")
@click.option("--to-file", required=True, type=click.Path(exists=True, path_type=Path), help="Path to new firmware")
@click.option("--patch", required=True, type=click.Path(exists=False, path_type=Path), help="Path to output patch")
@click.option("--verbose", required=False, type=click.BOOL, help="Enable logging", is_flag=True)
def create_patch(key_file, from_file, to_file, patch, verbose):
    if verbose:
        logger.setLevel(logging.DEBUG)
    with open(key_file, 'r') as f:
        key_pem = f.read()
    FwupDeltaPatchGenerator().create_and_sign(key_pem, from_file, to_file, patch)


if __name__ == "__main__":
    cli()

#!/usr/bin/env python3

import logging
import tempfile
from pathlib import Path

import click
import semver
from bitkey.elf_signer import ElfSigner
from bitkey.fwa.bitkey_fwa.constants import PRODUCT_W3A_UXC, PRODUCTS
from bitkey.key_manager import LocalKeyManager, PicocertKeyManager, SigningKeys
from bitkey.metadata import Metadata
from bitkey.signer_utils import IMAGE_TYPES, KEY_TYPES, AssetInfo, FirmwareSignerException, semver_to_int
from bitkey.stm32u5_signer import Stm32U5ElfSigner
from Crypto.Hash import SHA256
from Crypto.PublicKey import ECC
from Crypto.Signature import DSS

logging.basicConfig(level=logging.WARN, format="[%(levelname)s] %(message)s ")
logger = logging.getLogger("signer")

cli = click.Group()

EFR32_PROPERTIES_MAGIC = [0x13, 0xB7, 0x79, 0xFA, 0xC9, 0x25, 0xDD, 0xB7, 0xAD, 0xF3, 0xCF, 0xE0, 0xF1, 0xB6, 0x14, 0xB8]


class Efr32ElfSigner(ElfSigner):
    """Codesign a firmware ELF."""

    ECC_P256_SIG_SIZE = 64
    FLASH_ERASED_VALUE = 0xFF

    def __init__(self, unsigned_elf_path: Path, partitions_config_path: str):
        super().__init__(unsigned_elf_path, partitions_config_path)

    def _set_build_id(self, slot: str):
        meta_bytes = self.elf.get_section_by_name(f".app_{slot}_metadata_section").data()
        digest = Metadata.read_from_bytes(meta_bytes)["hash"]
        self._write_symbol_data("g_memfault_sdk_derived_build_id", digest)

        # Per eMemfaultBuildIdType:
        # kMemfaultBuildIdType_MemfaultBuildIdSha1 = 3,
        self._write_symbol_data("g_memfault_build_id", b"\x03")

    def _set_version(self, image_type: str, app_version: str):
        """Set version in EFR32 sl_app_properties structure."""
        version_int = semver_to_int(semver.VersionInfo.parse(app_version))

        sym_name = "sl_app_properties"
        props = self._read_symbol_data(sym_name)

        assert props[: len(EFR32_PROPERTIES_MAGIC)] == bytes(EFR32_PROPERTIES_MAGIC)

        off = len(EFR32_PROPERTIES_MAGIC)

        # Version number for this struct, NOT for the app.
        struct_version = props[off : off + 4]

        # If the struct changes, then this code may also need to change. Catch
        # this with an assertion.
        # Current version is major: 1, minor: 1. The major is shifted left 8.
        assert int.from_bytes(struct_version, byteorder="little") == (1 << 8) + (1 << 0)

        props = bytearray(props)
        APP_VERSION_OFFSET = 32
        props[APP_VERSION_OFFSET : APP_VERSION_OFFSET + 4] = version_int.to_bytes(4, byteorder="little")
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
            padded_bytes = version_bytes + (software_version_max_length - len(version_bytes)) * b"\x00"

            asset_version = self._read_symbol_data(sym_name)
            if asset_version != padded_bytes:
                raise FirmwareSignerException(
                    f"Version mismatch! Sign Request: {padded_bytes.decode()}, Asset: {asset_version_old.decode()}"
                )


class FwupDeltaPatchGenerator:
    @staticmethod
    def create_and_sign(signing_key_pem, from_file, to_file, patch_file):
        """Create and sign a delta patch from from_file to to_file, and write it to patch_file."""
        import detools  # keep this local so that firmware signer service does not depend on detools

        signing_key = ECC.import_key(signing_key_pem)

        with tempfile.NamedTemporaryFile() as tmp:
            with open(tmp.name, "wb") as tmp_file_handle, open(from_file, "rb") as from_file_handle, open(
                to_file, "rb"
            ) as to_file_handle:
                detools.create_patch(
                    ffrom=from_file_handle, fto=to_file_handle, fpatch=tmp_file_handle, compression="heatshrink"
                )

            # Sign the patch
            with open(tmp.name, "rb") as tmp_file:
                signing_input = tmp_file.read()
            digest = SHA256.new(signing_input)
            logger.debug(f"digest: {digest.hexdigest()}")
            signature = DSS.new(signing_key, "deterministic-rfc6979").sign(digest)
            logger.debug(f"signature: {signature.hex()}")

            # Add the signature and write it to patch_file
            with open(patch_file, "wb") as f, open(tmp.name, "rb") as tmp_file:
                f.write(tmp_file.read())
                f.write(signature)


@cli.command(help="Sign a firmware image")
@click.option("--elf", required=True, type=click.Path(exists=True, path_type=Path), help="ELF to sign")
@click.option("--product", required=True, type=click.Choice(PRODUCTS), help="Which product to sign for")
@click.option(
    "--key-type", required=True, type=click.Choice(KEY_TYPES, case_sensitive=False), help="Development or production keys"
)
@click.option(
    "--image-type", required=True, type=click.Choice(IMAGE_TYPES, case_sensitive=False), help="Bootloader or application"
)
@click.option("--slot", required=False, type=click.Choice(["a", "b"], case_sensitive=False), help="Application slot")
@click.option("--app-version", required=True, type=click.STRING, help="Application version")
@click.option("--partitions-config", required=True, type=click.STRING, help="Path to partitions.yml")
@click.option("--keys-dir", required=True, type=click.Path(exists=True, path_type=Path), help="Path to keys directory")
@click.option("--verbose", required=False, type=click.BOOL, help="Enable logging")
def sign(elf, product, key_type, image_type, slot, app_version, partitions_config, keys_dir, verbose):
    if verbose:
        logger.setLevel(logging.DEBUG)

    asset_info = AssetInfo(app_version, slot, product, image_type)
    signing_keys = SigningKeys(keys_dir, product, key_type, image_type)

    # Select the appropriate signer and key manager based on product
    if product == PRODUCT_W3A_UXC:
        signer = Stm32U5ElfSigner(elf, partitions_config)
        key_manager = PicocertKeyManager(signing_keys)
    else:
        # EFR32 platforms (w1a, w3a-core)
        signer = Efr32ElfSigner(elf, partitions_config)
        key_manager = LocalKeyManager(signing_keys)

    signer.codesign(key_manager, asset_info)


@cli.command(help="Verify a signed firmware image")
@click.option("--elf", required=True, type=click.Path(exists=True, path_type=Path), help="Signed ELF to verify")
@click.option("--product", required=True, type=click.Choice(PRODUCTS), help="Which product to verify")
@click.option(
    "--key-type",
    required=False,
    default="dev",
    type=click.Choice(KEY_TYPES, case_sensitive=False),
    help="Development or production keys (default: dev)",
)
@click.option(
    "--image-type", required=True, type=click.Choice(IMAGE_TYPES, case_sensitive=False), help="Bootloader or application"
)
@click.option(
    "--partitions-config", required=False, type=click.STRING, help="Path to partitions.yml (optional for verification)"
)
@click.option(
    "--keys-dir", required=False, default=None, type=click.Path(exists=True, path_type=Path), help="Path to keys directory"
)
@click.option("--verbose", required=False, type=click.BOOL, help="Enable logging")
def verify(elf, product, key_type, image_type, partitions_config, keys_dir, verbose):
    """Verify the signature of a signed firmware ELF."""
    if verbose:
        logger.setLevel(logging.DEBUG)

    # Default to dev keys if not specified
    if keys_dir is None:
        keys_dir = Path(__file__).parent.parent.parent / "config" / "keys"
        logger.debug(f"Using default keys directory: {keys_dir}")

    # For verification, we don't need app_version or slot (symbol is slot-agnostic)
    asset_info = AssetInfo(app_version=None, slot=None, product=product, image_type=image_type)
    signing_keys = SigningKeys(keys_dir, product, key_type, image_type)

    # Select the appropriate signer and key manager based on product
    if product == PRODUCT_W3A_UXC:
        signer = Stm32U5ElfSigner(elf, partitions_config)
        key_manager = PicocertKeyManager(signing_keys)
    else:
        # EFR32 platforms (w1a, w3a-core)
        signer = Efr32ElfSigner(elf, partitions_config)
        key_manager = LocalKeyManager(signing_keys)

    # Read the signature from the ELF
    signature_symbol = signer._image_to_sig_sym_name(image_type)
    signature = signer._read_symbol_data(signature_symbol)
    generated_hash = signer.gen_hash()

    # Read version from binary if it's an app image
    version_in_binary = None
    if image_type == "app":
        try:
            version_bytes = signer._read_symbol_data("_sysinfo_version_string")
            version_in_binary = version_bytes.rstrip(b"\x00").decode("ascii")
        except Exception as e:
            logger.debug(f"Could not read version from binary: {e}")

    # Verify the signature
    click.echo(f"\n{'='*80}")
    click.echo(f"Verifying signature for: {elf.name}")
    click.echo(f"Product: {product}, Image Type: {image_type}")
    click.echo(f"{'='*80}\n")
    if version_in_binary:
        click.echo(f"App Version: {version_in_binary}")
    click.echo(f"Signature Symbol: {signature_symbol}")
    click.echo("Signature (64 bytes):")
    click.echo(f"  {signature.hex()}\n")
    click.echo("Generated Hash (SHA-256):")
    click.echo(f"  {generated_hash.hexdigest()}\n")
    # Perform verification
    is_valid = signer.verify_signature(key_manager, asset_info)
    if is_valid:
        click.echo("Signature verification: PASSED")
    else:
        click.echo("Signature verification: FAILED")
    click.echo(f"Certificate: {Path(signing_keys.cert_path).name}")

    click.echo("\n" + "=" * 80 + "\n")

    # Return exit code based on verification result
    if not is_valid:
        raise click.Abort()


@cli.command(help="Generate and sign a delta patch")
@click.option("--key-file", required=True, type=click.Path(exists=True, path_type=Path), help="Path to key")
@click.option("--from-file", required=True, type=click.Path(exists=True, path_type=Path), help="Path to old firmware")
@click.option("--to-file", required=True, type=click.Path(exists=True, path_type=Path), help="Path to new firmware")
@click.option("--patch", required=True, type=click.Path(exists=False, path_type=Path), help="Path to output patch")
@click.option("--verbose", required=False, type=click.BOOL, help="Enable logging", is_flag=True)
def create_patch(key_file, from_file, to_file, patch, verbose):
    if verbose:
        logger.setLevel(logging.DEBUG)
    with open(key_file, "r") as f:
        key_pem = f.read()
    FwupDeltaPatchGenerator().create_and_sign(key_pem, from_file, to_file, patch)


if __name__ == "__main__":
    cli()

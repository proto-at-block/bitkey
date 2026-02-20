#!/usr/bin/env python3

import logging
import tempfile
from pathlib import Path
from typing import Optional, Tuple

import click
import semver
from bitkey.elf_signer import ElfSigner
from bitkey.fwa.bitkey_fwa.constants import PRODUCT_W3A_UXC, PRODUCTS
from bitkey.key_manager import KeyManager, LocalKeyManager, PatchSigningKeys, PicocertKeyManager, SigningKeys
from bitkey.metadata import Metadata
from bitkey.partition_info import get_application_partition_size
from bitkey.signer_utils import IMAGE_TYPES, KEY_TYPES, AssetInfo, FirmwareSignerException, semver_to_int
from bitkey.stm32u5_signer import Stm32U5ElfSigner
from Crypto.Hash import SHA256
from Crypto.PublicKey import ECC
from Crypto.Signature import DSS

logging.basicConfig(level=logging.WARN, format="[%(levelname)s] %(message)s ")
logger = logging.getLogger("signer")

cli = click.Group()

EFR32_PROPERTIES_MAGIC = [0x13, 0xB7, 0x79, 0xFA, 0xC9, 0x25, 0xDD, 0xB7, 0xAD, 0xF3, 0xCF, 0xE0, 0xF1, 0xB6, 0x14, 0xB8]

ECC_P256_SIG_SIZE = 64


def verify_patch_signature(patch_path: Path, key_manager: KeyManager) -> Tuple[bool, Optional[str]]:
    """Verify the signature of a delta patch file. Returns (success, error_message)."""
    with open(patch_path, "rb") as f:
        patch_data = f.read()

    if len(patch_data) < ECC_P256_SIG_SIZE:
        raise ValueError(f"Patch file too small: {len(patch_data)} bytes (minimum {ECC_P256_SIG_SIZE})")

    patch_content = patch_data[:-ECC_P256_SIG_SIZE]
    patch_signature = patch_data[-ECC_P256_SIG_SIZE:]
    digest = SHA256.new(patch_content)

    try:
        key_manager.verify_signature(digest, patch_signature)
        return True, None
    except ValueError as e:
        return False, str(e)


def apply_patch(from_signed_bin: Path, patch_path: Path, output_path: Path) -> Tuple[bool, Optional[str]]:
    """Apply a delta patch to a .signed.bin file. Returns (success, error_message).

    Transforms: from.signed.bin + patch -> to.signed.bin

    Note: .signed.bin files are the flashable firmware binaries produced by the signing process.
    They do NOT contain the signature (signatures are in separate .detached_signature files).
    """
    # detools is only available in dev environments, not the firmware signer service
    import detools

    with open(patch_path, "rb") as f:
        patch_data = f.read()

    if len(patch_data) < ECC_P256_SIG_SIZE:
        raise ValueError(f"Patch file too small: {len(patch_data)} bytes (minimum {ECC_P256_SIG_SIZE})")

    patch_content = patch_data[:-ECC_P256_SIG_SIZE]

    try:
        with tempfile.NamedTemporaryFile(delete=False) as tmp_patch:
            tmp_patch.write(patch_content)
            tmp_patch.flush()
            tmp_patch_path = Path(tmp_patch.name)

        try:
            with open(from_signed_bin, "rb") as from_file, open(output_path, "wb") as to_file, open(
                tmp_patch_path, "rb"
            ) as pf:
                detools.apply_patch(ffrom=from_file, fpatch=pf, fto=to_file)
            return True, None
        finally:
            tmp_patch_path.unlink(missing_ok=True)
    except Exception as e:
        return False, str(e)


def verify_firmware_signature_with_padding(
    signed_bin_data: bytes,
    signature: bytes,
    padded_size: int,
    key_manager: KeyManager,
) -> Tuple[bool, Optional[str]]:
    """Verify firmware signature by padding binary to flash slot size.

    This simulates what the bootloader does:
    - Bootloader reads from flash, which has 0xFF in erased areas
    - Hash is computed over padded binary (minus last 64 bytes for signature area)
    - This matches how the signature was originally computed during signing

    Args:
        signed_bin_data: The .signed.bin file contents
        signature: The 64-byte detached signature
        padded_size: Total flash slot size (binary will be padded with 0xFF to this size)
        key_manager: KeyManager for signature verification
    """
    if len(signature) != ECC_P256_SIG_SIZE:
        return False, f"Invalid signature size: {len(signature)} bytes (expected {ECC_P256_SIG_SIZE})"

    # Pad binary with 0xFF to flash slot size (simulating erased flash)
    if len(signed_bin_data) > padded_size - ECC_P256_SIG_SIZE:
        return False, f"Binary too large for flash slot: {len(signed_bin_data)} > {padded_size - ECC_P256_SIG_SIZE}"

    # Pad to (padded_size - signature_size), then hash
    padding_needed = (padded_size - ECC_P256_SIG_SIZE) - len(signed_bin_data)
    padded_data = signed_bin_data + (b"\xff" * padding_needed)

    digest = SHA256.new(padded_data)

    try:
        key_manager.verify_signature(digest, signature)
        return True, None
    except ValueError as e:
        return False, str(e)


def verify_delta_update(
    patch_path: Path,
    from_signed_bin: Path,
    to_detached_signature: Path,
    patch_key_manager: KeyManager,
    firmware_key_manager: KeyManager,
    flash_slot_size: int,
    to_signed_bin: Optional[Path] = None,
) -> Tuple[bool, Optional[str]]:
    """Verify a complete delta update. Returns (success, error_message).

    Transforms and verifies: from.signed.bin + patch -> patched output
    Then reconstructs what bootloader sees: patched output + 0xFF fill + signature
    And verifies the signature matches.

    Verification steps:
    1. Verify patch signature (proves patch is authentic)
    2. Apply patch: from.signed.bin + patch -> patched output
    3. Verify patched output's signature (simulates bootloader):
       - Pad patched output with 0xFF to (flash_slot_size - 64)
       - Verify hash against detached signature
    4. (Optional) Compare to expected .signed.bin for additional validation

    This simulates what the bootloader does:
    - Flash contains: [firmware binary][0xFF fill][signature]
    - Bootloader hashes [firmware binary][0xFF fill] and verifies against [signature]

    Args:
        patch_path: Path to the signed delta patch file (.signed.patch)
        from_signed_bin: Path to source firmware (.signed.bin)
        to_detached_signature: Path to target firmware's .detached_signature
        patch_key_manager: KeyManager for verifying patch signature
        firmware_key_manager: KeyManager for verifying firmware signature
        flash_slot_size: Flash slot size in bytes (e.g., 896KB for w3a-uxc)
        to_signed_bin: Optional path to expected target .signed.bin for comparison
    """
    # Step 1: Verify patch signature
    success, error = verify_patch_signature(patch_path, patch_key_manager)
    if not success:
        return False, f"Patch signature verification failed: {error}"

    # Step 2: Apply patch (from.signed.bin + patch -> patched output)
    with tempfile.NamedTemporaryFile(delete=False, suffix=".signed.bin") as tmp:
        output_path = Path(tmp.name)

    success, error = apply_patch(from_signed_bin, patch_path, output_path)
    if not success:
        output_path.unlink(missing_ok=True)
        return False, f"Patch application failed: {error}"

    try:
        with open(output_path, "rb") as f:
            patched_data = f.read()

        # Step 3: Verify patched output's signature (simulates bootloader verification)
        # Reconstruct what bootloader sees: [patched binary][0xFF fill] then verify signature
        with open(to_detached_signature, "rb") as f:
            signature = f.read()

        success, error = verify_firmware_signature_with_padding(patched_data, signature, flash_slot_size, firmware_key_manager)
        if not success:
            return False, f"Firmware signature verification failed: {error}"

        # Step 4: Compare to expected .signed.bin for additional validation
        if to_signed_bin:
            with open(to_signed_bin, "rb") as f:
                expected_data = f.read()

            if patched_data != expected_data:
                return (
                    False,
                    f"Patched result does not match expected .signed.bin (size: {len(patched_data)} vs {len(expected_data)})",
                )

    finally:
        output_path.unlink(missing_ok=True)

    return True, None


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


@cli.command(help="Verify a delta patch signature")
@click.option("--patch", required=True, type=click.Path(exists=True, path_type=Path), help="Path to delta patch file")
@click.option("--product", required=True, type=click.Choice(PRODUCTS), help="Which product to verify for")
@click.option("--key-type", required=False, default="dev", type=click.Choice(KEY_TYPES, case_sensitive=False))
@click.option("--keys-dir", required=False, default=None, type=click.Path(exists=True, path_type=Path))
def verify_patch(patch, product, key_type, keys_dir):
    """Verify the signature of a delta patch file."""
    if keys_dir is None:
        keys_dir = Path(__file__).parent.parent.parent / "config" / "keys"

    patch_keys = PatchSigningKeys(keys_dir, product, key_type)
    key_manager = LocalKeyManager(patch_keys)

    success, error = verify_patch_signature(patch, key_manager)
    if success:
        click.echo("PASSED")
    else:
        click.echo(f"FAILED: {error}")
        raise click.Abort()


@cli.command(help="Verify a delta update with signature verification (simulates bootloader)")
@click.option(
    "--patch", required=True, type=click.Path(exists=True, path_type=Path), help="Path to delta patch file (.signed.patch)"
)
@click.option(
    "--from-signed-bin", required=True, type=click.Path(exists=True, path_type=Path), help="Source firmware (.signed.bin)"
)
@click.option(
    "--to-detached-signature",
    required=True,
    type=click.Path(exists=True, path_type=Path),
    help="Target firmware's .detached_signature for signature verification",
)
@click.option(
    "--to-signed-bin",
    required=False,
    type=click.Path(exists=True, path_type=Path),
    help="Optional: expected target firmware (.signed.bin) for byte comparison",
)
@click.option("--product", required=True, type=click.Choice(PRODUCTS), help="Which product to verify for")
@click.option("--key-type", required=False, default="dev", type=click.Choice(KEY_TYPES, case_sensitive=False))
@click.option("--keys-dir", required=False, default=None, type=click.Path(exists=True, path_type=Path))
@click.option("--image-type", required=False, default="app", type=click.Choice(["app", "loader"]))
def verify_delta(patch, from_signed_bin, to_detached_signature, to_signed_bin, product, key_type, keys_dir, image_type):
    """Verify a delta firmware update (simulates bootloader verification).

    Verifies: from.signed.bin + patch -> patched output

    This simulates what the bootloader does:
    - Flash contains: [firmware binary][0xFF fill][signature]
    - Bootloader hashes [firmware binary][0xFF fill] and verifies against [signature]

    Steps:
    1. Verify patch signature (proves patch is authentic)
    2. Apply patch to source .signed.bin
    3. Verify patched output's signature:
       - Pad patched output with 0xFF to flash slot size
       - Verify hash against detached signature
    4. (Optional) Compare to expected .signed.bin with --to-signed-bin
    """
    if keys_dir is None:
        keys_dir = Path(__file__).parent.parent.parent / "config" / "keys"

    patch_keys = PatchSigningKeys(keys_dir, product, key_type)
    patch_key_manager = LocalKeyManager(patch_keys)

    firmware_keys = SigningKeys(keys_dir, product, key_type, image_type)
    firmware_key_manager = LocalKeyManager(firmware_keys)

    try:
        flash_slot_size = get_application_partition_size(product)
    except (FileNotFoundError, ValueError) as e:
        click.echo(f"FAILED: Could not get flash slot size for product {product}: {e}")
        raise click.Abort()

    success, error = verify_delta_update(
        patch, from_signed_bin, to_detached_signature, patch_key_manager, firmware_key_manager, flash_slot_size, to_signed_bin
    )
    click.echo("=" * 80)
    click.echo("Patch Information:\n")
    click.echo(f"Patch From: {from_signed_bin}")
    click.echo(f"Patch: {patch}")
    click.echo(f"Patched ELF Detached Signature: {to_detached_signature}\n")

    click.echo("Platform Specific Metadata:\n")
    click.echo(f"Patch Verification Key: {patch_keys.public_key_path}")
    click.echo(f"Firmware Verification Key: {firmware_keys.public_key_path}")
    click.echo(f"Flash Slot Size: {flash_slot_size}")
    click.echo("=" * 80)
    if not success:
        click.echo(f"Delta verification: FAILED: {error}")
        raise click.Abort()
    click.echo("Delta verification: PASSED")


if __name__ == "__main__":
    cli()

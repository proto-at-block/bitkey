#!/usr/bin/env python3

import ctypes
import logging
import tempfile
from pathlib import Path
from typing import Optional, Tuple

import click
import semver
from bitkey.elf_signer import ElfSigner
from bitkey.fwa.bitkey_fwa.constants import PRODUCT_W1A, PRODUCT_W3A_CORE, PRODUCT_W3A_UXC, PRODUCTS
from bitkey.key_manager import KeyManager, LocalKeyManager, PatchSigningKeys, PicocertKeyManager, SigningKeys
from bitkey.metadata import Metadata
from bitkey.partition_info import (
    get_application_partition_size,
    get_bootloader_metadata_offset_and_size,
    get_bootloader_partition_size,
)
from bitkey.signer_utils import (
    IMAGE_TYPES,
    KEY_TYPES,
    AssetInfo,
    FirmwareSignerException,
    semver_to_int,
)
from bitkey.stm32u5_signer import Stm32U5ElfSigner
from Crypto.Hash import SHA256
from Crypto.PublicKey import ECC
from Crypto.Signature import DSS

logging.basicConfig(level=logging.WARN, format="[%(levelname)s] %(message)s ")
logger = logging.getLogger("signer")

cli = click.Group()

EFR32_PROPERTIES_MAGIC = [0x13, 0xB7, 0x79, 0xFA, 0xC9, 0x25, 0xDD, 0xB7, 0xAD, 0xF3, 0xCF, 0xE0, 0xF1, 0xB6, 0x14, 0xB8]

ECC_P256_SIG_SIZE = 64
PLACEHOLDER_SIGNATURE = b"\xca\xfe" * (ECC_P256_SIG_SIZE // 2)
PRODUCT_CHIP_ID_LENGTH = {
    PRODUCT_W1A: 8,
    PRODUCT_W3A_CORE: 8,
    PRODUCT_W3A_UXC: 12,
}


def parse_chip_id(chip_id: str, expected_length: int) -> bytes:
    """Parse a chip ID hex string into bytes with an exact byte length."""
    normalized = chip_id.strip()
    expected_hex_len = expected_length * 2
    if len(normalized) != expected_hex_len:
        raise click.BadParameter(f"chip-id must be exactly {expected_hex_len} hex characters")

    try:
        parsed = bytes.fromhex(normalized)
    except ValueError as exc:
        raise click.BadParameter("chip-id must contain only hex bytes") from exc

    return parsed

# Delta patch version header — must match fwup_delta_header_v1_t in fwup_delta_impl.h.
FWUP_DELTA_HEADER_MAGIC = b"BKFW"
FWUP_DELTA_HEADER_VERSION_1 = 1
FWUP_DELTA_HEADER_V1_SIZE = 9  # magic(4) + header_version(1) + header_size(1) + major(1) + minor(1) + patch(1)


class DeltaPatchHeaderV1(ctypes.LittleEndianStructure):
    """Mirrors fwup_delta_header_v1_t (packed, 9 bytes)."""
    _pack_ = 1
    _fields_ = [
        ("magic", ctypes.c_char * 4),
        ("header_version", ctypes.c_uint8),
        ("header_size", ctypes.c_uint8),
        ("fw_major", ctypes.c_uint8),
        ("fw_minor", ctypes.c_uint8),
        ("fw_patch", ctypes.c_uint8),
    ]


if ctypes.sizeof(DeltaPatchHeaderV1) != FWUP_DELTA_HEADER_V1_SIZE:
    raise RuntimeError(
        f"DeltaPatchHeaderV1 has size {ctypes.sizeof(DeltaPatchHeaderV1)}, "
        f"expected {FWUP_DELTA_HEADER_V1_SIZE}"
    )


def build_delta_patch_header(version: str) -> bytes:
    """Build the 9-byte version header prepended to delta patch files."""
    v = semver.VersionInfo.parse(version)
    for name, val in [("major", v.major), ("minor", v.minor), ("patch", v.patch)]:
        if val > 255:
            raise ValueError(
                f"Version component '{name}' is {val}, exceeds uint8 max (255)"
            )
    hdr = DeltaPatchHeaderV1(
        magic=FWUP_DELTA_HEADER_MAGIC,
        header_version=FWUP_DELTA_HEADER_VERSION_1,
        header_size=FWUP_DELTA_HEADER_V1_SIZE,
        fw_major=v.major,
        fw_minor=v.minor,
        fw_patch=v.patch,
    )
    return bytes(hdr)


def verify_patch_signature(
    patch_path: Path, key_manager: KeyManager
) -> Tuple[bool, Optional[str]]:
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

    # Strip the signature from the end, then strip any version header from the start.
    patch_content = patch_data[:-ECC_P256_SIG_SIZE]
    if patch_content[: len(FWUP_DELTA_HEADER_MAGIC)] == FWUP_DELTA_HEADER_MAGIC:
        if len(patch_content) < FWUP_DELTA_HEADER_V1_SIZE:
            raise ValueError(f"Patch has BKFW magic but is too small for header: {len(patch_content)} bytes")
        header = DeltaPatchHeaderV1.from_buffer_copy(patch_content[:FWUP_DELTA_HEADER_V1_SIZE])
        header_version = header.header_version
        header_size = header.header_size
        if header_version != FWUP_DELTA_HEADER_VERSION_1:
            raise ValueError(f"Unknown BKFW header_version: {header_version}")
        expected_size = ctypes.sizeof(DeltaPatchHeaderV1)
        if header_size != expected_size:
            raise ValueError(
                f"BKFW header_size mismatch for version {header_version}: "
                f"got {header_size}, expected {expected_size}")
        patch_content = patch_content[header_size:]

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
        return (
            False,
            f"Invalid signature size: {len(signature)} bytes (expected {ECC_P256_SIG_SIZE})",
        )
    if signature == PLACEHOLDER_SIGNATURE:
        return (
            False,
            "Signature contains placeholder bytes (0xcafe...) and appears unsigned/pre-signing.",
        )

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


def verify_firmware_signature_embedded(
    signed_bin_data: bytes,
    key_manager: KeyManager,
) -> Tuple[bool, Optional[str]]:
    """Verify a full firmware/partition binary where the final 64 bytes are an embedded signature.

    Note:
        This expects the complete partition image (.bin) with the signature stored in-line
        in the last 64 bytes. Elsewhere in this module and in `elf_signer`, the term
        ".signed.bin" typically refers to an objcopy-produced binary with the signature
        section removed and a detached 64-byte signature. This function is for the
        embedded-signature form only.
    """
    if len(signed_bin_data) < ECC_P256_SIG_SIZE:
        return (
            False,
            f"Binary too small: {len(signed_bin_data)} bytes (minimum {ECC_P256_SIG_SIZE})",
        )

    signing_input = signed_bin_data[:-ECC_P256_SIG_SIZE]
    signature = signed_bin_data[-ECC_P256_SIG_SIZE:]
    if signature == PLACEHOLDER_SIGNATURE:
        return (
            False,
            "Embedded signature contains placeholder bytes (0xcafe...) and appears unsigned/pre-signing.",
        )
    digest = SHA256.new(signing_input)

    try:
        key_manager.verify_signature(digest, signature)
        return True, None
    except ValueError as e:
        return False, str(e)


def verify_bootloader_signature_with_metadata(
    signed_bin_data: bytes,
    detached_signature: bytes,
    detached_metadata: bytes,
    bootloader_size: int,
    metadata_offset: int,
    metadata_size: int,
    key_manager: KeyManager,
) -> Tuple[bool, Optional[str]]:
    """Verify detached bootloader artifacts by reconstructing bootloader signing input.

    Bootloader signing input includes metadata near the end of the bootloader partition.
    The distributed .signed.bin excludes this metadata, so we must reinsert it at
    the configured metadata offset before hashing.
    """
    if len(detached_signature) != ECC_P256_SIG_SIZE:
        return (
            False,
            f"Invalid signature size: {len(detached_signature)} bytes (expected {ECC_P256_SIG_SIZE})",
        )

    if len(detached_metadata) > metadata_size:
        return False, f"Metadata too large: {len(detached_metadata)} > {metadata_size}"

    # The metadata region starts near the end of the partition.
    if len(signed_bin_data) > metadata_offset:
        return (
            False,
            f"Bootloader binary overlaps metadata region: {len(signed_bin_data)} > metadata_offset {metadata_offset}",
        )

    reconstructed = signed_bin_data
    reconstructed += b"\xff" * (metadata_offset - len(reconstructed))
    reconstructed += detached_metadata
    reconstructed += b"\xff" * (metadata_size - len(detached_metadata))

    return verify_firmware_signature_with_padding(
        reconstructed,
        detached_signature,
        bootloader_size,
        key_manager,
    )


def _get_partition_size_for_image_type(product: str, image_type: str) -> int:
    if image_type == "app":
        return get_application_partition_size(product)
    if image_type == "bl":
        return get_bootloader_partition_size(product)
    raise ValueError(f"Unsupported image type: {image_type}")


def _infer_image_type_from_bin_name(bin_path: Path) -> Optional[str]:
    """Infer image type from common firmware artifact names."""
    name = bin_path.name.lower()
    if "-loader-" in name:
        return "bl"
    if "-app-" in name:
        return "app"
    return None


def _get_firmware_key_manager(product: str, signing_keys: SigningKeys) -> KeyManager:
    # UXC verifies via picocert chain. EFR32 uses raw public key.
    if product == PRODUCT_W3A_UXC:
        return PicocertKeyManager(signing_keys)
    return LocalKeyManager(signing_keys)


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
    CHIP_ID_LENGTH = 8

    def __init__(self, unsigned_elf_path: Path, partitions_config_path: str):
        super().__init__(unsigned_elf_path, partitions_config_path)

    def _set_build_id(self, slot: str):
        meta_bytes = self.elf.get_section_by_name(f".app_{slot}_metadata_section").data()
        digest = Metadata.read_from_bytes(meta_bytes)["hash"]
        self._write_symbol_data("g_memfault_sdk_derived_build_id", digest)

        # Per eMemfaultBuildIdType:
        # kMemfaultBuildIdType_MemfaultBuildIdSha1 = 3,
        self._write_symbol_data("g_memfault_build_id", b"\x03")

    def _set_version(self, image_type: str, app_version: str, chip_id: bytes = None):
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
        PRODUCT_ID_OFFSET = 40
        PRODUCT_ID_SIZE = 16
        props[APP_VERSION_OFFSET : APP_VERSION_OFFSET + 4] = version_int.to_bytes(4, byteorder="little")

        if image_type == "app" and chip_id is not None:
            if len(props) < (PRODUCT_ID_OFFSET + PRODUCT_ID_SIZE):
                raise FirmwareSignerException(
                    f"{sym_name} is too small ({len(props)} bytes) for 16-byte productId format"
                )
            props[PRODUCT_ID_OFFSET : PRODUCT_ID_OFFSET + len(chip_id)] = chip_id
            props[PRODUCT_ID_OFFSET + len(chip_id) : PRODUCT_ID_OFFSET + PRODUCT_ID_SIZE] = b"\x00" * (
                PRODUCT_ID_SIZE - len(chip_id)
            )

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
    def create_and_sign(signing_key_pem, from_file, to_file, patch_file, version=None):
        """Create and sign a delta patch from from_file to to_file, and write it to patch_file.

        If version is provided (e.g. "1.2.3"), a version header is prepended to the patch
        data before signing. The signature covers [header][detools data], so the version
        is manufacturer-attested and cannot be tampered with independently of the patch.
        """
        import detools  # keep this local so that firmware signer service does not depend on detools

        signing_key = ECC.import_key(signing_key_pem)

        with tempfile.NamedTemporaryFile() as tmp:
            with open(tmp.name, "wb") as tmp_file_handle, open(from_file, "rb") as from_file_handle, open(
                to_file, "rb"
            ) as to_file_handle:
                detools.create_patch(
                    ffrom=from_file_handle, fto=to_file_handle, fpatch=tmp_file_handle, compression="heatshrink"
                )

            header = build_delta_patch_header(version) if version is not None else b""

            # Sign [header][detools patch data]
            with open(tmp.name, "rb") as tmp_file:
                signing_input = header + tmp_file.read()
            digest = SHA256.new(signing_input)
            logger.debug(f"digest: {digest.hexdigest()}")
            signature = DSS.new(signing_key, "deterministic-rfc6979").sign(digest)
            logger.debug(f"signature: {signature.hex()}")

            # Write [header][detools patch data][signature]
            with open(patch_file, "wb") as f, open(tmp.name, "rb") as tmp_file:
                f.write(header)
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
@click.option(
    "--chip-id",
    required=False,
    type=click.STRING,
    help="Optional per-device chip ID in hex (e.g. 0011223344556677)",
)
@click.option("--partitions-config", required=True, type=click.STRING, help="Path to partitions.yml")
@click.option("--keys-dir", required=True, type=click.Path(exists=True, path_type=Path), help="Path to keys directory")
@click.option("--verbose", required=False, type=click.BOOL, help="Enable logging")
def sign(elf, product, key_type, image_type, slot, app_version, chip_id, partitions_config, keys_dir, verbose):
    if verbose:
        logger.setLevel(logging.DEBUG)

    chip_id_bytes = None
    if chip_id is not None:
        if image_type != "app":
            raise click.BadParameter("--chip-id is only supported for app images")
        expected_len = PRODUCT_CHIP_ID_LENGTH[product]
        chip_id_bytes = parse_chip_id(chip_id, expected_len)

    asset_info = AssetInfo(app_version, slot, product, image_type, chip_id=chip_id_bytes)
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
    else:
        # EFR32 platforms (w1a, w3a-core)
        signer = Efr32ElfSigner(elf, partitions_config)
    key_manager = _get_firmware_key_manager(product, signing_keys)

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


@cli.command(help="Verify a signed .bin with auto-detected signature mode")
@click.option(
    "--input-bin",
    "--signed-bin",
    "input_bin",
    required=True,
    type=click.Path(exists=True, path_type=Path),
    help="Path to input firmware .bin (supports legacy --signed-bin name)",
)
@click.option(
    "--detached-signature",
    required=False,
    type=click.Path(exists=True, path_type=Path),
    help="Detached signature file (required when bin does not include embedded signature)",
)
@click.option(
    "--detached-metadata",
    required=False,
    type=click.Path(exists=True, path_type=Path),
    help="Detached metadata file (required for detached bootloader verification)",
)
@click.option(
    "--product",
    required=True,
    type=click.Choice(PRODUCTS),
    help="Which product to verify for",
)
@click.option(
    "--key-type",
    required=False,
    default="dev",
    type=click.Choice(KEY_TYPES, case_sensitive=False),
    help="Development or production keys (default: dev)",
)
@click.option(
    "--image-type",
    required=False,
    default=None,
    type=click.Choice(["app", "bl"], case_sensitive=False),
    help="Optional image type override when filename is not inferable",
)
@click.option(
    "--keys-dir",
    required=False,
    default=None,
    type=click.Path(exists=True, path_type=Path),
)
def verify_bin(
    input_bin,
    detached_signature,
    detached_metadata,
    product,
    key_type,
    image_type,
    keys_dir,
):
    """Verify a .signed.bin file.

    Resolves image type from filename by default:
    - names containing '-app-' => app
    - names containing '-loader-' => bl
    - otherwise, requires --image-type

    Auto-detects signature mode by comparing input .bin size to partition size:
    - size == partition_size: signature is embedded in trailing 64 bytes.
    - size < partition_size:
      - app: requires detached signature + 0xFF padding verification.
      - bl: requires detached signature + detached metadata reconstruction.
    """
    if keys_dir is None:
        keys_dir = Path(__file__).parent.parent.parent / "config" / "keys"

    inferred_image_type = _infer_image_type_from_bin_name(input_bin)
    if inferred_image_type and image_type and inferred_image_type != image_type:
        click.echo(
            "FAILED: --image-type does not match input file name: "
            f"{input_bin.name} implies '{inferred_image_type}', but got '{image_type}'."
        )
        raise click.Abort()

    effective_image_type = inferred_image_type or image_type
    if effective_image_type is None:
        click.echo(
            "FAILED: Could not infer image type from input file name. "
            "Provide --image-type {app|bl} or use a conventional name containing '-app-' or '-loader-'."
        )
        raise click.Abort()

    signing_keys = SigningKeys(keys_dir, product, key_type, effective_image_type)
    key_manager = _get_firmware_key_manager(product, signing_keys)

    try:
        partition_size = _get_partition_size_for_image_type(product, effective_image_type)
    except (FileNotFoundError, ValueError) as e:
        click.echo(
            f"FAILED: Could not get partition size for product {product} ({effective_image_type}): {e}"
        )
        raise click.Abort()

    signed_bin_data = input_bin.read_bytes()
    signed_bin_size = len(signed_bin_data)

    if signed_bin_size > partition_size:
        click.echo(
            f"FAILED: Binary too large for partition ({signed_bin_size} > {partition_size}). "
            "Cannot auto-detect signature mode."
        )
        raise click.Abort()

    if signed_bin_size == partition_size:
        mode = "embedded"
        success, error = verify_firmware_signature_embedded(
            signed_bin_data, key_manager
        )
        if detached_signature is not None:
            click.echo(
                "Note: Ignoring --detached-signature because signature is embedded in the input bin"
            )
        if detached_metadata is not None:
            click.echo(
                "Note: Ignoring --detached-metadata because signature is embedded in the input bin"
            )
    else:
        mode = "detached"
        if detached_signature is None:
            click.echo(
                "FAILED: Detached signature is required when the input bin is smaller than partition size "
                f"({signed_bin_size} < {partition_size}). Provide --detached-signature."
            )
            raise click.Abort()

        signature = detached_signature.read_bytes()
        if effective_image_type == "bl":
            if detached_metadata is None:
                click.echo(
                    "FAILED: Detached metadata is required for detached bootloader verification. "
                    "Provide --detached-metadata."
                )
                raise click.Abort()
            metadata_offset, metadata_size = get_bootloader_metadata_offset_and_size(
                product
            )
            metadata = detached_metadata.read_bytes()
            success, error = verify_bootloader_signature_with_metadata(
                signed_bin_data,
                signature,
                metadata,
                partition_size,
                metadata_offset,
                metadata_size,
                key_manager,
            )
        else:
            success, error = verify_firmware_signature_with_padding(
                signed_bin_data,
                signature,
                partition_size,
                key_manager,
            )

    click.echo("=" * 80)
    click.echo("Bin Verification:\n")
    click.echo(f"Input Bin: {input_bin}")
    click.echo(f"Input Bin Size: {signed_bin_size}")
    if detached_signature:
        click.echo(f"Detached Signature: {detached_signature}")
    if detached_metadata:
        click.echo(f"Detached Metadata: {detached_metadata}")
    click.echo(f"Mode: {mode}")
    click.echo(f"Product: {product}")
    click.echo(f"Image Type: {effective_image_type}")
    click.echo(f"Partition Size: {partition_size}")
    click.echo(f"Verification Key: {signing_keys.public_key_path}")
    click.echo("=" * 80)

    if not success:
        click.echo(f"Bin verification: FAILED: {error}")
        raise click.Abort()
    click.echo("Bin verification: PASSED")


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

import logging
from pathlib import Path

import semver
from bitkey.elf_signer import ElfSigner
from bitkey.key_manager import KeyManager
from bitkey.metadata import Metadata
from bitkey.signer_utils import AssetInfo, FirmwareSignerException, semver_to_int

logger = logging.getLogger("signer")


class Stm32U5ElfSigner(ElfSigner):
    """Codesign STM32U5 firmware ELF (UXC platform).

    This signer is specific to the STM32U5 platform and uses:
    - Picocerts for certificate format
    - Simple app_properties structure with version field
    """
    CHIP_ID_LENGTH = 12

    def __init__(self, unsigned_elf_path: Path, partitions_config_path: str = None):
        super().__init__(unsigned_elf_path, partitions_config_path)

    def _validate_image_type(self, image_type: str):
        """STM32U5 supports bootloader and application firmware signing."""
        if image_type not in ["bl", "app"]:
            raise FirmwareSignerException(f"STM32U5 only supports 'bl' and 'app' image types, got: {image_type}")

    def _set_build_id(self, slot: str) -> None:
        """Writes a unique build identifier to a target binary for Memfault.

        :param slot: the slot identifier of the target image.
        :returns: ``None``
        """
        section = self.elf.get_section_by_name(f".app_{slot}_metadata_section")
        meta_bytes = section.data() if section else None
        if meta_bytes is None:
            return

        digest = Metadata.read_from_bytes(meta_bytes)["hash"]
        self._write_symbol_data("g_memfault_sdk_derived_build_id", digest)

        # Per eMemfaultBuildIdType:
        # kMemfaultBuildIdType_MemfaultBuildIdSha1 = 3,
        self._write_symbol_data("g_memfault_build_id", b"\x03")

    def _set_version(self, image_type: str, app_version: str, chip_id: bytes = None):
        """Set version in STM32U5 app_properties structure.

        STM32U5 app_properties is simpler than EFR32's sl_app_properties:
        - Offset 20: app.version (4 bytes, little-endian)

        Note: Bootloader does not update the version in bl_app_properties, it's compiled in.
        """
        if image_type == "bl":
            return

        version_int = semver_to_int(semver.VersionInfo.parse(app_version))

        sym_name = "app_properties"
        props = bytearray(self._read_symbol_data(sym_name))

        # Offset of app.version within app_properties
        VERSION_OFFSET = 16 + 4
        PRODUCT_ID_OFFSET = VERSION_OFFSET + 4
        PRODUCT_ID_SIZE = 16
        version_bytes = version_int.to_bytes(4, byteorder="little")
        props[VERSION_OFFSET : VERSION_OFFSET + 4] = version_bytes

        if chip_id is not None:
            if len(props) < (PRODUCT_ID_OFFSET + PRODUCT_ID_SIZE):
                raise FirmwareSignerException(
                    f"{sym_name} is too small ({len(props)} bytes) for 16-byte productId format"
                )
            props[PRODUCT_ID_OFFSET : PRODUCT_ID_OFFSET + len(chip_id)] = chip_id
            props[PRODUCT_ID_OFFSET + len(chip_id) : PRODUCT_ID_OFFSET + PRODUCT_ID_SIZE] = b"\x00" * (
                PRODUCT_ID_SIZE - len(chip_id)
            )

        self._write_symbol_data(sym_name, bytes(props))

    def codesign(self, key_manager: KeyManager, asset_info: AssetInfo):
        """
        Sign the firmware ELF file (apps only). For bootloaders, injects certificate but skips signing as they're protected via write-protected flash.
        STM32U5 specific implementation.
        """

        # Compute the presigned hash (inject certificate, etc)
        digest = self.gen_presign_hash(key_manager, asset_info)

        # STM32U5 loaders have injected certificates, but are unsigned.
        # In production, they are in write protected flash.
        # We still inject the certificate though, hence the "gen_presign_hash" call.
        if asset_info.get_image_type() == "bl":
            return

        # Pass the pre-signed artifact to key manager to sign
        signature = key_manager.generate_signature(digest)

        # Stitch it together and finalize
        self.stitch_and_finalize(key_manager, asset_info, signature, digest)

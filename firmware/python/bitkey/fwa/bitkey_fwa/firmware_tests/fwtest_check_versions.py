import os
from typing import Optional

import semver
from bitkey.metadata import Metadata

import bitkey_fwa
from bitkey_fwa.constants import (
    ASSET_APP,
    ASSET_LOADER,
    ENV_MFGTEST,
    PRODUCT_W1A,
    PRODUCT_W3A_CORE,
    PRODUCT_W3A_UXC,
    SECURITY_PROD,
    SUFFIX_ELF,
)
from bitkey_fwa.fwut import FirmwareUnderTest


class VersionChecks(bitkey_fwa.TestCase):
    """Validate firmware versions against maximum allowed values"""

    # Maximum version limits
    MAX_BL_VERSION = "2.0.0"

    # Per-product maximum app version limits
    MAX_APP_VERSION = {
        PRODUCT_W1A: "1.3.0",
        PRODUCT_W3A_CORE: "1.3.0",
        PRODUCT_W3A_UXC: "1.3.0",
    }

    # Per-product maximum mfgtest version limits
    # Keep these lower than the packout app version
    MAX_PROD_MFGTEST_VERSION = {
        PRODUCT_W1A: "1.0.50",
        PRODUCT_W3A_CORE: "1.2.0",
        PRODUCT_W3A_UXC: "1.2.0",
    }

    # Magic values for app_properties structs
    EFR32_MAGIC = b"\x13\xb7\x79\xfa\xc9\x25\xdd\xb7\xad\xf3\xcf\xe0\xf1\xb6\x14\xb8"
    STM32U5_MAGIC = b"BITKEY-UXC\x00\x00\x00\x00\x00\x00"

    # Struct offsets and sizes
    MAGIC_SIZE = 16  # Size of magic value in bytes
    VERSION_SIZE = 4  # Size of version field in bytes
    PRODUCT_ID_SIZE = 16  # Size of productId field in bytes
    EFR32_VERSION_OFFSET = 32  # Offset to app.version in EFR32 ApplicationProperties_t
    # Offset to app.productId in EFR32 ApplicationProperties_t
    EFR32_PRODUCT_ID_OFFSET = 40
    STM32U5_VERSION_OFFSET = 20  # Offset to app.version in STM32U5 app_properties_t
    # Offset to app.productId in STM32U5 app_properties_t
    STM32U5_PRODUCT_ID_OFFSET = 24

    PRODUCT_CHIP_ID_LENGTH = {
        PRODUCT_W1A: 8,
        PRODUCT_W3A_CORE: 8,
        PRODUCT_W3A_UXC: 12,
    }

    # Version format parsing (MMNNPPP: major * 10^5 + minor * 10^3 + patch)
    # Format: 2 digits for major, 2 for minor, 3 for patch (from semver_to_int in signer_utils.py)
    # Assumes: major < 100, minor < 100, and patch < 1000
    # Note: Leading zeros are dropped when string is converted to int, but divisor accounts for full 7-digit range
    VERSION_MAJOR_DIVISOR = 100000  # 10^5 to extract major
    VERSION_MINOR_DIVISOR = 1000  # 10^3 to extract minor
    VERSION_MINOR_MODULO = 100
    VERSION_PATCH_MODULO = 1000

    def _get_metadata_section_name(self) -> Optional[str]:
        """Get the metadata section name based on asset type

        Returns:
            Section name for metadata (e.g., ".bl_metadata_section"), or None if unknown asset
        """
        asset = FirmwareUnderTest.asset
        if asset == ASSET_LOADER:
            return ".bl_metadata_section"
        elif asset == ASSET_APP:
            slot = FirmwareUnderTest.slot
            return f".app_{slot}_metadata_section"
        else:
            return None

    def _get_version_from_metadata(self) -> Optional[semver.VersionInfo]:
        """Extract version from metadata section

        Returns:
            Semantic version object, or None if not found
        """
        try:
            # Get the appropriate metadata section name
            section_name = self._get_metadata_section_name()
            if section_name is None:
                return None

            # Read the metadata section from ELF
            section = self.get_elf_section_by_name(section_name)
            metadata_bytes = self.get_elf_section_data(section)

            # Parse metadata using Metadata class
            metadata = Metadata.read_from_bytes(metadata_bytes)

            # Extract version information
            major = metadata.get("ver_major", 0)
            minor = metadata.get("ver_minor", 0)
            patch = metadata.get("ver_patch", 0)

            return semver.VersionInfo(major=major, minor=minor, patch=patch)
        except Exception:
            # Metadata section not found or parsing failed
            return None

    def _get_efr32_app_properties_data(self) -> Optional[bytes]:
        """Extract EFR32 sl_app_properties symbol data

        Structure: magic(16) + structVersion(4) + signatureType(4) + signatureLocation(4) +
                  app.type(4) + app.version(4) + ...

        Returns:
            Raw app_properties bytes, or None if not found
        """
        try:
            asset = FirmwareUnderTest.asset

            if asset == ASSET_APP:
                slot = FirmwareUnderTest.slot
                section_name = f".app_{slot}_properties_section"
            else:
                # Bootloader: sl_app_properties is in .rodata
                section_name = ".rodata"

            props_data = self.get_elf_symbol_data(
                "sl_app_properties",
                section_name,
            )

            # Verify magic value
            actual_magic = props_data[: self.MAGIC_SIZE]
            if actual_magic != self.EFR32_MAGIC:
                self.fail(
                    f"sl_app_properties magic mismatch: expected {self.EFR32_MAGIC.hex()}, "
                    f"got {actual_magic.hex()}"
                )

            return props_data
        except (KeyError, IndexError):
            return None

    def _get_version_from_efr32_app_properties(self) -> Optional[semver.VersionInfo]:
        """Extract version from EFR32 sl_app_properties symbol

        Structure: magic(16) + structVersion(4) + signatureType(4) + signatureLocation(4) +
                  app.type(4) + app.version(4) + ...
        app.version is at offset 32

        Returns:
            Semantic version object, or None if not found
        """
        props_data = self._get_efr32_app_properties_data()
        if props_data is None:
            return None

        try:
            # Extract the version integer
            version_offset = self.EFR32_VERSION_OFFSET
            version_int = int.from_bytes(
                props_data[version_offset: version_offset + self.VERSION_SIZE],
                byteorder="little",
            )

            # Parse the MMNNPPP format
            major = version_int // self.VERSION_MAJOR_DIVISOR
            minor = (
                version_int // self.VERSION_MINOR_DIVISOR
            ) % self.VERSION_MINOR_MODULO
            patch = version_int % self.VERSION_PATCH_MODULO

            return semver.VersionInfo(major=major, minor=minor, patch=patch)
        except (KeyError, IndexError):
            return None

    def _get_stm32u5_app_properties_data(self) -> Optional[bytes]:
        """Extract STM32U5 app_properties symbol data

        Structure: magic(16) + structVersion(4) + app.version(4)

        Returns:
            Raw app_properties bytes, or None if not found
        """
        try:
            asset = FirmwareUnderTest.asset

            if asset == ASSET_APP:
                slot = FirmwareUnderTest.slot
                section_name = f".app_{slot}_properties_section"
                symbol_name = "app_properties"
            else:
                # Bootloader: bl_app_properties is in .rodata
                section_name = ".rodata"
                symbol_name = "bl_app_properties"

            props_data = self.get_elf_symbol_data(symbol_name, section_name)

            # Verify magic value
            actual_magic = props_data[: self.MAGIC_SIZE]
            if actual_magic != self.STM32U5_MAGIC:
                self.fail(
                    f"app_properties magic mismatch: expected {self.STM32U5_MAGIC.hex()}, "
                    f"got {actual_magic.hex()}"
                )

            return props_data
        except (KeyError, IndexError):
            return None

    def _get_version_from_stm32u5_app_properties(self) -> Optional[semver.VersionInfo]:
        """Extract version from STM32U5 app_properties symbol

        Structure: magic(16) + structVersion(4) + app.version(4)
        app.version is at offset 20

        Returns:
            Semantic version object, or None if not found
        """
        props_data = self._get_stm32u5_app_properties_data()
        if props_data is None:
            return None

        try:
            # Extract the version integer
            version_offset = self.STM32U5_VERSION_OFFSET
            version_int = int.from_bytes(
                props_data[version_offset: version_offset + self.VERSION_SIZE],
                byteorder="little",
            )

            # Parse the MMNNPPP format
            major = version_int // self.VERSION_MAJOR_DIVISOR
            minor = (
                version_int // self.VERSION_MINOR_DIVISOR
            ) % self.VERSION_MINOR_MODULO
            patch = version_int % self.VERSION_PATCH_MODULO

            return semver.VersionInfo(major=major, minor=minor, patch=patch)
        except (KeyError, IndexError):
            return None

    def _get_version_from_app_properties(self) -> Optional[semver.VersionInfo]:
        """Extract version from app_properties based on product type

        Returns:
            Semantic version object, or None if not found
        """
        product = FirmwareUnderTest.product

        if product in (PRODUCT_W1A, PRODUCT_W3A_CORE):
            return self._get_version_from_efr32_app_properties()
        elif product == PRODUCT_W3A_UXC:
            return self._get_version_from_stm32u5_app_properties()
        else:
            return None

    def _get_app_properties_product_id(self) -> Optional[bytes]:
        """Extract app.productId from app_properties based on product type

        Returns:
            productId bytes, or None if not found
        """
        product = FirmwareUnderTest.product

        if product in (PRODUCT_W1A, PRODUCT_W3A_CORE):
            props_data = self._get_efr32_app_properties_data()
            product_id_offset = self.EFR32_PRODUCT_ID_OFFSET
        elif product == PRODUCT_W3A_UXC:
            props_data = self._get_stm32u5_app_properties_data()
            product_id_offset = self.STM32U5_PRODUCT_ID_OFFSET
        else:
            return None

        if props_data is None:
            return None

        product_id = props_data[
            product_id_offset: product_id_offset + self.PRODUCT_ID_SIZE
        ]
        if len(product_id) != self.PRODUCT_ID_SIZE:
            return None

        return product_id

    def _is_per_device_signed_application(self) -> bool:
        """Check whether the signed application is bound to a device chip ID."""
        if not FirmwareUnderTest.signed or FirmwareUnderTest.asset != ASSET_APP:
            return False

        # Only UXC/STM32U5 mfgtest images can use the relaxed version gate today.
        # W1 EFR32 bootloaders never support per-device binding, and some W3 EFR32
        # units in the field predate the per-device-capable bootloader.
        if FirmwareUnderTest.product != PRODUCT_W3A_UXC:
            return False

        product_id = self._get_app_properties_product_id()
        if (
            product_id is None
            or len(product_id) != self.PRODUCT_ID_SIZE
            or product_id == (b"\x00" * self.PRODUCT_ID_SIZE)
        ):
            return False

        chip_id_length = self.PRODUCT_CHIP_ID_LENGTH.get(
            FirmwareUnderTest.product
        )
        if chip_id_length is None:
            return False

        padding = product_id[chip_id_length:]
        if padding != (b"\x00" * len(padding)):
            self.fail(
                "Per-device productId padding must be zero after the chip ID bytes"
            )

        return True

    def _get_and_validate_version(self, firmware_type: str) -> semver.VersionInfo:
        """Get firmware version and validate consistency for signed builds

        Args:
            firmware_type: String describing the firmware type for error messages

        Returns:
            The validated version
        """
        metadata_version = self._get_version_from_metadata()
        app_properties_version = self._get_version_from_app_properties()

        # Metadata must always exist
        if metadata_version is None:
            self.fail(f"{firmware_type} metadata version could not be found")

        # For signed firmware, both must exist and match
        if FirmwareUnderTest.signed:
            if app_properties_version is None:
                self.fail(
                    f"{firmware_type} app_properties version could not be found"
                )

            # Both exist, they must match
            self.assertEqual(
                metadata_version,
                app_properties_version,
                f"{firmware_type} version mismatch: metadata has {metadata_version}, "
                f"app_properties has {app_properties_version}",
            )

        # For unsigned firmware, only metadata is required (app_properties won't be set yet)
        return metadata_version

    def _compare_version(
        self,
        actual: Optional[semver.VersionInfo],
        max_version_str: str,
        firmware_type: str,
    ) -> None:
        """Compare version against maximum using semver

        Args:
            actual: Semantic version object to compare
            max_version_str: String representation of max version (e.g., "2.0.0")
            firmware_type: String describing the firmware type for error messages
        """
        if actual is None:
            self.fail(
                f"{firmware_type} version could not be determined from firmware"
            )

        max_version = semver.VersionInfo.parse(max_version_str)

        self.assertLess(
            actual,
            max_version,
            f"{firmware_type} version {actual} must be less than {max_version}",
        )

    @bitkey_fwa.product(PRODUCT_W1A, PRODUCT_W3A_CORE)
    @bitkey_fwa.asset(ASSET_LOADER)
    @bitkey_fwa.suffix(SUFFIX_ELF)
    def fwtest_efr32_bootloader_version_check(self):
        """Verify bootloader version does not exceed maximum and metadata/app_properties match"""
        version = self._get_and_validate_version("Bootloader")
        self._compare_version(version, self.MAX_BL_VERSION, "Bootloader")

    @bitkey_fwa.asset(ASSET_APP)
    @bitkey_fwa.suffix(SUFFIX_ELF)
    def fwtest_application_version_check(self):
        """Verify application version does not exceed maximum and metadata/app_properties match"""
        product = FirmwareUnderTest.product
        max_version = self.MAX_APP_VERSION.get(product)
        if max_version is None:
            self.fail(f"No max app version defined for product {product}")
        version = self._get_and_validate_version("Application")
        self._compare_version(version, max_version, "Application")

    @bitkey_fwa.asset(ASSET_APP)
    @bitkey_fwa.suffix(SUFFIX_ELF)
    @bitkey_fwa.environment(ENV_MFGTEST)
    @bitkey_fwa.security(SECURITY_PROD)
    def fwtest_mfgtest_version_check(self):
        """Verify production mfgtest application version does not exceed maximum and metadata/app_properties match"""
        product = FirmwareUnderTest.product
        firmware_type = "Production mfgtest application"
        if self._is_per_device_signed_application():
            max_version = self.MAX_APP_VERSION.get(product)
            firmware_type = "Per-device signed production mfgtest application"
            if max_version is None:
                self.fail(f"No max app version defined for product {product}")
        else:
            max_version = self.MAX_PROD_MFGTEST_VERSION.get(product)
        if max_version is None:
            self.fail(f"No max mfgtest version defined for product {product}")
        mode = os.environ.get("FWA_MFGTEST_VERSION_CHECK", "").strip().lower()
        version = self._get_and_validate_version(firmware_type)
        try:
            self._compare_version(version, max_version, firmware_type)
        except AssertionError as ex:
            if mode == "warn":
                raise Warning(str(ex))
            raise

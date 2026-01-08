import re
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import semver
from bitkey.fwa.bitkey_fwa.constants import (
    PRODUCT_W1A,
    PRODUCT_W3A_CORE,
    PRODUCT_W3A_UXC,
    SIGNER_DEVELOPMENT,
    SIGNER_LOCALSTACK,
    SIGNER_PRODUCTION,
    SIGNER_STAGING,
)

IMAGE_TYPES = ["bl", "app", "patch"]
KEY_TYPES = ["dev", "prod", "prod-proto"]
SLOTS = ["a", "b"]

# Signature symbol names
BL_CERTIFICATE_SYMBOL = "bl_certificate"
BL_SIGNATURE_SYMBOL = "bl_codesigning_signature"
APP_SIGNATURE_SYMBOL = "app_codesigning_signature"

DEFAULT_KEYS_DIR = Path(__file__).parent.parent / "config" / "keys"

# Certificate versions per product and image type for development
DEV_CERT_VERSIONS = {
    PRODUCT_W1A: {
        "app": 2,
        "bl": 1,
    },
    PRODUCT_W3A_CORE: {
        "app": 2,
        "bl": 1,
    },
    PRODUCT_W3A_UXC: {
        "app": 1,
        "bl": 1,
    },
}

# The version number of the signing cert for each environment per product
# localstack app cert will need to be generated for each user
APP_CERT_ENV_VERSIONS = {
    PRODUCT_W1A: {
        SIGNER_LOCALSTACK: 1,
        SIGNER_DEVELOPMENT: 1,
        SIGNER_STAGING: 1,
        SIGNER_PRODUCTION: 2,
    },
    PRODUCT_W3A_CORE: {
        SIGNER_LOCALSTACK: 1,
        SIGNER_DEVELOPMENT: 1,
        SIGNER_STAGING: 1,
        SIGNER_PRODUCTION: 1,
    },
    PRODUCT_W3A_UXC: {
        SIGNER_LOCALSTACK: 1,
        SIGNER_DEVELOPMENT: 1,
        SIGNER_STAGING: 1,
        SIGNER_PRODUCTION: 1,
    },
}


def semver_to_int(ver: semver.VersionInfo) -> int:
    """Convert semver to u32.
    2 digits for major, 2 for minor, and 3 for patch.
    """
    return int(f"{ver.major:02d}{ver.minor:02d}{ver.patch:03d}")


def get_latest_cert(
    cert_path: str, product: str, image_type: str, security_level: str, env_type: Optional[str] = None
) -> Path:
    """Get the latest signing cert where latest refers to the version number"""

    if env_type is None:
        fmt_env_type = ""
    else:
        fmt_env_type = "-" + env_type

    cert_path = Path(cert_path)
    cert_basename = f"{product}-{image_type}-signing-cert-{security_level}{fmt_env_type}.*.bin"

    possible_certs = cert_path.glob(cert_basename)

    latest_cert = max(possible_certs, key=lambda s: int(re.search(r"\.(\d+)\.", s.name).group(1)))

    return latest_cert


class FirmwareSignerException(Exception):
    pass


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
        return self.sym["st_value"]

    def name(self):
        return self.sym.name

    def size(self):
        return self.sym["st_size"]


@dataclass
class SignatureInfo:
    """Information about a signature extracted from an ELF file.

    Attributes:
        slot: Slot identifier ("a" or "b")
        address: Virtual address of signature in memory
        signature: Raw signature bytes (64 bytes for ECDSA P-256)
        is_valid: Whether the signature is cryptographically valid
        verification_message: Human-readable verification result
    """

    slot: str
    address: int
    signature: bytes
    is_valid: bool
    verification_message: str

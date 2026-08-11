import re
from binascii import hexlify
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from Crypto.PublicKey import ECC

from .constants import (
    PRODUCT_W3A_UXC,
    SECURITY_PROD,
    SIGNER_DEVELOPMENT,
    SIGNER_PERSONAL,
    SIGNER_PRODUCTION,
)
from .fwut import FirmwareUnderTest, keys_parent_path


def _get_key_data(key_path: Path) -> ECC.EccKey:
    """Get the key from the key_path"""

    with open(key_path, "rt") as f:
        key = ECC.import_key(f.read())
        return key


@dataclass
class ResolvedVerificationKeyPaths:
    public_key_path: str
    cert_path: Optional[str] = None
    private_key_path: Optional[str] = None


def _collect_possible_keys(key_fmt: str, product: str, security: str) -> list[Path]:
    """Build a list of matching keys, using dev-stack key names when a stack name is set."""
    key_path = keys_parent_path / f"{product}-{security}"

    if "app-signing-key" in key_fmt:
        signer_env = FirmwareUnderTest.signer_env
        stack_name = getattr(FirmwareUnderTest, "stack_name", None)

        # The app signing key names mirror bitkey.key_manager.SigningKeys and
        # scripts/sync-kms-keys.py:
        # 1. dev-stack -> use stack_name
        # 2. W3A-UXC shared stacks -> use no signer-env suffix
        # 3. personal prod for non-UXC -> use production suffix
        # 4. all other app cases -> append -{signer_env}
        if stack_name and signer_env == SIGNER_DEVELOPMENT:
            pattern = f"{product}-app-signing-key-{stack_name}-development.*.pub.pem"
        elif product == PRODUCT_W3A_UXC:
            pattern = key_fmt.format(product, security, "")
        elif signer_env == SIGNER_PERSONAL:
            suffix = ""
            if security == SECURITY_PROD:
                suffix = f"-{SIGNER_PRODUCTION}"
            pattern = key_fmt.format(product, security, suffix)
        else:
            pattern = key_fmt.format(product, security, f"-{signer_env}")
    else:
        # this is the case for bl-signing-key
        pattern = key_fmt.format(product, security)

    return list(key_path.glob(pattern))


def get_key_latest_version(key_fmt: str, product: str, security: str) -> Path:
    """Get the latest key from where latest refers to the wildcard number in the key name

    Raises:
        FileNotFoundError: If no keys matching the pattern are found
    """

    possible_keys = _collect_possible_keys(key_fmt, product, security)

    if len(possible_keys) == 0:
        raise FileNotFoundError("No keys found matching pattern")

    latest_key = max(
        possible_keys, key=lambda s: int(re.search(r"\.(\d+)\.", s.name).group(1))
    )

    return latest_key


def get_key(key_fmt: str) -> ECC.EccKey:
    """Given a key_fmt string with a version glob, get the key

    Raises:
        FileNotFoundError: If no keys matching the pattern are found
    """
    product = FirmwareUnderTest.product
    security = FirmwareUnderTest.security

    latest_key = get_key_latest_version(key_fmt, product, security)

    return _get_key_data(latest_key)


def get_sorted_key_names(key_fmt: str, product: str, security: str) -> list[Path]:
    """Get the names of all keys in the current signer env"""

    possible_keys = _collect_possible_keys(key_fmt, product, security)

    keys = sorted(
        possible_keys,
        key=lambda s: int(re.search(r"\.(\d+)\.", s.name).group(1)),
        reverse=True,
    )

    return keys


def get_all_keys(key_fmt: str, security: str) -> list[ECC.EccKey]:
    """Given a key_fmt string and security level, get all associated keys in order"""
    product = FirmwareUnderTest.product
    keys_ordered = get_sorted_key_names(key_fmt, product, security)

    keys = []
    for key_name in keys_ordered:
        key = _get_key_data(key_name)
        keys.append(key)

    return keys


def _key_path_to_cert_path(key_path: Path) -> Path:
    """Infer the corresponding signing certificate path from a signing key path."""

    cert_suffix = ".pct" if FirmwareUnderTest.product == PRODUCT_W3A_UXC else ".bin"

    cert_name = key_path.name.replace("-signing-key-", "-signing-cert-")
    if cert_name == key_path.name:
        raise ValueError(f"Cannot infer certificate path from key path: {key_path}")

    cert_name = cert_name.replace(".pub.pem", cert_suffix)
    cert_path = key_path.with_name(cert_name)
    if not cert_path.is_file():
        raise FileNotFoundError(f"Certificate not found: {cert_path}")

    return cert_path


def _get_verification_key_manager_from_path(key_path: Path):
    """Build the appropriate verification key manager for the current product."""
    from bitkey.key_manager import LocalKeyManager, PicocertKeyManager

    resolved_paths = ResolvedVerificationKeyPaths(public_key_path=str(key_path))

    if FirmwareUnderTest.product == PRODUCT_W3A_UXC:
        resolved_paths.cert_path = str(_key_path_to_cert_path(key_path))
        return PicocertKeyManager(resolved_paths)

    return LocalKeyManager(resolved_paths)


def get_verification_key_manager(key_fmt: str):
    """Resolve the current signing material and return the product-specific verifier."""

    product = FirmwareUnderTest.product
    security = FirmwareUnderTest.security
    key_path = get_key_latest_version(key_fmt, product, security)
    return _get_verification_key_manager_from_path(key_path)


def get_all_verification_key_managers(key_fmt: str, security: str) -> list:
    """Return all matching verification key managers in newest-first order."""

    product = FirmwareUnderTest.product
    key_paths = get_sorted_key_names(key_fmt, product, security)
    return [_get_verification_key_manager_from_path(key_path) for key_path in key_paths]


def get_patch_signing_key_bytes(key_fmt: str) -> bytes:
    """Get the hexlified patch signing key

    Raises:
        FileNotFoundError: If no keys matching the pattern are found
    """

    key = get_key(key_fmt)
    key_raw = key.export_key(format="raw")

    # drop the SEC#1 encoding byte
    return hexlify(key_raw)[2:]

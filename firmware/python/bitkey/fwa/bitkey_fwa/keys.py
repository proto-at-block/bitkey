import re
from binascii import hexlify
from pathlib import Path

from Crypto.PublicKey import ECC

from .fwut import FirmwareUnderTest, keys_parent_path


def _get_key_data(key_path: Path) -> ECC.EccKey:
    '''Get the key from the key_path'''

    with open(key_path, 'rt') as f:
        key = ECC.import_key(f.read())
        return key

def get_key_latest_version(key_fmt: str, product: str, security: str) -> Path:
    '''Get the latest key from where latest refers to the wildcard number in the key name'''

    key_path = keys_parent_path / f"{product}-{security}"

    if "app-signing-key" in key_fmt:
        signer_env = FirmwareUnderTest.signer_env
        if signer_env is None:
            signer_env = ""
        else:
            signer_env = "-" + signer_env

        key_basename = key_fmt.format(product, security, signer_env)
    else:
        key_basename = key_fmt.format(product, security)

    possible_keys = key_path.glob(key_basename)

    latest_key = max(possible_keys, key=lambda s: int(
        re.search(r'\.(\d+)\.', s.name).group(1)))

    return latest_key


def get_key(key_fmt: str) -> ECC.EccKey:
    '''Given a key_fmt string with a version glob, get the key'''
    product = FirmwareUnderTest.product
    security = FirmwareUnderTest.security

    latest_key = get_key_latest_version(key_fmt, product, security)

    return _get_key_data(latest_key)

def get_sorted_key_names(key_fmt: str, product: str, security: str) -> list[Path]:
    '''Get the names of all keys in the current signer env'''

    key_path = keys_parent_path / f"{product}-{security}"
    if "app-signing-key" in key_fmt:
        signer_env = FirmwareUnderTest.signer_env
        if signer_env is None:
            signer_env = ""
        else:
            signer_env = "-" + signer_env

        key_basename = key_fmt.format(product, security, signer_env)
    else:
        key_basename = key_fmt.format(product, security)

    possible_keys = key_path.glob(key_basename)

    keys = sorted(possible_keys, key=lambda s: int(
        re.search(r'\.(\d+)\.', s.name).group(1)), reverse=True)

    return keys

def get_all_keys(key_fmt: str, security: str) -> list[ECC.EccKey]:
    '''Given a key_fmt string and security level, get all associated keys in order'''
    product = FirmwareUnderTest.product
    keys_ordered = get_sorted_key_names(key_fmt, product, security)

    keys = []
    for key_name in keys_ordered:
            key = _get_key_data(key_name)
            keys.append(key)

    return keys

def get_patch_signing_key_bytes(key_fmt: str) -> bytes:
    """Get the hexlified patch signing key"""

    key = get_key(key_fmt)
    key_raw = key.export_key(format='raw')

    # drop the SEC#1 encoding byte
    return hexlify(key_raw)[2:]

import os
from abc import ABC, abstractmethod

from bitkey.picocert import PicocertV1
from bitkey.signer_utils import APP_CERT_ENV_VERSIONS, DEV_CERT_VERSIONS, PRODUCT_W3A_UXC, SIGNER_PRODUCTION
from Crypto.Hash import SHA256
from Crypto.PublicKey import ECC
from Crypto.Signature import DSS


class SigningKeys:
    def __init__(self, keys_dir, product, key_type, image_type):
        directory = self._key_directory(keys_dir, product, key_type)
        env_suffix = ""

        # Prod firmware uses APP_CERT_ENV_VERSIONS, dev firmware uses DEV_CERT_VERSIONS.
        if key_type == "prod":
            version = APP_CERT_ENV_VERSIONS[product][SIGNER_PRODUCTION]
        else:
            version = DEV_CERT_VERSIONS[product][image_type]
        version_suffix = f".{version}"
        key_type_in_filename = key_type

        # Prod-proto filenames are also different, they have no version and use "prod" files.
        if key_type == "prod-proto":
            key_type_in_filename = "prod"
            version_suffix = ""

        # W3A-UXC keys do not have an extra production suffix.
        if key_type == "prod" and product != PRODUCT_W3A_UXC:
            env_suffix = "-production"

        self.public_key_path = os.path.join(
            directory, f"{product}-{image_type}-signing-key-{key_type_in_filename}{env_suffix}{version_suffix}.pub.pem"
        )
        self.private_key_path = os.path.join(
            directory, f"{product}-{image_type}-signing-key-{key_type_in_filename}{env_suffix}{version_suffix}.priv.pem"
        )

        # UXC uses picocerts (.pct) instead of .bin certificates
        if product == PRODUCT_W3A_UXC:
            self.cert_path = os.path.join(
                directory, f"{product}-{image_type}-signing-cert-{key_type_in_filename}{env_suffix}{version_suffix}.pct"
            )
        else:
            self.cert_path = os.path.join(
                directory, f"{product}-{image_type}-signing-cert-{key_type_in_filename}{env_suffix}{version_suffix}.bin"
            )

        self.image_type = image_type
        self.key_type = key_type
        self.product = product

    def _key_directory(self, keys_dir, product, key_type):
        directory = os.path.join(keys_dir, product.lower() + "-" + key_type.lower())
        assert os.path.isdir(directory), f"Key directory not found: {directory}"
        return directory


class KeyManager(ABC):
    @abstractmethod
    def generate_signature(self, digest: SHA256.SHA256Hash) -> bytes:
        pass

    @abstractmethod
    def verify_signature(self, digest: SHA256.SHA256Hash, signature: bytes):
        pass

    @abstractmethod
    def get_signing_cert_path(self) -> str:
        pass


class LocalKeyManager(KeyManager):
    def __init__(self, signing_keys: SigningKeys):
        self._keys = signing_keys

    def generate_signature(self, digest: SHA256.SHA256Hash) -> bytes:
        with open(self._keys.private_key_path, "r") as f:
            signing_key = ECC.import_key(f.read())
        signature = DSS.new(signing_key, "deterministic-rfc6979").sign(digest)
        return signature

    def verify_signature(self, digest: SHA256.SHA256Hash, signature: bytes):
        with open(self._keys.public_key_path, "rb") as f:
            verification_key = ECC.import_key(f.read())
        try:
            DSS.new(verification_key, "deterministic-rfc6979").verify(digest, signature)
        except ValueError as e:
            raise e

    def get_signing_cert_path(self) -> str:
        return self._keys.cert_path


class PicocertKeyManager(KeyManager):
    def __init__(self, signing_keys: SigningKeys):
        self._keys = signing_keys

    def generate_signature(self, digest: SHA256.SHA256Hash) -> bytes:
        with open(self._keys.private_key_path, "r") as f:
            signing_key = ECC.import_key(f.read())
        signature = DSS.new(signing_key, "deterministic-rfc6979").sign(digest)
        return signature

    def verify_signature(self, digest: SHA256.SHA256Hash, signature: bytes):
        with open(self._keys.cert_path, "rb") as f:
            cert = PicocertV1.from_bytes(f.read())

        if not cert.verify_signature(digest, signature):
            raise ValueError("Signature verification failed")

    def get_signing_cert_path(self) -> str:
        return self._keys.cert_path

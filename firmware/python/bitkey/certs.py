from cryptography import x509
from binascii import unhexlify
import re


def _apply_armor(pem: str) -> str:
    return "\n".join([
        "-----BEGIN CERTIFICATE-----",
        pem,
        "-----END CERTIFICATE-----",
    ])


def _cert(cert) -> x509.Certificate:
    # Pass any type other than string through, and assume a string
    # is a PEM.
    if type(cert) != str:
        return cert
    try:
        return x509.load_pem_x509_certificate(bytes(cert, "utf-8"))
    except ValueError:
        pem = _apply_armor(cert)
        return x509.load_pem_x509_certificate(bytes(pem, "utf-8"))


def load_pem(pem: str):
    return x509.load_pem_x509_certificate(pem)


def load_der(der: bytes):
    return x509.load_der_x509_certificate(der)


def verify_cert_chain(cert_chain: list):
    """Verify a certificate chain. The first entry in the list should be
    the leaf certificate.

    Raises an exception if the chain does not verify.
    """

    assert len(cert_chain) >= 2

    for i in range(len(cert_chain)-1):
        cert_chain[i].verify_directly_issued_by(cert_chain[i+1])

    # Root is self-signed.
    cert_chain[-1].verify_directly_issued_by(cert_chain[-1])


def check_device_cert_is_for_block(cert) -> bool:
    cert = _cert(cert)

    subject = cert.subject

    BLOCK = "Block Inc"

    o = subject.get_attributes_for_oid(x509.NameOID.ORGANIZATION_NAME)[0].value
    cn = subject.get_attributes_for_oid(x509.NameOID.COMMON_NAME)[0].value

    return (o == BLOCK) and (BLOCK in cn)


def extract_pubkey_from_cert(cert) -> bytes:
    cert = _cert(cert)

    nums = cert.public_key().public_numbers()
    pubkey = nums.x.to_bytes(32, 'big') + nums.y.to_bytes(32, 'big')

    return pubkey


def read_se_serial(cert_der: bytes) -> bytes:
    cert = load_der(cert_der)

    cn = cert.subject.get_attributes_for_oid(x509.NameOID.COMMON_NAME)[0].value
    match = re.search(r'EUI:([0-9A-F]+)', cn)
    if match:
        eui = match.group(1)
        return unhexlify(eui)
    else:
        raise AssertionError("Could not find EUI in CN")


if __name__ == "__main__":
    unarmored_pem = "MIIB1DCCAXqgAwIBAgIUHhbdTTq/RzQSjetxtQwUEawyDIcwCgYIKoZIzj0EAwIwQTEWMBQGA1UEAwwNQmF0Y2ggWDAwMDAzODEaMBgGA1UECgwRU2lsaWNvbiBMYWJzIEluYy4xCzAJBgNVBAYTAlVTMCAXDTIzMDUxMTA2MDcwNFoYDzIxMjMwNTExMDYwNzA0WjBXMQswCQYDVQQGEwJVUzESMBAGA1UECgwJQmxvY2sgSW5jMTQwMgYDVQQDDCtCbG9jayBJbmMgRVVJOkE0NkRENEZGRkU4NDJBOUMgUzpTRTAgSUQ6TUNVMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaesn3I1zYV7HBfE3o9bFeYtH/fiL8i0bvVFUnB1gRtSXunIJ2uI4pqtZS69luno8RF5F3hjqvG05IweI38x6DqM4MDYwDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCBsAwFgYDVR0lAQH/BAwwCgYIKwYBBQUHAwIwCgYIKoZIzj0EAwIDSAAwRQIhAOooPnrIidG2btx6KVMi+RfwzIep3jx0vk1yRjK04NyoAiBxJ782AtdGDThszJkwpum+uMi9j3Be0jKzI68jVZLCNg=="
    check_device_cert_is_for_block(unarmored_pem)
    extract_pubkey_from_cert(unarmored_pem)

#!/usr/bin/env python3
"""
Verify a raw 64-byte ECDSA P-256 signature (r||s) using OpenSSL-compatible methods.

Usage:
    python3 verify_image_signature.py pub.pem file.bin file.sig
"""

import argparse
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, utils

def main(pub_path, data_path, sig_path):
    # Read inputs
    with open(pub_path, "rb") as f:
        pub_bytes = f.read()
    with open(data_path, "rb") as f:
        data = f.read()
    with open(sig_path, "rb") as f:
        sig = f.read()

    if len(sig) != 64:
        raise ValueError(f"Expected 64-byte raw signature (r||s), got {len(sig)} bytes")

    # Split raw signature into r and s
    r = int.from_bytes(sig[:32], "big")
    s = int.from_bytes(sig[32:], "big")

    # Encode to ASN.1 DER (what OpenSSL expects)
    der_sig = utils.encode_dss_signature(r, s)

    # Load public key
    pubkey = serialization.load_pem_public_key(pub_bytes)

    # Verify
    try:
        pubkey.verify(der_sig, data, ec.ECDSA(hashes.SHA256()))
        print("✅ Verified OK")
    except Exception as e:
        print("❌ Verification failed:", e)

if __name__ == "__main__":
    parser = argparse.ArgumentParser("verify-image-signature")
    parser.add_argument("pub_path")
    parser.add_argument("data_path")
    parser.add_argument("sig_path")
    parsed = parser.parse_args()

    main(parsed.pub_path, parsed.data_path, parsed.sig_path)

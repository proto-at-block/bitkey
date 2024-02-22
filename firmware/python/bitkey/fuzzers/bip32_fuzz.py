import bip32utils
import ecdsa
import hashlib

from bitkey.comms import NFCTransaction, WalletComms
from bitkey.wallet import Wallet
from bitkey.btc import BIP32Key

from base64 import b64encode
from binascii import unhexlify, hexlify

from subprocess import check_output
from time import sleep

import random

import shlex

# NOTE: This file implements a pretty hacky approach to on-target fuzzing. It works,
# but ideally we don't shell out like this.


def root_key():
    seed_hex = "000102030405060708090a0b0c0d0e0f000102030405060708090a0b0c0d0e0f"
    seed_bytes = bytes.fromhex(seed_hex)
    return bip32utils.BIP32Key.fromEntropy(seed_bytes)


def fuzz_test():
    # Derive the child key at path m/0'
    child_key = root_key().ChildKey(0 + bip32utils.BIP32_HARDEN)

    derivation_path = "m/0\'"
    i = 0
    while i < 32:
        child = random.randint(0, bip32utils.BIP32_HARDEN - 1)
        if bool(random.getrandbits(1)):
            derivation_path += f"/{child}\'"
            child_key = child_key.ChildKey(child + bip32utils.BIP32_HARDEN)
        else:
            derivation_path += f"/{child}"
            child_key = child_key.ChildKey(child)

        print(f"{i}: {derivation_path}")
        result = check_output(
            shlex.split(f"python -m python.bitkey.cli derive --network BITCOIN --path {derivation_path}", posix=False)).strip().decode('ascii')
        i += 1

        compare = hexlify(child_key.ExtendedKey(
            private=False, encoded=False)).decode('ascii')
        assert result == compare, f"{result} != {compare}"


def test():
    # Check a specific path
    derivation_path = "0'/756/769/264/512/718'/961'/638'/414/842/436'/796/907'/290/853'/136'/785'/835/119/800'/167/223/646/"

    result = check_output(
        shlex.split(f"python -m python.bitkey.cli derive --network BITCOIN --path {derivation_path}", posix=False)).strip().decode('ascii')

    path_components = derivation_path.split('/')
    current_key = root_key()

    for component in path_components:
        is_hardened = "'" in component
        index = int(component.rstrip("'"))
        current_key = current_key.ChildKey(
            index + bip32utils.BIP32_HARDEN if is_hardened else index)

    compare = hexlify(current_key.ExtendedKey(
        private=False, encoded=False)).decode('ascii')

    print("mine: " + result)
    print("theirs: " + compare)
    assert (result == compare)


def sign_verify(wallet: Wallet):
    path = []

    i = 0
    while i < 32:
        message = b"hello"
        digest = hashlib.sha256(message).digest()

        path.append(random.randint(0, bip32utils.BIP32_HARDEN - 1))

        rsp = wallet.derive(0, path).derive_rsp
        print(rsp)
        pubkey = BIP32Key(rsp.descriptor.bare_bip32_key)

        signature = wallet.derive_and_sign(
            digest, path).derive_and_sign_rsp.signature

        vk = ecdsa.VerifyingKey.from_string(
            pubkey.key_data, curve=ecdsa.SECP256k1, hashfunc=hashlib.sha256)

        assert vk.verify(signature, message)
        print(f'ok {i}')

        i += 1


if __name__ == "__main__":
    wallet = Wallet(WalletComms(NFCTransaction()))
    # fuzz_test()
    # test(wallet)
    sign_verify(wallet)

import click
from Crypto.PublicKey import ECC
from Crypto.Signature import DSS, eddsa
from Crypto.Hash import SHA256, SHA512

from binascii import unhexlify

cli = click.Group()

ALGS = {
    "secp256r1": (DSS, "fips-186-3", SHA256),
    "ed25519": (eddsa, "rfc8032", SHA512)
}


def bytes_to_public_key(pubkey: bytes, curve: str):
    if curve == "ed25519":
        return eddsa.import_public_key(pubkey)
    else:
        x_bytes = pubkey[:len(pubkey)//2]
        y_bytes = pubkey[len(pubkey)//2:]
        x = int.from_bytes(x_bytes, byteorder='big')
        y = int.from_bytes(y_bytes, byteorder='big')
        return ECC.construct(point_x=x, point_y=y, curve=curve)


def do_verify(curve, pubkey, signature, message=None, digest=None):
    if not ((message == None) ^ (digest == None)):
        print("Must supply one of message or digest")
        return

    pubkey = bytes_to_public_key(pubkey, curve)
    obj, mode, hasher = ALGS[curve]

    if message:
        message = bytes(message, encoding='ascii')
        inp = hasher.new(message)
    elif digest:
        inp = digest

    try:
        obj.new(pubkey, mode).verify(inp, signature)
        print("Verified.")
    except ValueError:
        print("Verification FAILED.")


@cli.command(help="Verify a signature; used for testing the Bitkey hardware.")
@click.option("--curve", type=click.Choice(ALGS.keys()), required=True)
@click.option("--pubkey", help="for ecdsa: uncompressed hex-encoded (x,y). no leading SEC1 prefix.", required=True)
@click.option("--signature", required=True)
@click.option("--message")
@click.option("--digest")
def verify(curve, pubkey, signature, message, digest):
    if digest:
        digest = unhexlify(digest)
    do_verify(curve, unhexlify(pubkey), unhexlify(
        signature), message, digest)


if __name__ == "__main__":
    cli()

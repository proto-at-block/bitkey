#!/usr/bin/env python

import click
import secrets

cli = click.Group()

KEY_FORMATS = ["hex", "code"]


def valid_c_variable_name(name):
    # Loose check.
    for c in ["-", " "]:
        assert c not in name, "Name is not a valid C variable"


def bytes_to_c_array(key_bytes):
    key_arr = key_bytes.hex(",").split(",")
    key_hex = [f"0x{byte}" for byte in key_arr]
    key_hex = ",".join(key_hex)
    return "{" + key_hex + "}"


def gen_code(name, key_bytes):
    valid_c_variable_name(name)
    name = name.lower()

    key_arr = bytes_to_c_array(key_bytes)

    length = len(key_bytes)
    length_var = f"{name.upper()}_LENGTH"
    return f"""
!!! Place in relevant keys.h
#define {length_var}_BYTES ({length}u)
#define {length_var}_BITS  ({length*8}u)

extern uint8_t {name}[{length_var}_BYTES];

!!! Place in relevant keys.c

uint8_t {name}[{length_var}_BYTES] = {key_arr};
"""


@cli.command(help="Generate an N bit symmetric key")
@click.option('--output-format', required=True,
              type=click.Choice(KEY_FORMATS,
                                case_sensitive=False))
@click.option("--bits", required=True, type=click.INT)
@click.option("--name", required=False, type=click.STRING, help="Name of key as a C variable")
def symmetric(output_format, bits, name):
    key = secrets.token_bytes(bits // 8)

    if output_format == "hex":
        click.echo(key.hex())
    elif output_format == "code":
        if name == None:
            name = click.prompt("Name of key")
        click.echo(gen_code(name, key))


@cli.command(help="Convert a hex string to a C-style array")
@click.argument("key")
def hex2arr(key):
    click.echo(bytes_to_c_array(bytes.fromhex(key)))


if __name__ == "__main__":
    cli()

import time
import click
import pathlib
import tempfile
import subprocess

from . import bitlog

from tqdm import tqdm
from binascii import hexlify
from google.protobuf.json_format import MessageToJson

from .wallet import Wallet
from bitkey_proto import wallet_pb2 as wallet_pb
from bitkey_proto import mfgtest_pb2 as mfgtest_pb
from bitkey_proto import ops_keybundle_pb2 as ops_keybundle
from bitkey_proto import ops_keys_pb2 as ops_keys
from .comms import WalletComms, NFCTransaction, ShellTransaction
from . import coredump as coredump_api
from .fwup import FirmwareUpdater
from .btc import DerivationPath, Sha256Hash
from binascii import unhexlify


def print_proto(proto):
    """Print a proto as JSON, including fields which are set to their default
    value (e.g. false for boolean fields).
    """
    click.echo(MessageToJson(proto, including_default_value_fields=True))


@click.group()
@click.option('--debug', type=click.BOOL, default=False, is_flag=True, help="Print raw TX/RX bytes")
@click.option('--serial-port', type=click.STRING, help="Serial port to use for wca transfer")
@click.pass_context
def cli(ctx, debug, serial_port):
    if serial_port != None:
        ctx.obj = ctx.with_resource(Wallet(comms=WalletComms(
            ShellTransaction(port=serial_port), debug=debug)))
    else:
        ctx.obj = ctx.with_resource(
            Wallet(comms=WalletComms(NFCTransaction(), debug=debug)))


@cli.command()
@click.option('--index', type=click.INT, required=False)
@click.pass_context
def start_fingerprint_enrollment(ctx, index=0):
    result = ctx.obj.start_fingerprint_enrollment(index)
    if result.start_fingerprint_enrollment_rsp.rsp_status == result.start_fingerprint_enrollment_rsp.start_fingerprint_enrollment_rsp_status.SUCCESS:
        click.echo("Fingerprint enrollment started")
    else:
        click.echo(result)


@cli.command()
@click.pass_context
def get_fingerprint_enrollment_status(ctx):
    print_proto(ctx.obj.get_fingerprint_enrollment_status())


@cli.command()
@click.pass_context
def query_authentication(ctx):
    print_proto(ctx.obj.query_authentication())


@cli.command()
@click.option('--csek', type=click.STRING)
@click.option('--legacy', type=click.BOOL)
@click.pass_context
def seal_csek(ctx, csek, legacy):
    print_proto(ctx.obj.seal_csek(csek, legacy))


@cli.command()
@click.option('--encrypted-csek', type=click.STRING)
@click.option('--iv', type=click.STRING)
@click.option('--tag', type=click.STRING)
@click.pass_context
def unseal_csek(ctx, encrypted_csek, iv, tag):
    print_proto(ctx.obj.unseal_csek(encrypted_csek, iv, tag))


@cli.command()
@click.option('--sighash', type=click.STRING)
@click.option('--change', type=click.INT)
@click.option('--address-index', type=click.INT)
@click.pass_context
def sign_txn(ctx, sighash, change, address_index):
    print_proto(ctx.obj.sign_txn(sighash, change, address_index))


@cli.command()
@click.pass_context
def wipe_state(ctx):
    print_proto(ctx.obj.wipe_state())


@cli.command()
@click.pass_context
def mfgtest_fingerprint_calibrate(ctx):
    print_proto(ctx.obj.mfgtest_fingerprint_calibrate())


@cli.command()
@click.pass_context
def mfgtest_fingerprint_image_run(ctx):
    progress = None

    def update(total, sent):
        nonlocal progress
        if progress is None:
            progress = tqdm(total=total)
        else:
            progress.update(sent)

    image = ctx.obj.mfgtest_fingerprint_image_capture_run(
        update_callback=update)
    progress.close()
    click.echo(hexlify(bytearray(image)))


@cli.command()
@click.pass_context
def mfgtest_fingerprint_selftest(ctx):
    ctx.obj.mfgtest_fingerprint_selftest_start()
    time.sleep(11)  # Selftest is slow
    print_proto(ctx.obj.mfgtest_fingerprint_selftest_get_result())


@cli.command()
@click.pass_context
def mfgtest_fingerprint_security_mode(ctx):
    print_proto(ctx.obj.mfgtest_fingerprint_security_mode())


@cli.command()
@click.option('--real', type=click.BOOL, default=False, help="Provision the sensor. Irreversible!")
@click.pass_context
def mfgtest_fingerprint_security_enable(ctx, real):
    print_proto(ctx.obj.mfgtest_fingerprint_security_enable(real))


@cli.command()
@click.pass_context
def mfgtest_fingerprint_security_test(ctx):
    print_proto(ctx.obj.mfgtest_fingerprint_security_test())


@cli.command()
@click.pass_context
def mfgtest_fingerprint_image_analysis(ctx):
    print_proto(ctx.obj.mfgtest_fingerprint_image_analysis())


@cli.command()
@click.option('--serial', type=click.STRING, help="hex-encoded serial number")
@click.option('--which', type=click.Choice(mfgtest_pb.serial_type.keys()))
@click.pass_context
def mfgtest_serial_write(ctx, serial, which):
    print_proto(ctx.obj.mfgtest_serial_write(
        serial, mfgtest_pb.serial_type.Value(which)))


@cli.command()
@click.option('--variant', type=click.Choice(mfgtest_pb.battery_variant.keys()))
@click.pass_context
def mfgtest_battery_variant(ctx, variant):
    variant_value = mfgtest_pb.battery_variant.Value(variant)
    print_proto(ctx.obj.mfgtest_battery_variant(variant_value))


@cli.command()
@click.pass_context
def device_id(ctx):
    print_proto(ctx.obj.device_id())


@cli.command()
@click.pass_context
def metadata(ctx):
    print_proto(ctx.obj.metadata())


@cli.command()
@click.pass_context
def reset(ctx):
    ctx.obj.reset()


@cli.command()
@click.pass_context
def fuel(ctx):
    fuel = ctx.obj.fuel().fuel_rsp
    if fuel.valid:
        click.echo(f"Charge: {fuel.repsoc / 1000}%")
        click.echo(f"vCell: {fuel.vcell} mV")
    else:
        click.echo("Fuel gauge reporting is NOT valid")


@cli.command()
@click.option('--action', type=click.Choice(["READ", "SET", "CLEAR"]), required=True)
@click.option('--port', type=click.Choice(["PORT_A", "PORT_B", "PORT_C", "PORT_D"]), required=True)
@click.option('--pin', type=click.INT, required=True)
@click.pass_context
def mfgtest_gpio(ctx, action, port, pin):
    print_proto(ctx.obj.mfgtest_gpio(
        mfgtest_pb.mfgtest_gpio_cmd.mfgtest_gpio_action.Value(action),
        mfgtest_pb.mfgtest_gpio_cmd.mfgtest_gpio_port.Value(port),
        pin))


@cli.command()
@click.option("--out", required=False, type=click.Path(exists=False, path_type=pathlib.Path), help="Output directory")
@click.option('--get-all', is_flag=True, help="Get all coredumps")
@click.option('--operation', type=click.Choice(wallet_pb.coredump_get_cmd.coredump_get_type.keys()))
@click.pass_context
def coredump(ctx, out: pathlib.Path, get_all: bool, operation: str):
    wallet = ctx.obj

    if operation == 'COUNT':
        click.echo(coredump_api.count(wallet))
    elif get_all:
        coredump_api.fetch_all(wallet, out)
    else:
        coredump_api.fetch_one(wallet, out)


@cli.command()
@click.pass_context
def events(ctx):
    wallet = ctx.obj
    events = []
    while True:
        result = wallet.events().events_get_rsp
        assert result.rsp_status == result.events_get_rsp_status.SUCCESS
        e = result.fragment
        events.extend(e.data)
        if e.remaining_size == 0:
            break

    for e in bitlog.parse_events(bytes(events)):
        print(e)


@cli.command()
@click.pass_context
def stress(ctx):
    count = 0
    while True:
        _ = ctx.obj.metadata()
        count += 1
        print(count)


@cli.command()
@click.pass_context
def feature_flags_get(ctx):
    wallet = ctx.obj
    flags = wallet.feature_flags_get()
    print_proto(flags)


@cli.command()
@click.pass_context
@click.option('--flag', type=(click.Choice(wallet_pb.feature_flag.keys()), click.BOOL), multiple=True, required=True)
def feature_flags_set(ctx, flag):
    wallet = ctx.obj

    flags = []
    for f in flag:
        flags.append(wallet_pb.feature_flag_cfg(flag=f[0], enabled=f[1]))
    result = wallet.feature_flags_set(flags)

    print_proto(result)


@cli.command()
@click.pass_context
def telemetry_id(ctx):
    wallet = ctx.obj
    print_proto(wallet.telemetry_id())


@cli.command()
@click.pass_context
def secinfo(ctx):
    wallet = ctx.obj
    print_proto(wallet.secinfo())


@cli.command()
@click.option('--kind', type=click.Choice(wallet_pb.cert_get_cmd.cert_type.keys()))
@click.option('--verbose', '-v', is_flag=True)
@click.pass_context
def cert_get(ctx, kind, verbose):
    wallet = ctx.obj
    result = wallet.cert_get(kind)
    print_proto(result)

    if verbose:
        with tempfile.NamedTemporaryFile() as f:
            f.write(result.cert_get_rsp.cert)
            f.flush()
            click.echo(subprocess.check_output(
                f"openssl x509 -inform der -in {f.name} -noout -text", shell=True))


@cli.command()
@click.pass_context
def pubkeys_get(ctx):
    wallet = ctx.obj
    print_proto(wallet.pubkeys_get())


@cli.command()
@click.option('--kind', type=click.Choice(wallet_pb.pubkey_get_cmd.pubkey_type.keys()))
@click.pass_context
def pubkey_get(ctx, kind):
    wallet = ctx.obj
    print_proto(wallet.pubkey_get(kind))


@cli.command()
@click.pass_context
def fingerprint_settings_get(ctx):
    wallet = ctx.obj
    print_proto(wallet.fingerprint_settings_get())


@cli.command()
@click.pass_context
def cap_touch_cal(ctx):
    wallet = ctx.obj
    print_proto(wallet.cap_touch_cal())


@cli.command()
@click.pass_context
def device_info(ctx):
    wallet = ctx.obj
    print_proto(wallet.device_info())


@cli.command()
@click.pass_context
def lock_device(ctx):
    wallet = ctx.obj
    print_proto(wallet.lock_device())


@cli.command()
@click.option("--bundle", required=True, type=click.Path(exists=True, path_type=pathlib.Path), help="Bundle path")
@click.option("--timeout", required=False, type=click.INT, help="Timeout in seconds")
@click.pass_context
def fwup_local(ctx, bundle, timeout=None):
    FirmwareUpdater(ctx.obj).fwup_local(bundle, timeout=timeout)


@cli.command()
@click.pass_context
def fwup(ctx):
    FirmwareUpdater(ctx.obj).fwup()


@click.option('--network', type=click.Choice(ops_keybundle.btc_network.keys()), required=True)
@click.option('--path', type=DerivationPath, required=True)
@click.pass_context
def derive(ctx, network, path):
    wallet = ctx.obj
    res = wallet.derive(
        ops_keybundle.btc_network.Value(network), path.path)
    print(hexlify(res.derive_rsp.descriptor.bare_bip32_key).decode('ascii'))


@cli.command()
@click.option('--digest', type=Sha256Hash, required=True)
@click.option('--path', type=DerivationPath, required=True)
@click.pass_context
def derive_and_sign(ctx, digest, path):
    wallet = ctx.obj
    print_proto(wallet.derive_and_sign(digest.bytes, path.path))


@cli.command
@click.option('--secret', type=click.STRING, required=True)
@click.pass_context
def unlock_secret(ctx, secret):
    wallet = ctx.obj
    print_proto(wallet.unlock_secret(secret))


@cli.command
@click.option('--secret', type=click.STRING, required=True)
@click.pass_context
def provision_unlock_secret(ctx, secret):
    wallet = ctx.obj
    print_proto(wallet.provision_unlock_secret(secret))


@cli.command()
@click.option('--curve', type=click.Choice(ops_keys.curve.keys()), required=True)
@click.option('--label', type=click.STRING, required=True)
@click.pass_context
def derive_public_key(ctx, curve, label):
    wallet = ctx.obj
    print_proto(wallet.derive_public_key(
        ops_keys.curve.Value(curve), label))


@cli.command()
@click.option('--digest', type=Sha256Hash, required=True)
@click.option('--curve', type=click.Choice(ops_keys.curve.keys()), required=True)
@click.option('--label', type=click.STRING, required=True)
@click.pass_context
def derive_public_key_and_sign(ctx, digest, curve, label):
    wallet = ctx.obj
    print_proto(wallet.derive_public_key_and_sign(curve, label, digest.bytes))


@cli.command()
@click.option('--response', type=click.Choice(wallet_pb.configure_unlock_limit_response_cmd.response_cfg.keys()), required=True)
@click.pass_context
def configure_unlock_limit_response(ctx, response):
    wallet = ctx.obj
    print_proto(wallet.configure_unlock_limit_response(response))


@cli.command()
@click.option('--index', type=click.INT, required=True)
@click.pass_context
def delete_fingerprint(ctx, index):
    wallet = ctx.obj
    print_proto(wallet.delete_fingerprint(index))


@cli.command()
@click.pass_context
def get_enrolled_fingerprints(ctx):
    wallet = ctx.obj
    print_proto(wallet.get_enrolled_fingerprints())


if __name__ == "__main__":
    cli()

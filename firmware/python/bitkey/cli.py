import functools
import pathlib
import subprocess
import sys
import tempfile
import time
from binascii import hexlify
from typing import Any, Callable, List

import click
from bitkey_proto import mfgtest_pb2 as mfgtest_pb
from bitkey_proto import ops_keybundle_pb2 as ops_keybundle
from bitkey_proto import ops_keys_pb2 as ops_keys
from bitkey_proto import wallet_pb2 as wallet_pb
from google.protobuf.json_format import MessageToJson
from tqdm import tqdm

from . import bitlog, charger
from . import coredump as coredump_api
from .btc import DerivationPath, Sha256Hash
from .comms import WalletComms, NFCTransaction, ShellTransaction
from .fwup import FirmwareUpdater
from .wallet import Wallet


def print_proto(proto):
    """Print a proto as JSON, including fields which are set to their default
    value (e.g. false for boolean fields).
    """
    click.echo(MessageToJson(proto, including_default_value_fields=True))


def add_mcu_option(callback: Callable) -> Callable:
    """Wraps a click function with MCU target options.

    :param callback: the function to wrap.
    :returns: the wrapped function.
    """

    @click.option(
        '-m',
        '--mcu',
        required=False,
        default='EFR32',
        type=click.Choice(['EFR32', 'STM32U5'], case_sensitive=False),
        help='Optional target MCU'
    )
    @functools.wraps(callback)
    def _add_mcu_options(mcu: str, *args, **kwargs) -> Any:
        return callback(mcu=mcu, *args, **kwargs)

    return _add_mcu_options


@click.group()
@click.option('--debug', type=click.BOOL, default=False, is_flag=True, help="Print raw TX/RX bytes")
@click.option('--serial-port', type=click.STRING, help="Serial port to use for wca transfer")
@click.option('--nfc-port', type=click.STRING, help="NFC reader USB port ID (e.g. '3-6.4.4.4.2', 'usb:<bus>:<device>', 'usb:<vid>:<pid>')", default="usb")
@click.option(
    '-p',
    '--product',
    default='w1',
    type=click.Choice(['W1', 'W3'], case_sensitive=False),
    help='Optional product name'
)
@click.option('--timeout', type=click.FLOAT, default=None, help="Global timeout in seconds for device operations (default: unlimited)")
@click.option('--transceive-timeout', type=click.FLOAT, default=None, help="Per-transceive timeout in seconds (default: 0.25)")
@click.pass_context
def cli(ctx: click.Context, debug: bool, serial_port: str, nfc_port: str, product: str, timeout: float, transceive_timeout: float) -> None:
    help_invoked: bool = any(opt in sys.argv[1:] for opt in ['-h', '--help'])
    if help_invoked:
        # Skip binding the context object if `--help` is passed for a
        # sub-command.
        return

    if serial_port is not None:
        ctx.obj = ctx.with_resource(Wallet(comms=WalletComms(
            ShellTransaction(port=serial_port), debug=debug), product=product))
    else:
        ctx.obj = ctx.with_resource(
            Wallet(comms=WalletComms(NFCTransaction(usbstr=nfc_port, timeout=timeout, transceive_timeout=transceive_timeout), debug=debug), product=product))


@cli.command()
@click.option('--index', type=click.INT, required=False)
@click.option('--label', type=click.STRING, required=False, default="")
@click.pass_context
def start_fingerprint_enrollment(ctx, index=0, label=""):
    result = ctx.obj.start_fingerprint_enrollment(index, label)
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
@click.argument(
    "instance",
    nargs=1,
    type=click.Choice(
        mfgtest_pb.mfgtest_spi_loopback_cmd.mfgtest_spi_loopback_cmd_instance.keys(),
        case_sensitive=False
    )
)
@click.argument("data", nargs=-1, metavar="BYTE", type=str)
@click.pass_context
def mfgtest_spi_loopback(ctx: click.Context, instance: str, data: List[str]) -> None:
    """Performs a SPI loopback test against the specified instance."""
    converted_data: List[int] = []
    default_data: List[str] = ["0xAA", "0x55", "0xFF"]
    for byte in (data or default_data):
        try:
            value = int(byte, 16) if byte.lower(
            ).startswith("0x") else int(byte)
            if not 0 <= value <= 255:
                raise click.BadParameter(
                    f"Byte value {value} is out of range. Must be 0-255.")
            converted_data.append(value)
        except ValueError:
            raise click.BadParameter(
                f"Invalid byte value '{byte}'. Please provide bytes as hex (e.g. 0xAA) or decimal (e.g. 170)."
            )

    rsp = ctx.obj.mfgtest_spi_loopback(
        mfgtest_pb.mfgtest_spi_loopback_cmd.mfgtest_spi_loopback_cmd_instance.Value(
            instance),
        converted_data
    )

    if rsp.rsp_status == rsp.mfgtest_spi_loopback_rsp_status.FAIL:
        # In testing, we have observed that the validation on-chip sometimes fails
        # due to the most-significant bit of the first byte being missing in the received data.
        # To workaround this, we compare the received data (with the first bit of the first byte removed)
        # against the expected data to see if they match.
        expected = "".join(format(b, "#010b" if i == 0 else "08b")
                           for i, b in enumerate(converted_data))
        actual = ""
        for i in range(0, len(rsp.data)):
            byte_str = format(rsp.data[i], "#010b")
            actual += (byte_str[:2] + byte_str[3:]) if i == 0 else byte_str[2:]

        if (converted_data and len(rsp.data) == len(converted_data) and expected.startswith(actual)):
            rsp.rsp_status = rsp.mfgtest_spi_loopback_rsp_status.SUCCESS
            rsp.data = bytes(converted_data)

    print_proto(rsp)


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
def mfgtest_runin_get_data(ctx):
    print_proto(ctx.obj.mfgtest_runin_get_data())


@cli.command()
@click.argument("test_type", type=click.Choice(["a", "b"], case_sensitive=False))
@click.option("-t", "--timeout", type=int, default=5000, help="Timeout (ms) for card detection.")
@click.option("-d", "--delay", type=int, default=1000, help="Delay (ms) before starting the tap test.")
@click.pass_context
def mfgtest_tap_test_start(ctx: click.Context, test_type: str, delay: int, timeout: int) -> None:
    """Starts a tap test over NFC. Test is started after the specified delay."""
    nfc_test = getattr(
        mfgtest_pb.mfgtest_nfc_loopback_test_type, f"NFC_LOOPBACK_TEST_{test_type.upper()}", None)
    if nfc_test is None:
        click.secho(f"Invalid test specified: {test_type}", fg="red")
    else:
        res = ctx.obj.mfgtest_tap_test_start(
            nfc_test=nfc_test, delay=delay, timeout=timeout)
        status = res.mfgtest_nfc_loopback_rsp.rsp_status
        if status == res.mfgtest_nfc_loopback_rsp.mfgtest_nfc_loopback_rsp_status.SUCCESS:
            click.secho(f"Test started successfully.", fg="green")
        else:
            click.secho(f"Failed to start test.", fg="red")


@cli.command()
@click.pass_context
def mfgtest_tap_test_result(ctx: click.Context) -> None:
    """Prints the result of the last tap test."""
    res = ctx.obj.mfgtest_tap_test_result()
    status = res.mfgtest_nfc_loopback_rsp.rsp_status
    if status == res.mfgtest_nfc_loopback_rsp.mfgtest_nfc_loopback_rsp_status.SUCCESS:
        click.secho(f"Test passed.", fg="green")
    else:
        click.secho("Test failed.", fg="red")


@cli.command()
@click.pass_context
def device_id(ctx):
    print_proto(ctx.obj.device_id())


@cli.command()
@add_mcu_option
@click.pass_context
def metadata(ctx: click.Context, mcu: str) -> None:
    """Requests metadata from the target MCU."""
    print_proto(ctx.obj.metadata(mcu=mcu))


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
@add_mcu_option
@click.pass_context
def mfgtest_gpio(ctx: click.Context, action: str, port: str, pin: int, mcu: str) -> None:
    """Read/set/clear a target GPIO."""
    print_proto(ctx.obj.mfgtest_gpio(
        mfgtest_pb.mfgtest_gpio_cmd.mfgtest_gpio_action.Value(action),
        mfgtest_pb.mfgtest_gpio_cmd.mfgtest_gpio_port.Value(port),
        pin,
        mcu=mcu))


@cli.command()
@click.pass_context
def mfgtest_button_get_events(ctx):
    """Get button events from the device."""
    response = ctx.obj.mfgtest_button(
        mfgtest_pb.mfgtest_button_cmd.GET_EVENTS)

    if response.mfgtest_button_rsp.rsp_status == mfgtest_pb.mfgtest_button_rsp.SUCCESS:
        events = response.mfgtest_button_rsp.events
        click.echo(f"Retrieved {len(events)} button events:")
        for i, event in enumerate(events):
            button_name = mfgtest_pb.mfgtest_button_event.button_id.Name(
                event.button)
            event_type = mfgtest_pb.mfgtest_button_event.event_type.Name(
                event.type)
            click.echo(
                f"  [{i}] {button_name} {event_type} @ {event.timestamp_ms}ms (duration: {event.duration_ms}ms)")
    else:
        click.echo(
            f"Error: {mfgtest_pb.mfgtest_button_rsp.mfgtest_button_rsp_status.Name(response.mfgtest_button_rsp.rsp_status)}")


@cli.command()
@click.pass_context
def mfgtest_button_clear_events(ctx):
    """Clear all button events from the device buffer."""
    response = ctx.obj.mfgtest_button(
        mfgtest_pb.mfgtest_button_cmd.CLEAR_EVENTS)

    if response.mfgtest_button_rsp.rsp_status == mfgtest_pb.mfgtest_button_rsp.SUCCESS:
        click.echo("Button events cleared successfully")
    else:
        click.echo(
            f"Error: {mfgtest_pb.mfgtest_button_rsp.mfgtest_button_rsp_status.Name(response.mfgtest_button_rsp.rsp_status)}")


@cli.command()
@click.option('--enable/--disable', required=True, help='Enable or disable button bypass mode')
@click.pass_context
def mfgtest_button_bypass(ctx, enable):
    """Enable or disable button bypass mode (prevents UI from consuming button events)."""
    response = ctx.obj.mfgtest_button(
        mfgtest_pb.mfgtest_button_cmd.SET_UI_BYPASS,
        bypass_enabled=enable)

    if response.mfgtest_button_rsp.rsp_status == mfgtest_pb.mfgtest_button_rsp.SUCCESS:
        status = "enabled" if enable else "disabled"
        click.echo(f"Button bypass mode {status}")
    else:
        click.echo(
            f"Error: {mfgtest_pb.mfgtest_button_rsp.mfgtest_button_rsp_status.Name(response.mfgtest_button_rsp.rsp_status)}")


@cli.command()
@click.argument('mode')
@click.option('--brightness', '-b', type=int, default=None,
              help='Display brightness percent (1-100). If not specified, brightness is unchanged.')
@click.pass_context
def mfgtest_show_screen(ctx, mode, brightness):
    """Show a manufacturing test screen on the device display.

    MODE can be a color name (RED, GREEN, BLUE, WHITE, BLACK, GRAY), a hex RGB color
    (e.g., 808080, #FF00FF), or a special mode (BURNIN, COLOR_BARS, SCROLLING_H, EXIT).

    Examples:
        bitkey-cli mfgtest-show-screen RED
        bitkey-cli mfgtest-show-screen RED --brightness 50
        bitkey-cli mfgtest-show-screen 808080 -b 100
    """
    # Validate brightness if provided
    if brightness is not None and (brightness < 1 or brightness > 100):
        click.echo("Error: brightness must be 1-100")
        return

    # Map color names to hex values
    color_map = {
        'RED': 0xFF0000,
        'GREEN': 0x00FF00,
        'BLUE': 0x0000FF,
        'WHITE': 0xFFFFFF,
        'BLACK': 0x000000,
        'GRAY': 0x808080,
    }

    # Map special modes to proto enums
    special_mode_map = {
        'EXIT': mfgtest_pb.mfgtest_show_screen_cmd.EXIT,
        'BURNIN': mfgtest_pb.mfgtest_show_screen_cmd.BURNIN_GRID,
        'COLOR_BARS': mfgtest_pb.mfgtest_show_screen_cmd.COLOR_BARS,
        'SCROLLING_H': mfgtest_pb.mfgtest_show_screen_cmd.SCROLLING_H,
    }

    mode_upper = mode.upper()
    custom_rgb = None

    if mode_upper in special_mode_map:
        test_mode = special_mode_map[mode_upper]
    elif mode_upper in color_map:
        test_mode = mfgtest_pb.mfgtest_show_screen_cmd.CUSTOM_COLOR
        custom_rgb = color_map[mode_upper]
    else:
        # Try to parse as hex RGB color (6 hex digits, optional # prefix)
        hex_str = mode_upper.lstrip('#')
        if len(hex_str) != 6:
            click.echo(
                f"Error: '{mode}' must be exactly 6 hex digits (got {len(hex_str)})")
            return
        try:
            custom_rgb = int(hex_str, 16)
            test_mode = mfgtest_pb.mfgtest_show_screen_cmd.CUSTOM_COLOR
        except ValueError:
            click.echo(f"Error: '{mode}' contains invalid hex characters")
            return

    response = ctx.obj.mfgtest_show_screen(test_mode, custom_rgb, brightness)

    if response.mfgtest_show_screen_rsp.rsp_status == mfgtest_pb.mfgtest_show_screen_rsp.SUCCESS:
        if test_mode == mfgtest_pb.mfgtest_show_screen_cmd.EXIT:
            click.echo("Exited mfg test screen")
        else:
            msg = f"Showing {mode_upper} screen"
            if brightness is not None:
                msg += f" at brightness {brightness}"
            click.echo(msg)
    else:
        click.echo(
            f"Error: {mfgtest_pb.mfgtest_show_screen_rsp.mfgtest_show_screen_rsp_status.Name(response.mfgtest_show_screen_rsp.rsp_status)}")


@cli.command()
@click.pass_context
def mfgtest_board_id(ctx: click.Context) -> None:
    """Reads the device board identifier."""
    board_id = ctx.obj.mfgtest_board_id()
    if board_id is not None:
        click.echo(f"Board ID: 0x{board_id:02X}")
    else:
        click.secho("Failed to read board ID", fg="red")


@cli.command()
@click.option("-t", "--timeout", default=60, help="Timeout (seconds) for touch test")
@click.pass_context
def mfgtest_touch_test_start(ctx: click.Context, timeout: int) -> None:
    """Starts a touch test."""
    if not ctx.obj.mfgtest_touch_test_start(timeout=timeout):
        click.secho("Failed to start touch test.", fg="red")
    else:
        click.secho(
            f"Touch test started successfully. Timeout: {timeout}s", fg="green")


@cli.command()
@click.pass_context
def mfgtest_touch_test_finish(ctx: click.Context) -> None:
    """Retrieves the results of a touch test and ends the test."""
    result = ctx.obj.mfgtest_touch_test_finish()

    # Check status using the proto status enum
    if result.rsp_status == result.TIMED_OUT:
        click.secho("Touch test TIMED OUT", fg="yellow")
        click.secho(f"boxes_remaining: {result.boxes_remaining}", fg="yellow")
    elif result.rsp_status == result.SUCCESS:
        click.secho(
            f"boxes_remaining: {result.boxes_remaining} (PASS)", fg="green")
    else:
        # FAILED or ERROR
        click.secho(
            f"boxes_remaining: {result.boxes_remaining} (FAIL)", fg="red")


@cli.command()
@click.pass_context
def mfgtest_charger_info(ctx: click.Context) -> None:
    """Reads status information from the charger."""
    proto = ctx.obj.mfgtest_charger_info()
    if proto:
        print_proto(proto)
    else:
        click.secho("Failed to retrieve charger info", fg="red")


@cli.command()
@click.pass_context
def mfgtest_charger_registers(ctx: click.Context) -> None:
    """Reads the registers from the charger."""
    proto = ctx.obj.mfgtest_charger_registers()
    if not proto:
        click.secho("Failed to read registers", fg="red")
        return

    for reg in proto.registers:
        offset: int = reg.offset
        name: str = ""
        if proto.charger_id == mfgtest_pb.mfgtest_charger_rsp.MAX77734:
            if offset in charger.MAX77734Regs:
                name = f"MAX77734_REG_{charger.MAX77734Regs(offset).name}"
        if not name:
            name = f"Unknown (0x{offset:02X})"
        click.echo(f"{name}: 0x{reg.value:02X}")


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
@add_mcu_option
@click.option("--bundle", required=True, type=click.Path(exists=True, path_type=pathlib.Path), help="Bundle path")
@click.option("--timeout", required=False, type=click.INT, help="Timeout in seconds")
@click.pass_context
def fwup_local(ctx, bundle, mcu: str, timeout=None) -> None:
    FirmwareUpdater(ctx.obj).fwup_local(bundle, timeout=timeout, mcu=mcu)


@cli.command()
@add_mcu_option
@click.argument("image", required=True, type=click.Path(exists=True))
@click.argument("signature", required=True, type=click.Path(exists=True))
@click.pass_context
def fwup(ctx: click.Context, image: click.Path, signature: click.Path, mcu: str) -> None:
    """Performs a normal firmware update using a specific firmware asset."""
    FirmwareUpdater(ctx.obj).fwup(mcu=mcu, image=image,
                                  signature=signature, params=ctx.obj.fwup_params(mcu))


@cli.command()
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
@click.option('--async-sign/--no-async-sign', type=bool, required=False, default=False)
@click.pass_context
def derive_and_sign(ctx, digest, path, async_sign):
    wallet = ctx.obj
    print_proto(wallet.derive_and_sign(digest.bytes, path.path, async_sign))


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


@cli.command()
@click.pass_context
def get_unlock_method(ctx):
    wallet = ctx.obj
    print_proto(wallet.get_unlock_method())


@cli.command()
@click.option('--index', type=click.INT, required=True)
@click.option('--label', type=click.STRING, required=True)
@click.pass_context
def set_fingerprint_label(ctx, index, label):
    wallet = ctx.obj
    print_proto(wallet.set_fingerprint_label(index, label))


@cli.command()
@click.pass_context
def cancel_fingerprint_enrollment(ctx):
    wallet = ctx.obj
    print_proto(wallet.cancel_fingerprint_enrollment())


if __name__ == "__main__":
    cli()

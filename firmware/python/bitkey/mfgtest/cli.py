from . import platform_config
import click

from .platform_config import PlatformConfig, Gpio, Port, DeviceIdentifiers, SerialNumber, DevInfo, ChipId, BoardId, ChargerInfo, FuelStatus
from ..shell import Shell

HW_REV = "hardware_revision"
SERIAL_PORT_NAME = "serial_port_name"


def _get_config(ctx):
    return platform_config.CONFIG_MAP[ctx.obj[HW_REV]]


# TODO: Remove this once we have a settled-upon set of identifiers so that we can introspect.
@click.group()
@click.option('--hardware-revision', type=click.STRING, default=platform_config.W1A_PROTO0)
@click.option('--serial-port-name', type=click.STRING, default=None)
@click.pass_context
def cli(ctx, hardware_revision, serial_port_name):
    """W1 Controller CLI.

    The global argument `hardware-revision` describes a product (e.g. w1a) and build (e.g. proto0) combination.
    This flag must be set if using a command that relies on hardware configuration that
    changes from build-to-build.
    """
    ctx.ensure_object(dict)
    if hardware_revision not in platform_config.CONFIG_MAP.keys():
        raise click.BadParameter(
            f"Unknown hardware revision {hardware_revision}. Choices are: {' '.join(list(platform_config.CONFIG_MAP.keys()))}")
    ctx.obj[HW_REV] = hardware_revision
    ctx.obj[SERIAL_PORT_NAME] = serial_port_name


# TODO: janky text parsing will be replaced by protobufs, but external API will be unchanged.
class W1Controller:
    def __init__(self, config: PlatformConfig, serial_port_name: str = None):
        self.config = config
        self.shell = Shell(serial_port_name=serial_port_name)

    def toggle_gpio(self, gpio: Gpio, state: bool):
        v = "1" if state else "0"
        self.shell.command(f"gpio {gpio.port}.{gpio.pin} {v}")

    def toggle_led(self, color: str, state: bool):
        led = {"r": self.config.led.r,
               "g": self.config.led.g,
               "b": self.config.led.b,
               "w": self.config.led.w}[color]
        self.toggle_gpio(led, state)

    def identifiers(self) -> DeviceIdentifiers:
        output = self.shell.command("identifiers")

        output = output.split("\n")

        mlb_serial = SerialNumber(output[1].split(": ")[1])
        assy_serial = SerialNumber(output[2].split(": ")[1])
        board_id = BoardId(output[3].split(": ")[1])
        chip_id = ChipId(output[4].split(": ")[1])
        devinfo = DevInfo(output[5].split(": ")[1])

        return DeviceIdentifiers(mlb_serial=mlb_serial, assy_serial=assy_serial,
                                 board_id=board_id, chip_id=chip_id, devinfo=devinfo)

    def metadata(self) -> str:
        output = self.shell.command("meta")
        output = output[5:]  # Bad.
        return output

    def write_mlb_serial(self, mlb_serial: str):
        mlb_serial = SerialNumber(mlb_serial)  # Check if valid.
        self.shell.command(f"identifiers -m {mlb_serial.serial}")

    def write_assy_serial(self, assy_serial: str):
        assy_serial = SerialNumber(assy_serial)  # Check if valid.
        self.shell.command(f"identifiers -a {assy_serial.serial}")

    def uptime(self) -> int:
        output = self.shell.command("uptime")
        output = output[7:].rstrip()  # As above, bad.
        hours, minutes, tmp = output.split(":")
        seconds, millis = tmp.split(".")
        seconds = (int(hours) * 3600) + (int(minutes) * 60) + \
            int(seconds) + (int(millis) * 0.001)
        return seconds

    def charger_info(self) -> ChargerInfo:
        charger_info = ChargerInfo(None, None, None)

        output = self.shell.command("charger -s").split("\n")[1]
        charger_info.status = not ("NOT" in output)

        output = self.shell.command("charger -m").split("\n")[2].lstrip()
        charger_info.mode = output

        output = self.shell.command("charger -d").split("\n")[1:]
        charger_info.registers = output

        return charger_info

    def charger_mode(self) -> str:
        output = self.shell.command("charger -m").split("\n")
        return output[1] + output[2]

    def led_off(self) -> str:
        self.shell.command("led-anim -o").split("\n")

    def fuel(self) -> FuelStatus:
        output = self.shell.command("fuel -s").split("\n")
        # Handle error output from fuel gauge
        if len(output) == 6:
            output.remove(output[1])
            output.remove(output[1])
        repsoc = float(output[1].split("=")[1].strip().replace('%', ''))
        vcell = int(output[2].split("=")[1].strip().replace('mV', ''))
        return FuelStatus(repsoc, vcell)


@cli.command()
@click.argument("port", type=click.Choice(["a", "b", "c", "d"], case_sensitive=False), required=True)
@click.argument("pin", type=int, required=True)
@click.argument("state", type=click.Choice(["on", "off"],
                                           case_sensitive=False), required=True)
@click.pass_context
def toggle_gpio(ctx, port, pin, state):
    """Toggle GPIOs on or off.

    A GPIO is specified by a port (e.g. A, B, C, D) and pin (an integer).
    This command does not perform validation on the GPIO, so caution is necessary
    before usage.
    """
    state = (state == "on")
    W1Controller(config=_get_config(ctx), serial_port_name=ctx.obj[SERIAL_PORT_NAME]).toggle_gpio(
        Gpio(Port.from_str(port), pin), state)


@cli.command()
@click.argument("color", type=click.Choice(["r", "g", "b", "w"],
                                           case_sensitive=False), required=True)
@click.argument("state", type=click.Choice(["on", "off"],
                                           case_sensitive=False), required=True)
@click.pass_context
def toggle_led(ctx, color, state):
    """Toggle an LED on or off.

    A LED is specified by a letter indicating the color of LED
    to be toggled.
    """
    state = (state == "on")
    W1Controller(config=_get_config(
        ctx), serial_port_name=ctx.obj[SERIAL_PORT_NAME]).toggle_led(color, state)


@cli.command()
@click.pass_context
def identifiers(ctx):
    """Output device identifiers such as serial number, board id, and chip id."""
    click.echo(W1Controller(config=_get_config(ctx),
               serial_port_name=ctx.obj[SERIAL_PORT_NAME]).identifiers())


@cli.command()
@click.pass_context
def metadata(ctx):
    """Output firmware metadata, such as version number."""
    click.echo(W1Controller(config=_get_config(ctx),
               serial_port_name=ctx.obj[SERIAL_PORT_NAME]).metadata())


@cli.command()
@click.argument("serial", required=True)
@click.argument("which", type=click.Choice(["mlb", "assy"],
                                           case_sensitive=False), required=True)
@click.pass_context
def write_serial(ctx, serial, which):
    """Program a serial number to device memory."""
    controller = W1Controller(config=_get_config(
        ctx), serial_port_name=ctx.obj[SERIAL_PORT_NAME])
    if which == "mlb":
        out = controller.write_mlb_serial(serial)
    elif which == "assy":
        out = controller.write_assy_serial(serial)
    click.echo(out)


@cli.command()
@click.pass_context
def uptime(ctx):
    """Retrieve system uptime in seconds."""
    controller = W1Controller(config=_get_config(
        ctx), serial_port_name=ctx.obj[SERIAL_PORT_NAME])
    click.echo(controller.uptime())


@cli.command()
@click.option("-s", "--status", is_flag=True, show_default=True, default=False, help="Get charging status")
@click.option("-m", "--mode", is_flag=True, show_default=True, default=False, help="Get charger mode")
@click.option("-d", "--dump", is_flag=True, show_default=True, default=False, help="Dump register info")
@click.pass_context
def charger(ctx, status, mode, dump):
    """Charger commands."""
    controller = W1Controller(config=_get_config(
        ctx), serial_port_name=ctx.obj[SERIAL_PORT_NAME])
    info = controller.charger_info()
    if status:
        click.echo(info.status)
    if mode:
        click.echo(info.mode)
    if dump:
        click.echo("\n".join(info.registers))


@cli.command()
@click.pass_context
def fuel(ctx):
    """Get fuel status."""
    controller = W1Controller(config=_get_config(
        ctx), serial_port_name=ctx.obj[SERIAL_PORT_NAME])
    click.echo(controller.fuel())


@cli.command()
@click.pass_context
def generate_documentation(ctx):
    """Generate a markdown file documenting this program."""
    click.echo("# W1 Controller CLI")
    click.echo("```")
    # ctx.info_name will be `generate-documentation` at this point, so we must
    # manually update it.
    ctx.info_name = None
    click.echo(cli.get_help(ctx))
    click.echo("```")
    click.echo()
    click.echo("---")

    for name, fn in cli.commands.items():
        if name == "generate-documentation":
            # The documentation is meant for end-users, who need not worry
            # about this command.
            continue
        ctx.info_name = name
        click.echo(f"## {name}")
        click.echo("```")
        click.echo(fn.get_help(ctx))
        click.echo("```")
        click.echo()
        click.echo("---")


if __name__ == "__main__":
    cli()

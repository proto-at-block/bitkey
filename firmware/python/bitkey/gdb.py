import json
import re
import sys
import click
import shlex
import psutil
import certifi
import pathlib
import subprocess

from tasks.lib.paths import BUILD_FW_APPS_DIR, CONFIG_FILE
from pygdbmi import gdbmiparser
from pprint import pprint

cli = click.Group()

DEVICE_CHIP = "EFR32MG24BXXXF1536"

gdb_flash_txt = """target extended-remote localhost:2331
monitor reset
load
compare-sections
monitor reset
monitor go
disconnect
quit
"""


class JLinkGdbServer:
    def __init__(self, chip: str, gdb_config: str = None, gdb_server_path: str = None, gdb_client_path: str = None):
        self.chip = chip
        self.gdb_config = gdb_config
        self.gdb_server = gdb_server_path or "JLinkGDBServer"
        self.gdb_client = gdb_client_path or "arm-none-eabi-gdb"

    def __enter__(self):
        # Kill dangling JLink processes. They can cause flashing to fail.
        self._kill_jlink_processes()

        server_command = shlex.split(
            f"{self.gdb_server} -nogui -device {self.chip} -if SWD")
        self.server_process = subprocess.Popen(server_command, start_new_session=True,
                                               stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self.server_process.kill()

    def _kill_jlink_processes(self):
        for p in psutil.process_iter():
            if 'JLink' in p.name():
                p.terminate()
                p.wait()

    def _parse_gdb_err(self, err: str) -> str:
        """Parses the error from a gdb stderr output"""
        # Example stderr output:
        # b'/.../gdb_flash.txt:1: Error in sourced command file:\nlocalhost:2331: Operation timed out.\n'
        pattern = re.compile(r"^localhost:\d+: (.*)$")
        lines = err.decode("utf-8").split('\n')
        # Second line of the output is the gdb error (typically)
        err_line = str(lines[1])

        try:
            return pattern.search(err_line).group(1)
        except:
            if 'disconnected' in err_line:
                return err_line.split('. ')[0]
            else:
                return "Unknown error"

    def flash(self, image: pathlib.Path) -> bool:
        assert self.gdb_config
        try:
            subprocess.check_output(shlex.split(
                f"{self.gdb_client} -q --batch --command={self.gdb_config} {image.absolute()}"),
                stderr=subprocess.STDOUT)
            click.echo(click.style(f'Flashed {image.name}', fg='green'))
            return True
        except subprocess.CalledProcessError as e:
            err = self._parse_gdb_err(e.output)
            click.echo(click.style(
                f'Error flashing {image.name}: {err}', fg='red'))
            return False

    def _do_erase(self, image: pathlib.Path):
        command = shlex.split(f"{self.gdb_client} -q {image.absolute()}")
        try:
            p = subprocess.Popen(command, stdin=subprocess.PIPE,
                                 stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            output = p.communicate(
                input="""target extended-remote localhost:2331
monitor flash erase
kill""".encode())[0]

            if "Flash erase: O.K." not in str(output):
                click.echo(click.style(f'Failed to erase flash', fg='red'))
                return False
            else:
                click.echo(click.style(f'Erased flash', fg='green'))
                return True
        except subprocess.CalledProcessError as e:
            err = self._parse_gdb_err(e.output)
            click.echo(click.style(
                f'Error erasing {image.name}: {err}', fg='red'))
        finally:
            p.kill()

    def erase(self, image: pathlib.Path):
        attempt, limit = (0, 5)
        while not self._do_erase(image) and attempt < limit:
            attempt += 1
            click.echo(click.style(
                f"Trying to erase flash again... ({attempt}/{limit})", fg='red'))
        return attempt != limit

    def debug_command(self, image: pathlib.Path):
        """Returns the command needed to open a gdb debugging session"""
        target_command = "target extended-remote localhost:2331"
        python_site_packages = list(filter(lambda x: x.endswith(
            'site-packages'), sys.path))[0]
        return [
            # Set SSL_CERT_FILE for authenticating to Memfault.
            f"SSL_CERT_FILE={certifi.where()}",
            "arm-none-eabi-gdb-py3",
            "-q",
            f"--eval-command=\"{target_command}\"",
            f"{image}",
            f"--ex=\"python import sys; sys.path.insert(0, '{python_site_packages}')\"",
            "--ex=\"source lib/metadata/gdb.py\"",
            "--ex=\"python import freertos_gdb\"",
        ]

    def run_command(self, image: pathlib.Path, command: str):
        gdb = shlex.split(f"{self.gdb_client} -q {image.absolute()}")
        try:
            p = subprocess.Popen(gdb, stdin=subprocess.PIPE,
                                 stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            output = p.communicate(
                input=f"target extended-remote localhost:2331\n{command}".encode())[0]
        except subprocess.CalledProcessError as e:
            err = self._parse_gdb_err(e.output)
            click.echo(click.style(f'Error: {err}', fg='red'))
        finally:
            p.kill()


# (This should be run with a JLinkGdbServer running)
class GdbCapture:
    gdb_process = None

    def __init__(self, breakpoints):
        with open(CONFIG_FILE, "r") as f:
            config = json.load(f)
            target = config.get('target')
        self.gdb_process = subprocess.Popen(['arm-none-eabi-gdb-py3', '--interpreter=mi2',
                                             BUILD_FW_APPS_DIR.joinpath(target)], stdin=subprocess.PIPE, stdout=subprocess.PIPE)

        # Connect to remote GDB server
        self.gdb_process.stdin.write(
            b'-target-select extended-remote localhost:2331\n')
        self.gdb_process.stdin.flush()

        # Set breakpoints
        for breakpoint in breakpoints.args:
            self.gdb_process.stdin.write(
                ("b %s" % breakpoint).encode('utf-8') + b'\n')
            self.gdb_process.stdin.flush()

        # Start execution
        self.gdb_process.stdin.write(b'run\n')
        self.gdb_process.stdin.flush()

    def get_backtrace(self):
        # Check for a backtrace
        self.gdb_process.stdin.write(b'-stack-list-frames\n')
        self.gdb_process.stdin.flush()
        payload, error = self.get_gdb_output()
        if payload:
            breakpoint_list = []
            frame_list = []
            # Parse breakpoints and stack output lines from payload only
            for line in payload:
                if 'breakpoint-created,bkpt=' in line:
                    breakpoint_dict = self.parse_breakpoint_output(line)
                    breakpoint_list.append(breakpoint_dict)
                if 'done,stack=' in line:
                    frame_list = self.parse_stack_list_output(line)
            print("GDB Breakpoints Set:")
            pprint(breakpoint_list)
            print("\nGDB Stack Output:")
            pprint(frame_list)
        if error:
            error = error.decode(sys.stderr.encoding)
            error = gdbmiparser.parse_response(error)
            print("GDB Error: \n")
            pprint(error)

    def get_gdb_output(self):
        """Retrieves the output from gdb subprocess"""
        output, error = self.gdb_process.communicate()
        if output:
            output = output.decode(sys.stdout.encoding)
            output = gdbmiparser.parse_response(output)
            output = output.get('payload').split('\n')
        return output, error

    def parse_breakpoint_output(self, output):
        """Parses the output from the gdb breakpoint creation into a single readable dictionary"""
        value = output.split('breakpoint-created,bkpt=')[1]
        value = value.strip().strip('"')
        pairs = value.strip('{}').split(',')
        parsed_dict = {}
        parsed_dict = self.parse_pairs_into_dict(pairs)
        return parsed_dict

    def parse_stack_list_output(self, output):
        """Parses the output from the gdb breakpoint stack list frames into a list of frames as readable dictionaries"""
        line = output.strip().rstrip('\n')
        dict_string = line[line.index('[') + 1:-1]
        frames = dict_string.split('frame=')
        parsed_dicts_list = []
        for frame in frames:
            if not frame:
                continue
            pairs = frame.strip('{}').split(',')
            frame_dict = self.parse_pairs_into_dict(pairs)
            parsed_dicts_list.append(frame_dict)
        return parsed_dicts_list

    def parse_pairs_into_dict(self, pairs):
        """Parses through a string of gdb key value pairs and returns them in a dictionary"""
        dict_of_pairs = {}
        for pair in pairs:
            if not pair:
                continue
            key, value = pair.split('=')
            key = key.strip()
            value = value.strip().strip('"')
            dict_of_pairs[key] = value
        return dict_of_pairs


@cli.command()
@click.argument("application_elf", required=True, type=click.Path(exists=True, path_type=pathlib.Path))
@click.argument("bootloader_elf", required=False, type=click.Path(exists=True, path_type=pathlib.Path))
@click.argument("gdb_config", required=True)
@click.argument("chip", required=True, default="EFR32MG24BXXXF1536")
def flash(application_elf, bootloader_elf, gdb_config, chip):
    with JLinkGdbServer(chip, gdb_config) as gdb:
        if bootloader_elf:
            gdb.flash(bootloader_elf)
        gdb.flash(application_elf)
    click.echo("Flashing done")


if __name__ == "__main__":
    cli()

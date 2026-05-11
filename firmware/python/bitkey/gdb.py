import json
import re
import sys
import click
import shlex
import psutil
import certifi
import pathlib
import socket
import subprocess
import tempfile
import time
from typing import List

from tasks.lib.paths import BUILD_FW_DIR, CONFIG_FILE
from pygdbmi import gdbmiparser
from pprint import pprint

cli = click.Group()

gdb_flash_txt = """target extended-remote localhost:2331
monitor reset
load
compare-sections
monitor reset
monitor go
monitor writeU32 0xE000EDF0 0xA05F0000
disconnect
quit
"""


class JLinkGdbServer:
    def __init__(
        self,
        chip: str,
        gdb_config: str | None = None,
        gdb_server_path: str | None = None,
        gdb_client_path: str | None = None,
        jlink_serial: str | None = None,
        jlink_exe_path: str | None = None,
        gdb_port: int = 2331,
        kill_existing: bool = True,
    ) -> None:
        self.chip: str = chip
        self.gdb_config: str | None = gdb_config
        self.gdb_server: str = gdb_server_path or "JLinkGDBServer"
        self.gdb_client: str = gdb_client_path or "arm-none-eabi-gdb"
        self.jlink_exe: str = jlink_exe_path or "JLinkExe"
        self.jlink_serial: str | None = jlink_serial
        self.gdb_port: int = int(gdb_port)
        self.kill_existing: bool = kill_existing
        if not 1 <= self.gdb_port <= 65533:
            raise click.ClickException(
                "gdb_port must be between 1 and 65533; SWO and telnet use the next two ports"
            )
        if self.gdb_config and self.gdb_port != 2331:
            raise click.ClickException(
                "Custom gdb_port is not supported with a static gdb_config"
            )

    def __enter__(self):
        if self.kill_existing:
            # Kill dangling JLink processes. They can cause flashing to fail.
            self._kill_jlink_processes()

        self._check_ports_available()

        base_command = (
            f"{self.gdb_server} -nogui -device {self.chip} -if SWD "
            f"-port {self.gdb_port} "
            f"-swoport {self.gdb_port + 1} "
            f"-telnetport {self.gdb_port + 2}"
        )
        if self.jlink_serial:
            base_command += f" -select usb={self.jlink_serial}"

        server_command = shlex.split(base_command)
        self.server_process = subprocess.Popen(server_command, start_new_session=True,
                                               stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
        self._check_server_started()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self.server_process.kill()

    def _kill_jlink_processes(self):
        for p in psutil.process_iter():
            if 'JLink' in p.name():
                p.terminate()
                p.wait()

    def _check_ports_available(self):
        host, _ = self._gdb_server_endpoint()
        for port in range(self.gdb_port, self.gdb_port + 3):
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                if s.connect_ex((host, port)) == 0:
                    raise click.ClickException(
                        f"Port {port} is already in use; choose a different --gdb-port"
                    )

    def _check_server_started(self):
        time.sleep(0.5)
        if self.server_process.poll() is not None:
            raise click.ClickException(
                "JLinkGDBServer exited before GDB could connect; check the J-Link serial and ports"
            )

    def _parse_gdb_err(self, err: str) -> str:
        """Parses the error from a gdb stderr output.

        Example standard error output:
            b'/.../gdb_flash.txt:1: Error in sourced command file:
            localhost:2331: Operation timed out.

        :param err: standard error from the GDB server execution.
        :returns: extracted error string.
        """
        host, _ = self._gdb_server_endpoint()
        pattern = re.compile(fr"^{host}:\d+: (.*)$")
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
            host, port = self._gdb_server_endpoint()
            output = p.communicate(
                input="\n".join([
                    f"target extended-remote {host}:{port}",
                    "monitor reset",
                    "monitor halt",
                    "monitor flash erase",
                    "kill"
                ]).encode())[0]

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

    def _gdb_server_endpoint(self) -> tuple[str, int]:
        """Returns the host and port for the GDB server.

        :returns: ``(hostname, port number)``.
        """
        host = (
            getattr(self, "gdb_host", None)
            or getattr(self, "host", None)
            or "localhost"
        )
        port = (
            getattr(self, "gdb_port", None)
            or getattr(self, "port", None)
            or getattr(self, "server_port", None)
            or 2331
        )
        return host, int(port)

    def erase_range(self, addr: int, size: int) -> bool:
        """Erase a specific flash address range using the active J-Link GDB server.

        :param addr: start address (in flash) to start the erase from.
        :param size: number of bytes to erase, must be a multiple of the minimum erase size.
        :returns: ``True`` on success, otherwise ``False``.
        """
        end_addr = addr + size
        script = "\n".join([
            f"device {self.chip}",
            "si 1",
            "speed 4000",
            "connect",
            f"erase {addr:#010x} {end_addr:#010x}",
            "exit"
        ])

        try:
            with tempfile.NamedTemporaryFile(mode="w", suffix=".jlink") as tf:
                tf.write(script)
                tf.flush()

                cmd = f"{self.jlink_exe} -CommandFile {tf.name} -NoGui 1"
                if self.jlink_serial:
                    cmd += f" -USB {self.jlink_serial}"

                output = subprocess.check_output(
                    shlex.split(cmd), stderr=subprocess.STDOUT)
                output_str = output.decode("utf-8", errors="replace")
                if "error" not in output_str.lower():
                    click.echo(click.style(
                        f"Erased {addr:#010x}+{size:#x}", fg="green"))
                    return True

            click.echo(output_str)
            click.echo(click.style(
                f"Failed to erase range {addr:#010x}+{size:#x}", fg="red"))
            return False
        except subprocess.CalledProcessError as e:
            output_str = e.output.decode("utf-8", errors="replace")
            click.echo(output_str)
            click.echo(click.style(
                f"Failed to erase range {addr:#010x}+{size:#x}", fg="red"))
            return False

    def debug_command(self, image: pathlib.Path):
        """Returns the command needed to open a gdb debugging session"""
        host, port = self._gdb_server_endpoint()
        target_command = f"target extended-remote {host}:{port}"
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
            host, port = self._gdb_server_endpoint()
            output = p.communicate(
                input=f"target extended-remote {host}:{port}\n{command}".encode())[0]
        except subprocess.CalledProcessError as e:
            err = self._parse_gdb_err(e.output)
            click.echo(click.style(f'Error: {err}', fg='red'))
        finally:
            p.kill()


# (This should be run with a JLinkGdbServer running)
class GdbCapture:
    gdb_process = None

    def __init__(
        self,
        breakpoints: List[str],
        platform: str,
        host: str = "localhost",
        port: int = 2331,
    ) -> None:
        with open(CONFIG_FILE, "r") as f:
            config = json.load(f)
            target = config.get('target')

        build_dir = BUILD_FW_DIR.joinpath(platform)
        target_dir = build_dir.joinpath("app", platform, "application", target)
        self.gdb_process = subprocess.Popen(['arm-none-eabi-gdb-py3', '--interpreter=mi2',
                                             target_dir], stdin=subprocess.PIPE, stdout=subprocess.PIPE)

        # Connect to remote GDB server
        self.gdb_process.stdin.write(
            f'-target-select extended-remote {host}:{port}\n'.encode('utf-8'))
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

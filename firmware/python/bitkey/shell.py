import io
import platform

from serial import Serial, serialutil
from serial.tools.miniterm import Miniterm


class Shell:
    def __init__(self, serial_port_name: str = None):
        if serial_port_name:
            serial_port = serial_port_name
        else:
            if platform.system() == 'Windows':
                serial_port = 'COM4'
            else:
                serial_port = '/dev/cu.usbserial-0001'
        m = Miniterm(
            Serial(
                port=serial_port,
                baudrate=115200,
                timeout=2,
            ),
            echo=False,
            eol="crlf",
            filters=["default"]
        )
        m.exit_character = chr(29)
        m.menu_character = chr(20)
        m.raw = True
        m.set_rx_encoding("ascii")
        m.set_tx_encoding("ascii")
        self.miniterm = m

    def interactive(self):
        self.miniterm.start()
        self.miniterm.serial.write(b"\n")
        try:
            self.miniterm.join(True)
        except KeyboardInterrupt:
            pass
        self.miniterm.join()
        self.miniterm.close()

    def command(self, line: str) -> str:
        try:
            self.miniterm.serial.write(b"%s\n" % line.encode("ascii"))
            data = self.miniterm.serial.read_until(b"W1>")
            _stdout = io.BytesIO()
            self.miniterm.console.byte_output = _stdout
            self.miniterm.console.write_bytes(data)
            output = str(_stdout.getvalue(),
                         encoding="ascii").split("W1>", 1)[0]
            # TODO: Hack, but fine for now.
            MAGENTA = "\033[1;35m"
            output = output[:output.find(MAGENTA)]
            return output
        except serialutil.SerialException:
            raise IOError("SerialException")

    def command_binary(self, cmd: str, data: bytes) -> bytes:
        response = self.command("{} {}".format(cmd, data.hex()))

        if 'err:' in response:
            raise IOError(response[response.find('err:') + len('err:'):])

        if 'ok:' in response:
            hex_response = response[response.find('ok:') + len('ok:'):]
            return bytearray.fromhex(hex_response)

        return None


if __name__ == "__main__":
    Shell().interactive()

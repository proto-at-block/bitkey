from __future__ import annotations

import logging
import re
import serial
import serial.tools.list_ports as list_ports
import time
from types import TracebackType
from typing import List, Optional, Union

from bitkey_proto import wallet_pb2 as wallet_pb
import nfc

from . import util
from .shell import Shell

logger = logging.getLogger(__name__)


class NFCTransaction:
    retry_max = 5
    rdwr_options = {'on-connect': lambda tag: False}
    timeout = 0.25  # 250ms timeout

    def __init__(self, usbstr='usb'):
        spec = self.port_spec_to_usb_device(usbstr)
        self.clf = nfc.ContactlessFrontend(spec)
        self._connect()

    def _connect(self):
        try:
            self.tag = self.clf.connect(rdwr=self.rdwr_options)
        except Exception as e:
            raise (e)
        if self.tag == False or self.tag == None:
            raise IOError("Failed to connect to NFC reader")

    def _resume(self):
        # ST-RFAL does not support APDU chaining during NFC activation.
        # To work around this, we send an empty message as the first thing
        # to resume communications.
        try:
            self.tag.transceive(Wca.make_resume_message())
        except Exception as e:
            print("failed to resume")
            raise (e)

    @staticmethod
    def port_spec_to_usb_device(port_spec: str) -> str:
        """Convert device port specification to nfcpy USB device format.

        Accepts:
          - Physical port path: '3-6.4.4.4.2' or 'usb:3-6.4.4.4.2'
          - Bus:Device format: 'usb:003:009' (passed through)
          - VID:PID format: 'usb:054c:02e1' (passed through)
          - Auto-detect: 'usb' (passed through)

        :param port_spec: the device port specification.
        :returns: USB device format string.
        """
        if not port_spec or port_spec == 'usb':
            return 'usb'

        # Remove 'usb:' prefix if present
        if port_spec.startswith('usb:'):
            port_spec = port_spec[4:]

        # Check if it's already in correct format (bus:device or vid:pid)
        # Format: 3 or 4 hex digits, colon, 3 or 4 hex digits
        if re.match(r'^[0-9a-fA-F]{3,4}:[0-9a-fA-F]{3,4}$', port_spec):
            return f'usb:{port_spec}'

        # Check if it looks like a physical port path (contains dots or dashes)
        # Examples: '3-6.4.4.4.2', '1-1.4', '3-6'
        if re.match(r'^\d+[-.][\d.]+$', port_spec):
            try:
                bus, dev = util.usb_dev_from_port(port_spec)
            except TypeError:
                raise RuntimeError(f"Device not found at physical port {port_spec}")
            return f'usb:{bus:03d}:{dev:03d}'

        # If we get here, assume it's already a valid format and pass through.
        return f'usb:{port_spec}'

    def transceive(self, payload, timeout=None):
        timeout = timeout if timeout else self.timeout
        for _ in range(0, self.retry_max):
            try:
                return self.tag.transceive(bytes(payload), timeout=timeout)
            except nfc.tag.tt4.Type4TagCommandError:
                print("NFC error, retrying...")
                self._connect()
                self._resume()
                continue
        raise IOError("nfc retry error")

    def close(self):
        self.clf.close()


class SerialTransport:

    _DEFAULT_VIDS = [
        0x0403,  # FTDI
    ]

    _INTERFACES = [
        "UART",
        "TTL232R",
        "FT232R",
        "FT232H",
        "C232HM-DDHSL-0"
    ]

    baudrate: int
    timeout: int

    def __init__(
        self: SerialTransport,
        vids: Optional[Union[int, List[int]]] = None,
        pids: Optional[Union[int, List[int]]] = None,
        interfaces: Optional[Union[str, List[str]]] = None,
        baudrate: int = 2000000,
        timeout: int = 10,
    ) -> None:
        """Initializes the serial transport instance.

        :param vids: optional list of vendor IDs to filter serial devices by.
        :param pids: optional list of product IDs to filter serial devices by.
        :param interfaces: optional list of serial device names.
        :param baudrate: serial baudrate (default: 2 MHz).
        :param timeout: timeout (in seconds) for serial operations (default: 10s).
        :returns: ``None``
        """
        if not vids and not pids:
            vids = self._DEFAULT_VIDS

        if isinstance(vids, int):
            vids = [vids]

        if isinstance(pids, int):
            pids = [pids]

        if interfaces is None:
            interfaces = self._INTERFACES
        elif isinstance(interfaces, str):
            interfaces = [interfaces]

        self._vids: Optional[List[int]] = vids
        self._pids: Optional[List[int]] = pids
        self._interfaces: List[str] = interfaces
        self._serial: Optional[serial.Serial] = None

        self.baudrate = baudrate
        self.timeout = timeout

    def __del__(self: SerialTransport) -> None:
        """Tears down the serial interface.

        :param self: the transport instance.
        :returns: ``None``
        """
        self.close()

    def __enter__(self: SerialTransport) -> SerialTransport:
        """Opens the underlying serial connection.

        :param self: the transport instance.
        :returns: the transport instance.
        """
        self.open()
        return self

    def __exit__(
        self: SerialTransport,
        type: Optional[BaseException] = None,
        value: Optional[BaseException] = None,
        traceback: Optional[TracebackType] = None,
    ) -> None:
        """Tears down the open serial connection.

        :param self: the transport instance.
        :returns: ``None``
        """
        self.close()

    def open(self: SerialTransport) -> None:
        """"Opens the serial interface for communication.

        :param self: the transport instance.
        :returns: ``None``
        :raises: ``RuntimeError`` if serial device not found.
        """
        self.close()

        ports = []
        for port in list_ports.comports():
            if self._vids is None or port.vid in self._vids:
                ports.append(port)

        dev = None
        for port in ports:
            logger.debug(f"Found device: {port.interface} ({port.name})")
            if port.interface is not None:
                if any(interface in port.interface for interface in self._interfaces):
                    dev = port.device
                    break
            elif port.product is not None:
                if any(port.product.startswith(interface) for interface in self._interfaces):
                    dev = port.device
                    break
        else:
            raise RuntimeError("UART not found.")

        self._serial = serial.Serial(dev, self.baudrate, timeout=self.timeout)

    def close(self: SerialTransport) -> None:
        """Closes the serial interface.

        :param self: the transport instance.
        :returns: ``None``
        """
        if self._serial is not None:
            self._serial.close()
            self._serial = None

    def write(self: SerialTransport, data: bytes) -> bool:
        """Writes bytes over the underlying serial interface.

        :param self: the transport instance.
        :param data: bytes to write.
        :returns: ``True`` if data written, otherwise ``False``.
        """
        if self._serial:
            try:
                self._serial.write(data)
            except serial.serialutil.SerialException as err:
                logger.debug(f"Failed to write data to serial: {err=}")
                return False
            return True
        return False

    def read(self: SerialTransport, num_bytes: int) -> Optional[bytes]:
        """Reads bytes from the underlying serial interface.

        :param self: the transport instance.
        :param num_bytes: number of bytes to read.
        :returns: ``None`` if no bytes available, otherwise bytes read.
        """
        if not self._serial:
            return None

        bytes_to_read = self._serial.inWaiting()
        if bytes_to_read == 0:
            # No bytes to read, wait to throttle read, then return.
            time.sleep(0.05)
            return None
        return self._serial.read(bytes_to_read)


class ShellTransaction:
    def __init__(self, port):
        self.shell = Shell(port)

    def transceive(self, payload, timeout=None):
        return self.shell.command_binary("wca", bytes(payload))

    def close(self):
        self.clf.close()


class Wca:
    CLA = 0x87
    INS_PROTO = 0x75
    INS_PROTO_CONT = 0x77
    INS_PROTO_GET_RESPONSE = 0x78

    MAX_WCA_BUFFER_SIZE = 512
    APDU_OVERHEAD_SIZE = 7
    MAX_PROTO_SIZE = MAX_WCA_BUFFER_SIZE - APDU_OVERHEAD_SIZE

    @staticmethod
    def _be_uint16(size):
        return size.to_bytes(2, byteorder='big')

    @staticmethod
    def _encode_lc(size):
        if size >= 1 and size <= 255:
            return size.to_bytes(1, byteorder='big')
        else:
            return [0] + list(size.to_bytes(2, byteorder='big'))

    @staticmethod
    def make_resume_message():
        cmd = wallet_pb.wallet_cmd()
        msg = wallet_pb.empty_cmd()
        cmd.empty_cmd.CopyFrom(msg)
        return bytes(Wca.from_serialized_proto(cmd.SerializeToString())[0])

    @staticmethod
    def from_serialized_proto(proto):
        first = True
        chunks = []
        for i in range(0, len(proto), Wca.MAX_PROTO_SIZE):
            proto_fragment = proto[i:i + Wca.MAX_PROTO_SIZE]

            if first:
                first = False
                chunk = [Wca.CLA, Wca.INS_PROTO]
            else:
                chunk = [Wca.CLA, Wca.INS_PROTO_CONT]

            chunk += Wca._be_uint16(len(proto))
            chunk += Wca._encode_lc(len(proto_fragment))
            chunk += proto_fragment

            assert len(chunk) <= Wca.MAX_WCA_BUFFER_SIZE
            chunks.append(chunk)
        return chunks


class WalletComms:
    def __init__(self, transport=None, debug=False):
        if transport == None:
            transport = NFCTransaction()
        self.transport = transport
        self.debug = debug

    def _status_words_ok(self, sw1, sw2):
        return (sw1 == 0x90 and sw2 == 0x00) or (sw1 == 0x61)

    def transceive(self, proto, timeout=None):
        serialized = proto.SerializeToString()
        chunks = Wca.from_serialized_proto(serialized)

        for chunk in chunks:
            if self.debug:
                print(f"TX: {', '.join('0x{:02x}'.format(a) for a in chunk)}")
            response = self.transport.transceive(chunk, timeout=timeout)
            if response is None or len(response) < 2:
                raise IOError("response error")
            sw1, sw2 = response[-2], response[-1]
            if not self._status_words_ok(sw1, sw2):
                raise IOError(f"apdu status words {sw1}, {sw2}")

        response_bytes = response[:len(response)-2]
        if self.debug:
            print(
                f"RX: {', '.join('0x{:02x}'.format(a) for a in response_bytes)}")

        # TODO: Handle response chunks
        rsp = wallet_pb.wallet_rsp()

        # TODO(W-4766): Determine why there is sometimes a garbage
        # first byte when spamming NFC messages.
        try:
            rsp.ParseFromString(bytes(response_bytes))
        except:
            response_bytes = response_bytes[1:]
            rsp.ParseFromString(bytes(response_bytes))

        return rsp

    def close(self):
        self.transport.close()

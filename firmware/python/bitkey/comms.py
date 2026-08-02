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

    def __init__(self, usbstr='usb', timeout=None, transceive_timeout=None):
        """Initialize NFC connection.

        Args:
            usbstr: USB device selector ('usb' auto-detects)
            timeout: Global timeout in seconds including retries (None = unlimited)
            transceive_timeout: Per-transceive timeout in seconds (default: 0.25s)
        """
        if timeout is not None and timeout < 0:
            raise ValueError(f"timeout must be non-negative, got {timeout}")
        if transceive_timeout is not None and transceive_timeout < 0:
            raise ValueError(
                f"transceive_timeout must be non-negative, got {transceive_timeout}")

        self.transceive_timeout = transceive_timeout if transceive_timeout is not None else 0.25
        self.global_timeout = timeout
        self.show_retries = False
        self.tag = None

        spec = self.port_spec_to_usb_device(usbstr)
        self.clf = nfc.ContactlessFrontend(spec)
        self._connect()

    def _connect(self):
        # Use nfcpy's built-in terminate parameter for timeout support
        if self.global_timeout is not None and self.global_timeout > 0:
            started = time.monotonic()

            def terminate():
                return time.monotonic() - started > self.global_timeout
            self.tag = self.clf.connect(
                rdwr=self.rdwr_options, terminate=terminate)
            if not self.tag:
                raise TimeoutError(
                    f"Timeout waiting for NFC device ({self.global_timeout}s)")
        else:
            self.tag = self.clf.connect(rdwr=self.rdwr_options)
            if not self.tag:
                raise IOError("Failed to connect to NFC reader")

    def _resume(self):
        # ST-RFAL does not support APDU chaining during NFC activation.
        # To work around this, we send an empty message as the first thing
        # to resume communications.
        try:
            self.tag.transceive(Wca.make_resume_message())
        except Exception:
            print("failed to resume")
            raise

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
                raise RuntimeError(
                    f"Device not found at physical port {port_spec}")
            return f'usb:{bus:03d}:{dev:03d}'

        # If we get here, assume it's already a valid format and pass through.
        return f'usb:{port_spec}'

    def transceive(self, payload: Union[bytes, List[int]], timeout: Optional[int] = None) -> bytes:
        """Performs an NFC transaction with the underlying NFC tag.

        This method will retry communication with the NFC tag up to the defined
        ``retry_max`` specified in the ``NfcTransaction`` instance.

        :param payload: data to transmit to the tag.
        :param timeout: optional timeout (int) per transaction attempt.
        :returns: ``None``
        """
        per_transceive_timeout = timeout if timeout is not None else self.transceive_timeout
        err = None
        timed_out: bool = True

        # Set up global timeout tracking using monotonic clock
        start_time = time.monotonic()

        for attempt in range(0, self.retry_max):
            # Check if we've exceeded the global timeout
            if self.global_timeout is not None:
                elapsed = time.monotonic() - start_time
                if elapsed >= self.global_timeout:
                    raise TimeoutError(
                        f"Global timeout ({self.global_timeout}s) exceeded after {attempt} attempts")

            try:
                return self.tag.transceive(bytes(payload), timeout=per_transceive_timeout)
            except nfc.tag.tt4.Type4TagCommandError as _err:
                if _err.errno == nfc.tag.TIMEOUT_ERROR:
                    if self.show_retries:
                        print(f"NFC timeout during transceive (attempt {attempt + 1}/{self.retry_max}), retrying...")
                    else:
                        logger.info(
                            f"Timed out during NFC transceive ({attempt=}), retrying...")
                else:
                    err = _err
                    if self.show_retries:
                        print(f"NFC error during transceive (attempt {attempt + 1}/{self.retry_max}), retrying...")
                    else:
                        print(f"NFC error during transceive, retrying...")
                    timed_out = False
                self._connect()
                self._resume()
                continue

        if timed_out:
            raise TimeoutError("Timed out communicating with NFC tag")
        raise IOError(f"NFC retry error: error={err}")

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
        self.shell.close()


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
    _DEFAULT_SEND_RETRY_MAX = 5
    # Most responses are derived as "<command field without _cmd>_rsp".
    # These entries are explicit exceptions where wallet.proto uses different names.
    _CMD_TO_RSP_NAME_OVERRIDES = {
        "derive_key_descriptor_cmd": "derive_rsp",
        "derive_key_descriptor_and_sign_cmd": "derive_and_sign_rsp",
        "sign_tx_request_cmd": "sign_tx_response",
    }
    # These commands intentionally return status-only wallet_rsp messages
    # (no oneof msg) for some flows.
    _STATUS_ONLY_ALLOWED_COMMANDS = {
        "fwup_start_cmd",
        "get_confirmation_result_cmd",
        "wipe_state_cmd",
    }

    def __init__(self, transport=None, debug=False):
        if transport is None:
            transport = NFCTransaction()
        self.transport = transport
        self.debug = debug
        transport_retry_max = getattr(
            self.transport, "retry_max", self._DEFAULT_SEND_RETRY_MAX)
        if isinstance(transport_retry_max, int) and transport_retry_max > 0:
            # Keep command-level retries bounded so we don't amplify
            # large transport retry settings.
            self.send_retry_max = min(
                transport_retry_max, self._DEFAULT_SEND_RETRY_MAX)
        else:
            self.send_retry_max = self._DEFAULT_SEND_RETRY_MAX

    def _status_words_ok(self, sw1, sw2):
        return (sw1 == 0x90 and sw2 == 0x00) or (sw1 == 0x61)

    @classmethod
    def response_name_for_command(cls, cmd):
        cmd_name = cmd.WhichOneof("msg")
        if cmd_name is None:
            raise ValueError("wallet_cmd is missing oneof msg")

        response_name = cls._CMD_TO_RSP_NAME_OVERRIDES.get(cmd_name)
        if response_name is not None:
            return response_name

        if not cmd_name.endswith("_cmd"):
            raise ValueError(
                f"wallet_cmd msg field does not end with _cmd: {cmd_name}")
        return f"{cmd_name[:-4]}_rsp"

    @classmethod
    def response_tag_for_command(cls, cmd):
        response_name = cls.response_name_for_command(cmd)
        response_field = wallet_pb.wallet_rsp.DESCRIPTOR.fields_by_name.get(
            response_name)
        if response_field is None:
            raise ValueError(
                f"wallet_rsp is missing expected field: {response_name}")
        return response_field.number

    @staticmethod
    def response_tag_from_response(rsp):
        response_name = rsp.WhichOneof("msg")
        if response_name is None:
            return None
        return wallet_pb.wallet_rsp.DESCRIPTOR.fields_by_name[response_name].number

    def _transceive_once(self, proto, timeout=None):
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

    def transceive(self, proto, timeout=None):
        # Only wallet_cmd responses can be validated by matching response oneof tags.
        descriptor = getattr(proto, "DESCRIPTOR", None)
        if descriptor is None or descriptor.full_name != wallet_pb.wallet_cmd.DESCRIPTOR.full_name:
            return self._transceive_once(proto, timeout=timeout)

        expected_response_tag = self.response_tag_for_command(proto)
        return self.send(proto, expected_response_tag, timeout=timeout)

    def send(self, proto, expected_response_tag, timeout=None):
        """Send a command and validate the returned wallet_rsp oneof tag."""
        cmd_name = proto.WhichOneof("msg")
        last_response_tag = None

        for attempt in range(self.send_retry_max):
            rsp = self._transceive_once(proto, timeout=timeout)
            response_tag = self.response_tag_from_response(rsp)

            if response_tag == expected_response_tag:
                return rsp

            if response_tag is None:
                # Older firmware can return status-only UNKNOWN_MESSAGE for
                # unsupported command tags; preserve that signal for callers.
                # Some firmware also uses the deprecated unknown_msg flag.
                if rsp.status == wallet_pb.status.UNKNOWN_MESSAGE or rsp.unknown_msg:
                    return rsp

                # Only allow status-only responses for command flows that intentionally
                # omit wallet_rsp.msg. This avoids treating malformed/empty responses
                # as success for regular typed commands.
                if cmd_name in self._STATUS_ONLY_ALLOWED_COMMANDS and rsp.status != wallet_pb.status.UNSPECIFIED:
                    return rsp

            last_response_tag = response_tag
            if getattr(self.transport, "show_retries", False):
                print(
                    "Unexpected wallet_rsp tag; retrying "
                    f"(attempt {attempt + 1}/{self.send_retry_max}, "
                    f"expected={expected_response_tag}, actual={response_tag})"
                )
            else:
                logger.warning(
                    "Unexpected wallet_rsp tag. expected=%s actual=%s retry=%d/%d",
                    expected_response_tag,
                    response_tag,
                    attempt + 1,
                    self.send_retry_max,
                )

        raise IOError(
            f"wallet_rsp tag mismatch after {self.send_retry_max} retries: "
            f"expected={expected_response_tag} actual={last_response_tag}"
        )

    def close(self):
        self.transport.close()

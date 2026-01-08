"""
UXC interface

This module provides an API for interacting with a UXC chip.
"""

from __future__ import annotations

import abc
import click
import collections
import contextlib
import ctypes
import enum
import inspect
import logging
import queue
import time
from threading import Lock, Thread, Timer
from types import TracebackType
from typing import Dict, List, Optional, Tuple, Union, get_type_hints

from bitkey_proto import mfgtest_pb2, uxc_pb2, wallet_pb2
from google.protobuf.message import Message

from . import cobs, comms

logger = logging.getLogger(__name__)


class UXCError(Exception):
    """Base exception for UXC-related errors."""

    pass


class UXCCertType(enum.IntEnum):
    """Enumeration of certificate types for the UXC."""

    BATCH = wallet_pb2.cert_get_cmd.cert_type.BATCH_CERT
    HOST = wallet_pb2.cert_get_cmd.cert_type.DEVICE_HOST_CERT


class UXCFlags(enum.IntEnum):
    """Message header flags."""

    ACK = 1 << 0
    NACK = 1 << 1
    ENCRYPTED = 1 << 2
    FIRST_MSG = 1 << 3


class UXCMsgHdr(ctypes.LittleEndianStructure):

    _pack_ = 1
    _fields_ = [
        ("proto_tag", ctypes.c_uint32),
        ("send_seq_num", ctypes.c_uint8),
        ("ack_seq_num", ctypes.c_uint8),
        ("crc", ctypes.c_uint16),
        ("flags", ctypes.c_uint16),
        ("reserved", ctypes.c_uint16),
        ("payload_len", ctypes.c_uint16),
    ]


class UXCMsg:

    hdr: UXCMsgHdr
    payload: Optional[Union[bytes, bytearray]]

    # CRC values
    CRC_SEED = 0xFFFF
    CRC_POLYNOMIAL = 0x1021

    def __bytes__(self: UXCMsg) -> bytes:
        """Converts the instance to a bytes.

        :param self: the message instance.
        :returns: message encoded as bytes.
        """
        data = bytes(self.hdr)
        if self.payload:
            data += bytes(self.payload)
        return data

    def device_msg(self: UXCMsg) -> None:
        """Attempts to decode the message payload as a UXC device message.

        This method does not make any assumptions about whether the received
        data is actually a host or device message proto. This is left up to
        the caller.

        :param self: the UXC message instance.
        :returns: ``None`` if decoding fails, else UXC device message.
        """
        msg = uxc_pb2.uxc_msg_device()
        msg.ParseFromString(bytes(self))
        return msg

    def host_msg(self: UXCMsg) -> None:
        """Attempts to decode the message payload as a UXC host message.

        This method does not make any assumptions about whether the received
        data is actually a host or device message proto. This is left up to
        the caller.

        :param self: the UXC message instance.
        :returns: ``None`` if decoding fails, else UXC host message.
        """
        msg = uxc_pb2.uxc_msg_host()
        msg.ParseFromString(bytes(self))
        return msg

    @classmethod
    def compute_crc(
        cls: UXCMsg, hdr: UXCMsgHdr, payload: Optional[Union[bytes, bytearray]]
    ) -> int:
        """Computes the CRC16 of a message header and its payload.

        :param cls: the message class definition.
        :param hdr: the message header instance.
        :param payload: the message payload.
        :returns: computed CRC16 CCITT.
        """
        data = bytearray(hdr)

        # Zero out the CRC
        for i in range(ctypes.sizeof(ctypes.c_uint16)):
            data[UXCMsgHdr.crc.offset + i] = 0x00

        # Add the payload if one is present.
        if payload:
            data.extend(payload)

        crc = cls.CRC_SEED
        for i in range(len(data)):
            crc ^= (data[i] << 8) & 0xFFFF
            for _ in range(8):
                if crc & 0x8000:
                    crc = (crc << 1) ^ cls.CRC_POLYNOMIAL
                else:
                    crc <<= 1
                crc &= 0xFFFF
        return crc

    @classmethod
    def from_bytes(cls: UXCMsg, data: Union[bytes, bytearray, List[int]]) -> UXCMsg:
        """Constructs a message from a bytestream.

        :param cls: the message class definition.
        :param data: input data to decode a message from.
        :returns: ``UXCMsg```
        :raises: ``UXCError`` if decoding fails.
        """
        if not isinstance(data, (bytes, bytearray, list)):
            raise TypeError("Data must be bytes, bytearray or list of integers")

        # Convert to bytes if needed
        if isinstance(data, list):
            data = bytes(data)

        hdr_size = ctypes.sizeof(UXCMsgHdr)
        if len(data) < hdr_size:
            raise UXCError(
                f"Not enough bytes provided for header: expected >= {hdr_size}"
            )

        msg = UXCMsg()
        msg.hdr = UXCMsgHdr.from_buffer_copy(data)

        if len(data[hdr_size:]) < msg.hdr.payload_len:
            raise UXCError(
                f"Not enough bytes provided for payload: expected >= {msg.hdr.payload_len}"
            )

        msg.payload = (
            data[hdr_size : msg.hdr.payload_len + hdr_size]
            if msg.hdr.payload_len
            else None
        )

        crc = cls.compute_crc(msg.hdr, msg.payload)
        if crc != msg.hdr.crc:
            raise UXCError(
                f"CRC mismatch: message CRC={msg.hdr.crc}, computed CRC={crc}"
            )

        return msg


class UXCClient(abc.ABC):

    write: callable
    read: callable
    recv_cb: callable

    ack_timeout: int

    retransmit_timeout: int
    retransmit_count: int

    decoder: cobs.CobsDecoder
    encoder: cobs.CobsEncoder

    # Read Parameters
    READ_MAX_SIZE = 1024
    READ_TIMEOUT_S = 1

    # UXC Parameters
    UXC_RETRANSMIT_MAX_COUNT = 3
    UXC_RETRANSMIT_TIMEOUT_MS = 500
    UXC_ACK_TIMEOUT_MS = 200

    THREAD_STOP_DELAY_S = 0.1

    def __init__(
        self: UXCClient,
        write: callable,
        read: callable,
        recv_cb: Optional[callable] = None,
        ack_timeout: Optional[int] = None,
        retransmit_timeout: Optional[int] = None,
        retransmit_count: Optional[int] = None,
    ) -> None:
        """Initializes the client instance.

        The client is passed a callback to use for reading and write data.

        .. code-block:: python

           def write(data: bytes) -> int:
               return len(data)

           def read(size: int) -> Union[bytes, bytearray):
               return bytearray(size)

           def on_proto(proto_tag: int, proto: Message) -> None:
               pass

           client = UXCClient(write=write, read=read, recv_cb=on_proto)
           client.start()

        :param self: the client instance.
        :param write: callback to invoke to write data.
        :param read: callback to invoke to read data.
        :param recv_cb: callback to invoke when a message is successfully received and decoded.
        :param ack_timeout: milliseconds to wait after reception of data to send a pure ACK.
        :param retransmit_timeout: milliseconds to wait before attempting a re-transmit.
        :param retransmit_count: number of times to retransmit a message.
        :returns: ``None``
        """
        self.write = write
        self.read = read
        self.recv_cb = recv_cb or self._on_proto
        self._recv_q: Dict[int, queue.Queue] = collections.defaultdict(queue.Queue)

        self.decoder = cobs.CobsDecoder()
        self.encoder = cobs.CobsEncoder()

        self.retransmit_timeout = (
            retransmit_timeout or self.UXC_RETRANSMIT_TIMEOUT_MS
        ) / 1000
        self.retransmit_count = retransmit_count or self.UXC_RETRANSMIT_MAX_COUNT
        self.ack_timeout = (ack_timeout or self.UXC_ACK_TIMEOUT_MS) / 1000

        self._send_seq_num: int = 0
        self._recv_seq_num: int = 0
        self._ack_q: queue.Queue = queue.Queue()
        self._sent_first_msg: bool = False

        self._stop: bool = False

        self._timer: Optional[Timer] = None
        self._thread: Optional[Thread] = None
        self._lock = Lock()

    def __del__(self: UXCClient) -> None:
        """Terminates the client instance.

        :param self: the client instance.
        :returns: ``None``
        """
        if self._timer is not None:
            self._timer.cancel()
            self._timer = None

        self.stop()

    def __enter__(self: UXCClient) -> UXCClient:
        """Initiates comms with the companion device/host.

        :param self: the client instance.
        :returns: client instance.
        """
        self.start()
        return self

    def __exit__(
        self: UXCClient,
        type: Optional[BaseException] = None,
        value: Optional[BaseException] = None,
        traceback: Optional[TracebackType] = None,
    ) -> None:
        """Tears down comms with the companion device/host.

        :param self: the client instance.
        :returns: ``None``
        """
        self.stop()
        return None

    def _send_ack(self: UXCClient) -> None:
        """Callback to send a pure ACK on the ACK timer thread.

        :param self: the UXC client instance.
        :returns: ``None``
        """
        logger.debug("ACK timer expired, sending pure ACK.")
        self.send()

    def _recv_thread(self: UXCClient) -> None:
        """Internal receive thread used to read from the input stream.

        :param self: the UXC client instance.
        :returns: ``None``
        """
        read_buffer = bytearray()
        while not self._stop:
            try:
                read_bytes = self.read(self.READ_MAX_SIZE)
                if not read_bytes:
                    continue

                # Append the data to the current read buffer; this is necessary
                # as the read may not contain all the bytes necessary yet.
                read_buffer.extend(read_bytes)
            except Exception:
                # TODO: Maybe we do not want this to be a raw catch?
                continue

            # Process each frame of COBS data.
            enc = bytearray()
            idx = 0
            while idx < len(read_buffer):
                byte = read_buffer[idx]
                idx += 1
                enc.append(byte)

                if byte == cobs.FRAME_DELIMITER:
                    self._handle_recv_data(enc)

                    # Shift the read buffer to remove the read bytes.
                    read_buffer = read_buffer[idx:]
                    idx = 0
                    enc = bytearray()

        logger.debug("Receive thread terminating.")

    def _handle_recv_data(self: UXCClient, enc: bytes) -> None:
        """Handles data received by the receive thread.

        This method is responsible for decoding received data and forwarding
        the decoed proto to the registered callback.

        :param self: the UXC client instance.
        :param enc: the COBS encoded read data.
        :returns: ``None``
        """
        try:
            dec = self.decoder.decode(enc)
            msg = UXCMsg.from_bytes(dec)
        except cobs.CobsError as err:
            logger.debug(f"Failed to decode data: {err=}")
            return
        except UXCError as err:
            logger.debug(f"Failed to decode message: {err=}")
            return

        # Stop timer if running.
        if self._timer is not None:
            self._timer.cancel()

        duplicate_msg = True
        first_msg = False
        if (msg.hdr.flags & UXCFlags.FIRST_MSG) and (msg.hdr.send_seq_num == 1):
            first_msg = True

        if msg.payload and (first_msg or msg.hdr.send_seq_num != self._recv_seq_num):
            duplicate_msg = False
            self._recv_seq_num = msg.hdr.send_seq_num

            # Start ACK timer to send a pure ACK.
            logger.debug(f"Starting ACK timer: seq_num={self._recv_seq_num}")
            self._timer = Timer(self.ack_timeout, self._send_ack)
            self._timer.start()

        if (
            msg.hdr.flags & (UXCFlags.ACK | UXCFlags.NACK)
            and msg.hdr.ack_seq_num >= self._send_seq_num
        ):
            if msg.hdr.flags & UXCFlags.ACK:
                logger.debug(f"Received ACK for seq_num={msg.hdr.ack_seq_num}")
            self._ack_q.put(msg.hdr.flags & (UXCFlags.ACK | UXCFlags.NACK))

        if not duplicate_msg and msg.payload:
            proto = self.proto_recv()
            proto.ParseFromString(bytes(msg.payload))
            self.recv_cb(msg.hdr.proto_tag, proto)

    def _on_proto(self: UXCClient, proto_tag: int, proto: Message) -> None:
        """Default callback invoked on receipt of a proto message.

        :param self: the UXC client instance.
        :param proto_tag: the proto tag identifier.
        :param proto: the received proto.
        :returns: ``None``
        """
        field_name = proto.WhichOneof("msg")
        logger.debug(f"Received message: {proto_tag=} ({field_name}), {proto=}")
        self._recv_q[proto_tag].put(getattr(proto, field_name))

    def wait_for(
        self: UXCClient, proto_tag: Union[int, str], timeout: Optional[int] = 10
    ) -> Optional[Message]:
        """Waits up to ``timeout`` seconds for a proto with the given tag.

        :param self: the UXC client instance.
        :param proto_tag: the proto identifier to wait for.
        :param timeout: optional timeout (in seconds) to wait (default: 10s).
        :returns: ``None`` on timeout, otherwise the received proto message.
        :raises: ``ValueError`` if ``proto_tag`` is invalid.
        """
        if not isinstance(proto_tag, int):
            for index, field in self.proto_recv.DESCRIPTOR.fields_by_number.items():
                if field.name == proto_tag:
                    proto_tag = index
                    break
            else:
                raise ValueError(f"Could not find matching field for '{proto_tag}'")

        try:
            logger.debug(f"Waiting for message with proto tag: {proto_tag}")
            return self._recv_q[proto_tag].get(timeout=timeout)
        except queue.Empty:
            return None

    @property
    def proto_send(self: UXCClient) -> Message:
        """Returns the proto type to encode messages as for sending.

        :param self: the UXC client instance.
        :returns: Protobuf class definition.
        """
        raise NotImplementedError

    @property
    def proto_recv(self: UXCClient) -> Message:
        """Returns the proto type to decode messages as for receiving.

        :param self: the UXC client instance.
        :returns: Protobuf class definition.
        """
        raise NotImplementedError

    def send(
        self: UXCClient,
        proto: Optional[Union[uxc_pb2.uxc_msg_device, uxc_pb2.uxc_msg_host]] = None,
    ) -> bool:
        """Sends a proto message.

        :param self: the UXC client instance.
        :param proto: the UXC host or device proto to send.
        :returns: ``True`` if sent successfully, otherwise ``False``.
        """
        with self._lock:
            payload_bytes = proto.SerializeToString() if proto else None
            payload_len = len(payload_bytes) if payload_bytes else 0
            proto_tag = 0x00

            if proto:
                # Determine the proto tag by iterating through the descriptor fields.
                field_name = proto.WhichOneof("msg")

                for index, field in proto.DESCRIPTOR.fields_by_number.items():
                    if field.name == field_name:
                        proto_tag = index
                        break
                else:
                    raise ValueError(f"Unknown protobuf: {field_name}")

            next_send_seq_num = self._send_seq_num + 1
            if next_send_seq_num > 0xFF:
                # Sequence number is a UINT8 (range [1..255] inclusive).
                next_send_seq_num = 1
            self._send_seq_num = next_send_seq_num

            hdr = UXCMsgHdr()
            hdr.proto_tag = proto_tag
            hdr.send_seq_num = next_send_seq_num
            hdr.ack_seq_num = self._recv_seq_num
            hdr.flags = UXCFlags.ACK
            if not self._sent_first_msg:
                self._sent_first_msg = True
                hdr.flags |= UXCFlags.FIRST_MSG
            hdr.reserved = 0x00
            hdr.payload_len = payload_len
            hdr.crc = UXCMsg.compute_crc(hdr, payload_bytes)

            # Construct the message to send.
            msg = UXCMsg()
            msg.hdr = hdr
            msg.payload = payload_bytes

            # COBS encode message to send.
            enc = self.encoder.encode(bytes(msg))

            # Stop timer if running.
            if self._timer is not None:
                logger.debug("Stopping ACK timer.")
                self._timer.cancel()

            # Attempt to send message.
            for attempt in range(self.retransmit_count):
                logger.debug(
                    f"Sending message: seq_num={msg.hdr.send_seq_num}, ack={msg.hdr.ack_seq_num}, {attempt=}, {proto=}"
                )

                bytes_written = self.write(enc)
                if bytes_written:
                    logger.debug(f"Wrote {bytes_written} bytes.")

                if not payload_len:
                    # Pure ACK messages are not re-transmitted.
                    break

                try:
                    logger.debug(f"Waiting {self.retransmit_timeout}s for ACK")
                    ack = self._ack_q.get(timeout=self.retransmit_timeout)
                    if ack & UXCFlags.ACK:
                        # Message was sent successfully and ACK'd.
                        logger.debug(f"Message ACK'd: seq_num={msg.hdr.send_seq_num}")
                        return True
                    logger.debug(f"Message NACK'd: seq_num={msg.hdr.send_seq_num}")
                except queue.Empty:
                    logger.debug(
                        f"Timed out waiting for ACK: seq_num={msg.hdr.send_seq_num}"
                    )

        return False

    def start(self: UXCClient) -> None:
        """Starts the client's receive thread.

        :param self: the UXC client instance.
        :returns: ``None``
        """
        self.stop()
        self._stop = False
        self._thread = Thread(target=self._recv_thread)
        self._thread.daemon = True
        self._thread.start()

    def stop(self: UXCClient) -> None:
        """Stops the client's receive thread.

        :param self: the UXC client instance.
        :returns: ``None``
        """
        if self._thread:
            time.sleep(self.THREAD_STOP_DELAY_S)
            self._stop = True
            self._thread.join(timeout=self.READ_TIMEOUT_S)
            self._thread = None

    def get_cert(
        self: UXCClient, cert_type: wallet_pb2.cert_get_cmd.cert_type.ValueType
    ) -> bytes:
        """Requests a certificate from the connected client/host.

        :param self: the client instance.
        :param cert_type: the type of the certificate.
        :returns: Certificate bytes on success, otherwise ``None``.
        """
        msg = self.proto_send()
        cmd = wallet_pb2.cert_get_cmd()
        cmd.kind = cert_type
        msg.cert_get_cmd.CopyFrom(cmd)
        if self.send(msg):
            rsp = self.wait_for("cert_get_rsp")
            if rsp and rsp.rsp_status == wallet_pb2.cert_get_rsp.cert_get_rsp_status.SUCCESS:
                return rsp.cert
        return None

    def send_cert(
        self: UXCClient,
        cert: Union[bytes, bytearray],
        status: int = wallet_pb2.cert_get_rsp.cert_get_rsp_status.SUCCESS,
    ) -> bool:
        """Sends a certificate response to the connected client/host.

        :param self: the client instance.
        :param cert: the certificate bytes.
        :param status: status to set in the response (defaults to ``SUCCESS``).
        :returns: ``True`` if response sent successfully, otherwise ``False``.
        """
        msg = self.proto_send()
        rsp = wallet_pb2.cert_get_rsp()
        rsp.cert = cert if isinstance(cert, bytes) else bytes(cert)
        rsp.rsp_status = status
        msg.cert_get_rsp.CopyFrom(rsp)
        return self.send(msg)

    def send_boot_msg(
        self: UXCClient,
        mcu_id: int,
        auth_status: int = uxc_pb2.uxc_auth_status.UXC_AUTH_STATUS_UNAUTHENTICATED
    ) -> None:
        """Sends a boot status message to the companion MCU.

        :param self: the client instance.
        :param mcu_id: identifier of the MCU booting up.
        :param auth_status: authentication status of the MCU (default: un-authenticated).
        :returns: ``True`` if sent successfully, otherwise ``False``.
        """
        msg = self.proto_send()
        boot_status = uxc_pb2.uxc_boot_status_msg()
        boot_status.mcu_id = mcu_id
        boot_status.auth_status = auth_status
        msg.boot_status_msg.CopyFrom(boot_status)
        return self.send(msg)


class UXCHost(UXCClient):

    proto_send = uxc_pb2.uxc_msg_host
    proto_recv = uxc_pb2.uxc_msg_device

    def get_empty(self: UXCHost) -> bool:
        """Sends an empty request.

        :param self: the host instance.
        :returns: ``True`` if empty response received, otherwise ``False``.
        """
        msg = self.proto_send()
        cmd = wallet_pb2.empty_cmd()
        msg.empty_cmd.CopyFrom(cmd)
        if self.send(msg):
            if self.wait_for("empty_rsp"):
                return True
        return False

    def send_reset(self: UXCHost) -> None:
        """Sends a reset command.

        :param self: the host instance.
        :returns: ``None``
        """
        msg = self.proto_send()
        cmd = wallet_pb2.reset_cmd()
        msg.reset_cmd.CopyFrom(cmd)
        self.send(msg)

    def get_metadata(self: UXCHost) -> Optional[wallet_pb2.meta_rsp]:
        """Sends a request for firmware metadata.

        :param self: the host instance.
        :returns: Firmware metadata response on success, otherwise ``None``.
        """
        msg = self.proto_send()
        cmd = wallet_pb2.meta_cmd()
        msg.meta_cmd.CopyFrom(cmd)
        if self.send(msg):
            return self.wait_for("meta_rsp")
        return None

    def fwup(
        self: UXCHost,
        data: bytes,
        mode: wallet_pb2.fwup_mode.ValueType = wallet_pb2.fwup_mode.FWUP_MODE_NORMAL,
        bootloader_upgrade: bool = False,
        offset: int = 0,
        max_chunk_size: int = 448,
    ) -> wallet_pb2.fwup_finish_rsp.fwup_finish_rsp_status.ValueType:
        """Performs a firmware update iteratively.

        The ``mode`` can be used to configure the type of update being
        performed. This method will send each chunk of data, waiting for the
        responses until the entire FWUP has completed. Returns a
        ``fwup_finish_rsp_status`` on completion.

        Note that ``max_chunk_size`` must be a multiple of the minimum flash
        write size for the target.

        :param self: the host instance.
        :param data: the firmware update image data.
        :param mode: optional update mode (default: NORMAL).
        :param bootloader_upgrade: ``True`` if upgrading the bootloader (default: ``False``).
        :param offset: offset in the application slot to begin the update from (default: ``0``).
        :param max_chunk_size: maximum FWUP data chunk size (default: `448`).
        :returns: FWUP status code.
        :raises: ``NotImplementedError`` if update mode not supported.
        """
        if mode != wallet_pb2.fwup_mode.FWUP_MODE_NORMAL:
            # TODO: Implement support for other types of updates.
            raise NotImplementedError("Only normal updates are currently supported.")

        # Send the start command.
        cmd = wallet_pb2.fwup_start_cmd()
        cmd.mode = mode
        msg = self.proto_send()
        msg.fwup_start_cmd.CopyFrom(cmd)
        if not self.send(msg):
            return wallet_pb2.fwup_finish_rsp.fwup_finish_rsp_status.ERROR

        # Process the start response.
        rsp = self.wait_for("fwup_start_rsp")
        if not rsp:
            return wallet_pb2.fwup_finish_rsp.fwup_finish_rsp_status.ERROR
        elif rsp.rsp_status != wallet_pb2.fwup_start_rsp.fwup_start_rsp_status.SUCCESS:
            if (
                rsp.rsp_status
                == wallet_pb2.fwup_start_rsp.fwup_start_rsp_status.UNSPECIFIED
            ):
                return wallet_pb2.fwup_finish_rsp.fwup_finish_rsp_status.UNSPECIFIED
            elif (
                rsp.rsp_status
                == wallet_pb2.fwup_start_rsp.fwup_start_rsp_status.UNAUTHENTICATED
            ):
                return wallet_pb2.fwup_finish_rsp.fwup_finish_rsp_status.UNAUTHENTICATED
            return wallet_pb2.fwup_finish_rsp.fwup_finish_rsp_status.ERROR

        # Chunk and send the FWUP data.
        sequence_id = 0
        while len(data):
            chunk_size = min(max_chunk_size, len(data))
            chunk = data[: chunk_size]
            data = data[chunk_size :]

            cmd = wallet_pb2.fwup_transfer_cmd()
            cmd.sequence_id = sequence_id
            cmd.fwup_data = chunk
            cmd.offset = offset
            cmd.mode = mode
            msg = self.proto_send()
            msg.fwup_transfer_cmd.CopyFrom(cmd)

            # Increment sequence ID for the next chunk.
            sequence_id += 1

            rsp = self.wait_for("fwup_transfer_rsp") if self.send(msg) else None
            if not rsp:
                return wallet_pb2.fwup_finish_rsp.fwup_finish_rsp_status.ERROR
            elif (
                rsp.rsp_status
                != wallet_pb2.fwup_transfer_rsp.fwup_transfer_rsp_status.SUCCESS
            ):
                if (
                    rsp.rsp_status
                    == wallet_pb2.fwup_transfer_rsp.fwup_transfer_rsp_status.UNSPECIFIED
                ):
                    return wallet_pb2.fwup_finish_rsp.fwup_finish_rsp_status.UNSPECIFIED
                elif (
                    rsp.rsp_status
                    == wallet_pb2.fwup_transfer_rsp.fwup_transfer_rsp_status.UNAUTHENTICATED
                ):
                    return (
                        wallet_pb2.fwup_finish_rsp.fwup_finish_rsp_status.UNAUTHENTICATED
                    )
                return wallet_pb2.fwup_finish_rsp.fwup_finish_rsp_status.ERROR

        # Send the finish command.
        # TODO: Need to handle offset values for delta / oneshot updates.
        cmd = wallet_pb2.fwup_finish_cmd()
        cmd.bl_upgrade = bootloader_upgrade
        cmd.mode = mode
        msg = self.proto_send()
        msg.fwup_finish_cmd.CopyFrom(cmd)
        if not self.send(msg):
            return wallet_pb2.fwup_finish_rsp.fwup_finish_rsp_status.ERROR

        # Process the response.
        rsp = self.wait_for("fwup_finish_rsp")
        if not rsp:
            return wallet_pb2.fwup_finish_rsp.fwup_finish_rsp_status.ERROR
        return rsp.rsp_status

    def gpio_read(self: UXCHost, port: int, pin: int) -> Optional[int]:
        """Reads a GPIO pin's value.

        :param self: the host instance.
        :param port: the GPIO port for the pin.
        :param pin: the GPIO pin to read.
        :returns: GPIO pin input value on success, otherwise ``None``.
        """
        msg = self.proto_send()
        cmd = mfgtest_pb2.mfgtest_gpio_cmd()
        cmd.action = mfgtest_pb2.mfgtest_gpio_cmd.mfgtest_gpio_action.READ
        cmd.port = port
        cmd.pin = pin
        msg.mfgtest_gpio_cmd.CopyFrom(cmd)
        if self.send(msg):
            rsp = self.wait_for("mfgtest_gpio_rsp")
            if rsp:
                return rsp.output
        return None

    def gpio_set(self: UXCHost, port: int, pin: int) -> Optional[int]:
        """Sets a GPIO.

        :param self: the host instance.
        :param port: the GPIO port for the pin.
        :param pin: the GPIO pin to set.
        :returns: GPIO pin value on success, otherwise ``None``.
        """
        msg = self.proto_send()
        cmd = mfgtest_pb2.mfgtest_gpio_cmd()
        cmd.action = mfgtest_pb2.mfgtest_gpio_cmd.mfgtest_gpio_action.SET
        cmd.port = port
        cmd.pin = pin
        msg.mfgtest_gpio_cmd.CopyFrom(cmd)
        if self.send(msg):
            rsp = self.wait_for("mfgtest_gpio_rsp")
            if rsp:
                return rsp.output
        return None

    def gpio_clear(self: UXCHost, port: int, pin: int) -> Optional[int]:
        """Clears a GPIO.

        :param self: the host instance.
        :param port: the GPIO port for the pin.
        :param pin: the GPIO pin to clear.
        :returns: GPIO pin value on success, otherwise ``None``.
        """
        msg = self.proto_send()
        cmd = mfgtest_pb2.mfgtest_gpio_cmd()
        cmd.action = mfgtest_pb2.mfgtest_gpio_cmd.mfgtest_gpio_action.CLEAR
        cmd.port = port
        cmd.pin = pin
        msg.mfgtest_gpio_cmd.CopyFrom(cmd)
        if self.send(msg):
            rsp = self.wait_for("mfgtest_gpio_rsp")
            if rsp:
                return rsp.output
        return None

    def get_coredump_count(self: UXCHost) -> Optional[int]:
        """Retrieves the count of coredumps from the connected device.

        :param self: the host instance.
        :returns: Numeric count on succes, otherwise ``None``.
        """
        msg = self.proto_send()
        cmd = wallet_pb2.coredump_get_cmd()
        cmd.type = wallet_pb2.coredump_get_cmd.coredump_get_type.COUNT
        msg.coredump_get_cmd.CopyFrom(cmd)
        if self.send(msg):
            rsp = self.wait_for("coredump_get_rsp")
            if (
                rsp
                and rsp.rsp_status
                == wallet_pb2.coredump_get_rsp.coredump_get_rsp_status.SUCCESS
            ):
                return rsp.coredump_count
        return None

    def get_coredump(self: UXCHost) -> Optional[Tuple[int, bytes]]:
        """Retrieves the first available coredump from the connected device.

        After this method is called, the coredump will be erased from the
        device. If the request is not successful, then ``None`` is returned.

        :param self: the host instance.
        :returns: (number of coredumps remaining, coredump bytes).
        """
        coredump_count = self.get_coredump_count()
        if not coredump_count:
            return None

        coredump = b""
        offset = 0
        while True:
            msg = self.proto_send()
            cmd = wallet_pb2.coredump_get_cmd()
            cmd.type = wallet_pb2.coredump_get_cmd.coredump_get_type.COREDUMP
            cmd.offset = offset
            msg.coredump_get_cmd.CopyFrom(cmd)
            if self.send(msg):
                rsp = self.wait_for("coredump_get_rsp")
                if (
                    rsp
                    and rsp.rsp_status
                    == wallet_pb2.coredump_get_rsp.coredump_get_rsp_status.SUCCESS
                ):
                    coredump += rsp.coredump_fragment.data
                    offset = rsp.coredump_fragment.offset
                    if rsp.coredump_fragment.complete:
                        return (rsp.coredump_fragment.coredumps_remaining, coredump)
                else:
                    logger.error("Coredump request failed.")
                    break
            else:
                logger.error("Failed to send coredump request.")
                break
        return None

    def get_events(self: UXCHost) -> Tuple[int, bytes]:
        """Requests events from the connected device.

        On failure, ``(None, b"")`` is returned.

        :param self: the host instance.
        :returns: (version, data)
        """
        version = None
        event_buffer = b""
        remaining_size = 1
        while remaining_size:
            msg = self.proto_send()
            cmd = wallet_pb2.events_get_cmd()
            msg.events_get_cmd.CopyFrom(cmd)
            if not self.send(msg):
                logger.error("Failed to send event request.")
                break

            event_rsp = self.wait_for("events_get_rsp")
            if not event_rsp:
                logger.error("Timed out waiting for event response.")
                break

            if event_rsp.rsp_status != wallet_pb2.events_get_rsp.events_get_rsp_status.SUCCESS:
                logger.error(f"Get event failed: status={event_rsp.rsp_status}")
                break

            event_buffer += event_rsp.fragment.data
            remaining_size = event_rsp.fragment.remaining_size
            version = event_rsp.version

        return (version, event_buffer)


class UXCDevice(UXCClient):

    proto_send = uxc_pb2.uxc_msg_device
    proto_recv = uxc_pb2.uxc_msg_host

    def send_empty(self: UXCDevice) -> bool:
        """Sends an empty response.

        :param self: the device instance.
        :returns: ``True`` if response successfully sent, otherwise ``False``.
        """
        msg = self.proto_send()
        rsp = wallet_pb2.empty_rsp()
        msg.empty_rsp.CopyFrom(rsp)
        return self.send(msg)

    def send_metadata(
        self: UXCDevice,
        bl: wallet_pb2.firmware_metadata,
        fw_a: wallet_pb2.firmware_metadata,
        fw_b: wallet_pb2.firmware_metadata,
        active_slot: wallet_pb2.firmware_slot.ValueType,
        status: wallet_pb2.meta_rsp.meta_rsp_status.ValueType = wallet_pb2.meta_rsp.meta_rsp_status.SUCCESS,
    ) -> bool:
        """Sends firmware metadata to the host.

        :param self: the device instance.
        :param bl: the metadata for the bootloader application.
        :param fw_a: the metadata for the slot A firmware application.
        :param fw_b: the metadata for the slot B firmware application.
        :param active_slot: identifier of the active slot firmware application.
        :returns: ``True`` if response successfully send, otherwise ``False``.
        """
        msg = self.proto_send()
        rsp = wallet_pb2.meta_rsp()
        rsp.meta_bl.CopyFrom(bl)
        rsp.meta_slot_a.CopyFrom(fw_a)
        rsp.meta_slot_b.CopyFrom(fw_b)
        rsp.active_slot = active_slot
        rsp.rsp_status = status
        msg.meta_rsp.CopyFrom(rsp)
        return self.send(msg)

    def send_fwup_start(
        self: UXCDevice,
        status: wallet_pb2.fwup_start_rsp.fwup_start_rsp_status.ValueType,
    ) -> bool:
        """Sends a FWUP start response to the connected host.

        :param self: the device instance.
        :param status: status to set in the response proto.
        :returns: ``True`` if response sent successfully, otherwise ``False``.
        """
        msg = self.proto_send()
        rsp = wallet_pb2.fwup_start_rsp()
        rsp.rsp_status = status
        msg.fwup_start_rsp.CopyFrom(rsp)
        return self.send(msg)

    def send_fwup_transfer(
        self: UXCDevice,
        status: wallet_pb2.fwup_transfer_rsp.fwup_transfer_rsp_status.ValueType,
    ) -> bool:
        """Sends a FWUP transfer response to the connected host.

        :param self: the device instance.
        :param status: status to set in the response proto.
        :returns: ``True`` if response sent successfully, otherwise ``False``.
        """
        msg = self.proto_send()
        rsp = wallet_pb2.fwup_transfer_rsp()
        rsp.rsp_status = status
        msg.fwup_transfer_rsp.CopyFrom(rsp)
        return self.send(msg)

    def send_fwup_finish(
        self: UXCDevice,
        status: wallet_pb2.fwup_finish_rsp.fwup_finish_rsp_status.ValueType,
    ) -> bool:
        """Sends a FWUP finish response to the connected host.

        :param self: the device instance.
        :param status: status to set in the response proto.
        :returns: ``True`` if response sent successfully, otherwise ``False``.
        """
        msg = self.proto_send()
        rsp = wallet_pb2.fwup_finish_rsp()
        rsp.rsp_status = status
        msg.fwup_finish_rsp.CopyFrom(rsp)
        return self.send(msg)

    def send_gpio_status(self: UXCDevice, value: int) -> bool:
        """Sends a GPIO response to the connected host.

        :param self: the device instance.
        :param value: command status (`0` / `1`) or GPIO value (`0` / `1`).
        :returns: ``True`` if response sent, otherwise ``False``
        """
        msg = self.proto_send()
        rsp = mfgtest_pb2.mfgtest_gpio_rsp()
        rsp.output = value
        msg.mfgtest_gpio_rsp.CopyFrom(rsp)
        return self.send(msg)

    def send_coredump_count(
        self: UXCDevice,
        coredump_count: int,
        status: int = wallet_pb2.coredump_get_rsp.coredump_get_rsp_status.SUCCESS,
    ) -> bool:
        """Sends a coredump count response to the connected host.

        :param self: the device instance.
        :param coredump_count: number of coredumps stored on the device.
        :param status: optional response status (default: SUCCESS).
        :returns: ``True`` if response sent successfully, otherwise ``False``.
        """
        msg = self.proto_send()
        rsp = wallet_pb2.coredump_get_rsp()
        rsp.rsp_status = status
        rsp.coredump_count = coredump_count
        msg.coredump_get_rsp.CopyFrom(rsp)
        return self.send(msg)

    def send_coredump_fragment(
        self: UXCDevice,
        data: bytes,
        offset: int,
        complete: bool,
        remaining_coredumps: int,
        status: int = wallet_pb2.coredump_get_rsp.coredump_get_rsp_status.SUCCESS,
    ) -> bool:
        """Sends a coredump data fragment to the connected host.

        :param self: the device instance.
        :param data: the coredump fragment data.
        :param offset: offset within the entire coredump that the data starts from.
        :param complete: ``True`` if this is the last fragment of the coredump, else ``False``.
        :param remaining_coredumps: Remaining number of coredumps on the device.
        :param status: optional response status (default: SUCCESS).
        :returns: ``True`` if response sent successfully, otherwise ``False``.
        """
        fragment = wallet_pb2.coredump_fragment()
        fragment.data = data
        fragment.offset = offset
        fragment.complete = complete
        fragment.coredumps_remaining = remaining_coredumps

        rsp = wallet_pb2.coredump_get_rsp()
        rsp.rsp_status = status
        rsp.coredump_fragment.CopyFrom(fragment)
        if not complete:
            # Add 1 to the total count to account for this coredump.
            rsp.coredump_count = 1 + remaining_coredumps
        else:
            rsp.coredump_count = remaining_coredumps

        msg = self.proto_send()
        msg.coredump_get_rsp.CopyFrom(rsp)
        return self.send(msg)

    def send_event_data(
        self: UXCDevice,
        data: bytes,
        remaining_bytes: int,
        version: int,
        status: int = wallet_pb2.events_get_rsp.events_get_rsp_status.SUCCESS,
    ) -> bool:
        """Sends event data to the connected host.

        :param self: the device instance.
        :param data: the event fragment data.
        :param remaining_bytes: remaining number of bytes in the event buffer.
        :param version: event version.
        :param status: optional response status (default: SUCCESS).
        :returns: ``True`` if response sent successfully, otherwise ``False``.
        """
        fragment = wallet_pb2.event_fragment()
        fragment.data = data
        fragment.remaining_size = remaining_bytes

        rsp = wallet_pb2.events_get_rsp()
        rsp.rsp_status = status
        rsp.version = version
        rsp.fragment.CopyFrom(fragment)

        msg = self.proto_send()
        msg.events_get_rsp.CopyFrom(rsp)
        return self.send(msg)


def _cli_add_options(group: click.Group, cls: UXCClient) -> None:
    """Recursively iterates through the passed class to dynamically define click commands.

    :param group: the click group to add the commands to.
    :param cls: the derived ``UXCClient`` class.
    :returns: Annotated click group.
    """

    def _create_command(name: str, member: callable) -> click.Command:
        """Dynamically creates a click command.

        :param name: function name.
        :param member: callable function.
        :returns: Generate click command.
        """

        signature = inspect.signature(member)
        default_args = dict(
            (
                (k, v.default)
                for k, v in signature.parameters.items()
                if v.default is not inspect.Parameter.empty
            )
        )

        def _command(command_name: str, help_str: str) -> click.Command:

            @click.command(name=command_name, help=help_str)
            @click.pass_context
            def __command(ctx, *args, **kwargs):
                method_name = ctx.command.name.replace("-", "_")
                with ctx.obj as obj:
                    func = getattr(obj, method_name)
                    new_args = []

                    # Remove the output file from the key-word argument list.
                    ofile = kwargs.pop("output_file", None)

                    # Read all the data from any click file objects.
                    for arg in args:
                        if hasattr(arg, "read"):
                            new_args.append(arg.read())
                        else:
                            new_args.append(arg)

                    for (key, value) in kwargs.items():
                        if hasattr(value, "read"):
                            kwargs[key] = value.read()

                    # Call the function and store the result.
                    result = func(*new_args, **kwargs)

                    # If an output file is given, write the output to the file,
                    # otherwise print it out.
                    data = None
                    if result and ofile is not None:
                        if isinstance(result, collections.abc.Iterable):
                            for r in result:
                                if isinstance(r, bytes) or isinstance(r, bytearray):
                                    data = r
                                    break
                        elif isinstance(result, bytes) or isinstance(result, bytearray):
                            data = result

                    if data is not None:
                        ofile.write(data)
                        bytes_written = len(data)
                        click.echo(f"Wrote {bytes_written} bytes to: {ofile.name}")
                    elif isinstance(result, bool):
                        click.echo("Success" if result else "Failed")
                    else:
                        click.echo(str(result))

            return __command

        # Generate the command. This has to happen before the arguments are bound.
        command = _command(name.replace("_", "-"), member.__doc__.splitlines()[0])

        # Add the arguments and options.
        for param_name, param_type in get_type_hints(member).items():
            if param_name == "self":
                # Exclude self-reference from the arguments / options.
                continue

            param_default = default_args.get(param_name, None)
            param_arg_types = list(getattr(param_type, "__args__", [])) + [param_type]
            is_bytes_type = any((atype == bytes) or (atype == bytearray) for atype in param_arg_types)
            metavar = None
            if param_name == "return":
                if not is_bytes_type:
                    continue
                # Replace bytes return types with file types.
                param_type = click.File("wb")
                param_name = "output_file"
            elif is_bytes_type:
                # Replace bytes parameter types with file types.
                param_type = click.File("rb")
                metavar = f"{param_name}_file".upper()

            if param_default is not None:
                # Add as an option.
                param_name = param_name.replace("_", "-")
                option = click.option(
                    f"--{param_name}", type=param_type, default=param_default, metavar=metavar
                )
                command = option(command)
            else:
                argument = click.argument(f"{param_name}", type=param_type, metavar=metavar)
                command = argument(command)

        return command

    # Functions to exclude from the available commands.
    _exclude_functions = ["send", "recv", "start", "stop", "wait_for"]

    # Dynamically add commands to the click group.
    for name, member in inspect.getmembers(cls):
        if not inspect.isfunction(member):
            # Exclude non-callable members.
            continue
        elif name.startswith("_") or name in _exclude_functions:
            # Exclude private functions.
            continue

        command = _create_command(name, member)
        group.add_command(command)


@click.group(invoke_without_command=True)
@click.option(
    "--pids",
    default=None,
    multiple=True,
    type=int,
    help="optional list of PIDS to identify the serial device.",
)
@click.option(
    "--vids",
    default=None,
    multiple=True,
    type=int,
    help="optional list of VIDs to identify the serial device.",
)
@click.option("-b", "--baudrate", default=2000000, type=int, help="serial baud rate")
@click.option(
    "-v",
    "--verbose",
    default=0,
    count=True,
    help="increase verbosity of logging output",
)
@click.pass_context
def cli(
    ctx: click.Context,
    baudrate: int,
    vids: Optional[int],
    pids: Optional[int],
    verbose: bool,
) -> None:
    """Command line interface for the UXC CLI"""
    # Remove the previously bound logging handlers.
    for handler in logging.root.handlers[:]:
        logging.root.removeHandler(handler)

    if verbose >= 2:
        level = logging.DEBUG
    elif verbose == 1:
        level = logging.INFO
    else:
        level = logging.CRITICAL

    fmt = "%(asctime)s [%(name)s] %(message)s"
    logging.basicConfig(format=fmt, datefmt="%H:%M:%S", level=level)

    def _connect(cls: UXCClient) -> callable:

        @contextlib.contextmanager
        def __connect():
            try:
                with comms.SerialTransport(
                    baudrate=baudrate, vids=(vids or None), pids=(pids or None)
                ) as transport:
                    with cls(write=transport.write, read=transport.read) as instance:
                        yield instance
            finally:
                pass

        return __connect()

    ctx.obj = _connect


@cli.group()
@click.pass_context
def host(ctx: click.Context) -> None:
    """Commands for interfacing with the UXC as the Core."""
    ctx.obj = ctx.obj(UXCHost)


_cli_add_options(host, UXCHost)


@cli.group()
@click.pass_context
def device(ctx: click.Context) -> None:
    """Commands for interfacing with the Core as the UXC."""
    ctx.obj = ctx.obj(UXCDevice)


_cli_add_options(device, UXCDevice)


__all__ = [
    "UXCMsgHdr",
    "UXCMsg",
    "UXCHost",
    "UXCDevice",
    "cli",
]


if __name__ == "__main__":
    cli()

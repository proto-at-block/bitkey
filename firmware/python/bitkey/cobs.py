"""
nano Consistent Overhead Byte Stuffing (nanoCOBS)

This module provides a Python implementation of the nanocobs library.
"""

from __future__ import annotations

from typing import List, Optional, Tuple, Union

# Frame delimiter used to mark the end of COBS-encoded data
FRAME_DELIMITER = 0x00


class CobsError(Exception):
    """Base exception for COBS-related errors."""

    pass


class CobsEncoder:

    dst: bytearray
    dst_max: int
    cur: int
    code_idx: int
    code: int
    need_advance: bool

    @staticmethod
    def encode(data: Union[bytes, bytearray, List[int]]) -> bytes:
        """Encodes data using COBS encoding.

        :param data: input data to encode.
        :returns: COBS-encoded data, including delimiter.
        :raises: ``CobsError`` on encoding failure.
        """
        if not isinstance(data, (bytes, bytearray, list)):
            raise TypeError(f"Data must be bytes, bytearray or list of integers: given '{type(data)}'")

        # Convert to bytes if needed
        if isinstance(data, list):
            data = bytes(data)

        encoder = CobsEncoder()
        encoder.encode_inc_begin()
        encoder.encode_inc(data)
        return encoder.encode_inc_end()

    def encode_inc_begin(self: CobsEncoder) -> None:
        """Begin an incremental COBS encoding operation.

        :param self: the encoder instance.
        :returns: ``None``
        """
        self.cur = 1
        self.code_idx = 0
        self.code = 1
        self.need_advance = 0
        self.dst = bytearray(2)

    def encode_inc(self: CobsEncoder, data: Union[bytes, bytearray]) -> None:
        """Continues an incremental COBS encoding operation.

        :param self: the encoder instance.
        :param data: input data to encode.
        :returns: ``None``
        :raises:: ``CobsError`` if encoding fails.
        """
        if not data:
            return

        dst_idx = self.cur
        dst_code_idx = self.code_idx
        code = self.code
        need_advance = self.need_advance

        if need_advance:
            dst_idx += 1
            if dst_idx >= len(self.dst):
                self.dst.append(0x00)
            need_advance = 0

        for byte in data:
            if byte:
                self.dst[dst_idx] = byte
                dst_idx += 1
                if dst_idx >= len(self.dst):
                    self.dst.append(0x00)
                code += 1

            if (byte == 0) or (code == 0xFF):
                self.dst[dst_code_idx] = code
                dst_code_idx = dst_idx
                code = 1

                if byte == 0 or len(data) > 1:
                    dst_idx += 1
                    if dst_idx >= len(self.dst):
                        self.dst.append(0x00)
                else:
                    need_advance = True

        self.cur = dst_idx
        self.code = code
        self.code_idx = dst_code_idx
        self.need_advance = need_advance

    def encode_inc_end(self: CobsEncoder) -> bytes:
        """Finishes an incremental COBS encoding operation.

        :param self: the encoder isntance.
        :returns: encoded bytes.
        """
        cur = self.cur

        if self.code_idx >= len(self.dst):
            self.dst.append(self.code)
        else:
            self.dst[self.code_idx] = self.code

        if cur >= len(self.dst):
            self.dst.append(FRAME_DELIMITER)
        else:
            self.dst[cur] = FRAME_DELIMITER
        return self.dst[: cur + 1]


class CobsDecoder:

    state: int
    block: int
    code: int
    cur: int
    dec_dst: bytearray

    # Decode states
    DECODE_READ_CODE = 0
    DECODE_RUN = 1
    DECODE_FINISH_RUN = 2

    @staticmethod
    def decode(data: Union[bytes, bytearray]) -> bytes:
        """Decodes COBS-encoded data.

        :param data: COBS-encoded input data
        :returns: decoded data.
        :raises: ``CobsError` if decoding fails or input is invalid.
        """
        if len(data) < 2:
            raise CobsError("Input data too short (must be at least 2 bytes)")

        # Allocate max possible decoded size
        decoder = CobsDecoder()
        decoder.decode_inc_begin()

        _, dec = decoder.decode_inc(data)
        if dec is None:
            raise CobsError("Incomplete decode")

        return dec

    def decode_inc_begin(self: CobsDecoder) -> None:
        """Begin an incremental COBS decoding operation.

        :param self: the decoder instance.
        :returns: ``None``.
        """
        self.state = self.DECODE_READ_CODE
        self.block = 0
        self.code = 0
        self.cur = 0
        self.dec_dst = bytearray()

    def decode_inc(
        self: CobsDecoder, enc_src: Union[bytes, bytearray]
    ) -> Tuple[int, Optional[bytes]]:
        """Continues an incremental COBS decoding operation.

        :param enc_src: Encoded input data.
        :returns: Tuple of (bytes read from source, decoded bytes if complete).
        :raises: ``CobsError`` If decoding fails or input is invalid.
        """
        src_idx = 0
        decode_complete = False

        while src_idx < len(enc_src):
            if self.state == self.DECODE_READ_CODE:
                self.block = self.code = enc_src[src_idx]
                src_idx += 1
                self.state = self.DECODE_RUN

            elif self.state == self.DECODE_FINISH_RUN:
                if enc_src[src_idx] == 0:
                    decode_complete = True
                    break

                if self.code != 0xFF:
                    if self.cur >= len(self.dec_dst):
                        self.dec_dst.append(0)
                    else:
                        self.dec_dst[self.cur] = 0
                    self.cur += 1

                self.state = self.DECODE_READ_CODE

            elif self.state == self.DECODE_RUN:
                while self.block - 1:
                    if src_idx >= len(enc_src):
                        break

                    self.block -= 1
                    b = enc_src[src_idx]
                    src_idx += 1

                    if b == 0:
                        raise CobsError("Invalid zero byte in payload")

                    if self.cur >= len(self.dec_dst):
                        self.dec_dst.append(b)
                    else:
                        self.dec_dst[self.cur] = b
                    self.cur += 1

                if self.block == 1:
                    self.state = self.DECODE_FINISH_RUN

        return src_idx, self.dec_dst if decode_complete else None


__all__ = [
    "CobsDecoder",
    "CobsEncoder",
    "CobsError",
]

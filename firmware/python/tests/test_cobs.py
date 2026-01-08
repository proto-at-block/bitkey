"""Test cases for COBS encoding/decoding."""

import unittest

from bitkey import cobs


class TestCOBS(unittest.TestCase):
    """Test cases for COBS encoding and decoding."""

    def test_basic_encoding(self):
        """Test basic encoding of simple data."""
        encoder = cobs.CobsEncoder()

        # Test empty data
        self.assertEqual(encoder.encode(b""), b"\x01\x00")

        # Test single byte
        self.assertEqual(encoder.encode(b"\x01"), b"\x02\x01\x00")

        # Test data with no zeros
        self.assertEqual(encoder.encode(b"\x01\x02\x03"), b"\x04\x01\x02\x03\x00")

        # Test data with zeros
        self.assertEqual(encoder.encode(b"\x00"), b"\x01\x01\x00")
        self.assertEqual(encoder.encode(b"\x00\x00"), b"\x01\x01\x01\x00")
        self.assertEqual(
            encoder.encode(b"\x11\x22\x00\x33"), b"\x03\x11\x22\x02\x33\x00"
        )

    def test_basic_decoding(self):
        """Test basic decoding of simple data."""
        # Test empty data
        self.assertEqual(cobs.CobsDecoder.decode(b"\x01\x00"), b"")

        # Test single byte
        self.assertEqual(cobs.CobsDecoder.decode(b"\x02\x01\x00"), b"\x01")

        # Test data with no zeros
        self.assertEqual(
            cobs.CobsDecoder.decode(b"\x04\x01\x02\x03\x00"), b"\x01\x02\x03"
        )

        # Test data with zeros
        self.assertEqual(cobs.CobsDecoder.decode(b"\x01\x01\x00"), b"\x00")
        self.assertEqual(cobs.CobsDecoder.decode(b"\x01\x01\x01\x00"), b"\x00\x00")
        self.assertEqual(
            cobs.CobsDecoder.decode(b"\x03\x11\x22\x02\x33\x00"), b"\x11\x22\x00\x33"
        )

    def test_incremental_encoding(self):
        """Test incremental encoding."""
        data = b"\x11\x22\x00\x33"
        expected = b"\x03\x11\x22\x02\x33\x00"

        # Encode in chunks
        encoder = cobs.CobsEncoder()
        encoder.encode_inc_begin()
        encoder.encode_inc(data[:2])  # \x11\x22
        encoder.encode_inc(data[2:])  # \x00\x33
        out = encoder.encode_inc_end()

        self.assertEqual(out, expected)

    def test_incremental_decoding(self):
        """Test incremental decoding."""
        encoded = b"\x03\x11\x22\x02\x33\x00"
        expected = b"\x11\x22\x00\x33"

        # Decode in chunks
        decoder = cobs.CobsDecoder()
        decoder.decode_inc_begin()

        # First chunk
        src_len, dec = decoder.decode_inc(encoded[:3])
        self.assertEqual(3, src_len)
        self.assertIsNone(dec)

        # Second chunk
        src_len, dec = decoder.decode_inc(encoded[src_len:])
        self.assertEqual(len(encoded) - 4, src_len)

        self.assertEqual(dec, expected)

    def test_error_cases(self):
        """Test various error conditions."""
        # Invalid input type
        with self.assertRaises(TypeError):
            encoder = cobs.CobsEncoder()
            encoder.encode("not bytes")

        # Input too short for decode
        with self.assertRaises(cobs.CobsError):
            cobs.CobsDecoder.decode(b"\x00")

        # Invalid zero in payload
        with self.assertRaises(cobs.CobsError):
            cobs.CobsDecoder.decode(b"\x02\x00\x00")

    def test_large_blocks(self):
        """Test encoding/decoding of large blocks."""
        # Create data block of 254 non-zero bytes
        data = bytes(range(1, 255))
        encoded = cobs.CobsEncoder.encode(data)
        decoded = cobs.CobsDecoder.decode(encoded)
        self.assertEqual(data, decoded)

        # Test block that requires multiple 0xFF codes
        data = bytes([1] * 254 + [0] + [2] * 254)
        encoded = cobs.CobsEncoder.encode(data)
        decoded = cobs.CobsDecoder.decode(encoded)
        self.assertEqual(data, decoded)


if __name__ == "__main__":
    unittest.main()

"""Test cases for the FWUP module."""

from __future__ import annotations

import unittest

from bitkey import fwup


class TestFwup(unittest.TestCase):
    """Test cases for the FWUP module."""

    def test_fwup_params_from_product(self):
        """Validates that the expected FWUP parameters are returned per product.

        Parameters are based on the `partitions.yml` for the target.
        """
        params = fwup.FwupParams.from_product("w1a")
        self.assertEqual(params.version, 0)
        self.assertEqual(params.signature_offset, ((632 * 1024) - 64))
        self.assertEqual(params.app_props_offset, 1024)
        self.assertEqual(params.signature_size, 64)
        self.assertEqual(params.chunk_size, 452)

        params = fwup.FwupParams.from_product("w3a-core")
        self.assertEqual(params.version, 0)
        self.assertEqual(params.signature_offset, ((632 * 1024) - 64))
        self.assertEqual(params.app_props_offset, 1024)
        self.assertEqual(params.signature_size, 64)
        self.assertEqual(params.chunk_size, 452)

        params = fwup.FwupParams.from_product("w3a-uxc")
        self.assertEqual(params.version, 0)
        self.assertEqual(params.signature_offset, ((896 * 1024) - 64))
        self.assertEqual(params.app_props_offset, 1024)
        self.assertEqual(params.signature_size, 64)
        self.assertEqual(params.chunk_size, 448)

        params = fwup.FwupParams.from_product("w4")
        self.assertIsNone(params)


if __name__ == "__main__":
    unittest.main()

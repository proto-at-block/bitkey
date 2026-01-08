"""Test cases for the wallet module."""

from __future__ import annotations

import unittest
import unittest.mock

import bitkey.wallet
from bitkey.fwup import FwupParams


class TestWallet(unittest.TestCase):
    """Test cases for the Wallet module APIs."""

    def setUp(self: TestWallet) -> None:
        """Pre-test setup tasks."""
        self.comms = unittest.mock.Mock()

    def test_fwup_params(self: Wallet) -> None:
        """Test cases for the ``fwup_params`` API."""
        wallet = bitkey.wallet.Wallet(self.comms, product="w1")
        params = wallet.fwup_params()
        self.assertIsInstance(params, FwupParams)
        self.assertEqual(452, params.chunk_size)

        wallet = bitkey.wallet.Wallet(self.comms, product="w1")
        params = wallet.fwup_params("efr32")
        self.assertIsInstance(params, FwupParams)
        self.assertEqual(452, params.chunk_size)

        wallet = bitkey.wallet.Wallet(self.comms, product="w3")
        params = wallet.fwup_params()
        self.assertIsInstance(params, FwupParams)
        self.assertEqual(452, params.chunk_size)

        wallet = bitkey.wallet.Wallet(self.comms, product="w3")
        params = wallet.fwup_params("efr32")
        self.assertIsInstance(params, FwupParams)
        self.assertEqual(452, params.chunk_size)

        wallet = bitkey.wallet.Wallet(self.comms, product="w3")
        params = wallet.fwup_params("stm32u5")
        self.assertIsInstance(params, FwupParams)
        self.assertEqual(448, params.chunk_size)


if __name__ == "__main__":
    unittest.main()

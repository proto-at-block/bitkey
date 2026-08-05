"""Test cases for the comms module."""

from __future__ import annotations

import unittest
import unittest.mock

from bitkey import comms
from bitkey_proto import wallet_pb2 as wallet_pb


class TestComms(unittest.TestCase):
    """Test cases for the `comms` module."""

    def test_port_spec_to_usb_device(self: TestComms) -> None:
        """Test case for the USB spec conversion function."""
        transaction = comms.NFCTransaction
        self.assertEqual("usb", transaction.port_spec_to_usb_device("usb"))
        self.assertEqual(
            "usb:003:009", transaction.port_spec_to_usb_device("usb:003:009")
        )
        self.assertEqual(
            "usb:054c:02e1", transaction.port_spec_to_usb_device(
                "usb:054c:02e1")
        )
        self.assertEqual(
            "usb:/dev/hidraw0", transaction.port_spec_to_usb_device(
                "/dev/hidraw0")
        )

        with unittest.mock.patch("bitkey.comms.util.usb_dev_from_port") as mock_dev_from_port:
            mock_dev_from_port.return_value = None

            with self.assertRaises(RuntimeError):
                transaction.port_spec_to_usb_device("1-6.4.4.2")

            mock_dev_from_port.return_value = [500, 199]
            self.assertEqual(
                "usb:500:199", transaction.port_spec_to_usb_device(
                    "3-6.4.4.4.2")
            )

    def test_response_tag_for_command_special_cases(self: TestComms) -> None:
        """Validates command->response mapping for non-standard names."""
        cmd = wallet_pb.wallet_cmd()
        cmd.derive_key_descriptor_cmd.SetInParent()
        self.assertEqual(
            wallet_pb.wallet_rsp.DESCRIPTOR.fields_by_name["derive_rsp"].number,
            comms.WalletComms.response_tag_for_command(cmd),
        )

        if "sign_tx_request_cmd" in wallet_pb.wallet_cmd.DESCRIPTOR.fields_by_name:
            cmd = wallet_pb.wallet_cmd()
            cmd.sign_tx_request_cmd.SetInParent()
            self.assertEqual(
                wallet_pb.wallet_rsp.DESCRIPTOR.fields_by_name["sign_tx_response"].number,
                comms.WalletComms.response_tag_for_command(cmd),
            )

    def test_send_retries_until_response_tag_matches(self: TestComms) -> None:
        wallet_comms = comms.WalletComms(transport=unittest.mock.Mock())
        wallet_comms.send_retry_max = 3

        cmd = wallet_pb.wallet_cmd()
        cmd.device_id_cmd.SetInParent()
        expected_tag = comms.WalletComms.response_tag_for_command(cmd)

        wrong_rsp = wallet_pb.wallet_rsp()
        wrong_rsp.meta_rsp.SetInParent()
        right_rsp = wallet_pb.wallet_rsp()
        right_rsp.device_id_rsp.SetInParent()

        wallet_comms._transceive_once = unittest.mock.Mock(
            side_effect=[wrong_rsp, right_rsp]
        )
        rsp = wallet_comms.send(cmd, expected_tag, timeout=2)

        self.assertIs(rsp, right_rsp)
        self.assertEqual(2, wallet_comms._transceive_once.call_count)
        wallet_comms._transceive_once.assert_called_with(cmd, timeout=2)

    def test_send_raises_after_retry_exhaustion(self: TestComms) -> None:
        wallet_comms = comms.WalletComms(transport=unittest.mock.Mock())
        wallet_comms.send_retry_max = 2

        cmd = wallet_pb.wallet_cmd()
        cmd.device_id_cmd.SetInParent()
        expected_tag = comms.WalletComms.response_tag_for_command(cmd)

        wrong_rsp = wallet_pb.wallet_rsp()
        wrong_rsp.meta_rsp.SetInParent()
        wallet_comms._transceive_once = unittest.mock.Mock(
            side_effect=[wrong_rsp, wrong_rsp]
        )

        with self.assertRaises(IOError):
            wallet_comms.send(cmd, expected_tag)

    def test_send_accepts_status_only_response(self: TestComms) -> None:
        wallet_comms = comms.WalletComms(transport=unittest.mock.Mock())

        cmd = wallet_pb.wallet_cmd()
        cmd.fwup_start_cmd.SetInParent()
        expected_tag = comms.WalletComms.response_tag_for_command(cmd)

        status_only_rsp = wallet_pb.wallet_rsp()
        status_only_rsp.status = wallet_pb.status.CONFIRMATION_PENDING
        wallet_comms._transceive_once = unittest.mock.Mock(
            return_value=status_only_rsp)

        rsp = wallet_comms.send(cmd, expected_tag)
        self.assertIs(rsp, status_only_rsp)

    def test_send_accepts_status_only_response_for_wipe_state(self: TestComms) -> None:
        wallet_comms = comms.WalletComms(transport=unittest.mock.Mock())

        cmd = wallet_pb.wallet_cmd()
        cmd.wipe_state_cmd.SetInParent()
        expected_tag = comms.WalletComms.response_tag_for_command(cmd)

        status_only_rsp = wallet_pb.wallet_rsp()
        status_only_rsp.status = wallet_pb.status.CONFIRMATION_PENDING
        wallet_comms._transceive_once = unittest.mock.Mock(
            return_value=status_only_rsp)

        rsp = wallet_comms.send(cmd, expected_tag)
        self.assertIs(rsp, status_only_rsp)

    def test_send_retries_status_only_response_for_typed_command(self: TestComms) -> None:
        wallet_comms = comms.WalletComms(transport=unittest.mock.Mock())
        wallet_comms.send_retry_max = 2

        cmd = wallet_pb.wallet_cmd()
        cmd.meta_cmd.SetInParent()
        expected_tag = comms.WalletComms.response_tag_for_command(cmd)

        status_only_rsp = wallet_pb.wallet_rsp()
        status_only_rsp.status = wallet_pb.status.ERROR
        right_rsp = wallet_pb.wallet_rsp()
        right_rsp.meta_rsp.SetInParent()

        wallet_comms._transceive_once = unittest.mock.Mock(
            side_effect=[status_only_rsp, right_rsp]
        )
        rsp = wallet_comms.send(cmd, expected_tag)

        self.assertIs(rsp, right_rsp)
        self.assertEqual(2, wallet_comms._transceive_once.call_count)

    def test_send_accepts_status_only_unknown_message(self: TestComms) -> None:
        wallet_comms = comms.WalletComms(transport=unittest.mock.Mock())

        cmd = wallet_pb.wallet_cmd()
        cmd.meta_cmd.SetInParent()
        expected_tag = comms.WalletComms.response_tag_for_command(cmd)

        unknown_rsp = wallet_pb.wallet_rsp()
        unknown_rsp.status = wallet_pb.status.UNKNOWN_MESSAGE
        wallet_comms._transceive_once = unittest.mock.Mock(
            return_value=unknown_rsp)

        rsp = wallet_comms.send(cmd, expected_tag)
        self.assertIs(rsp, unknown_rsp)
        wallet_comms._transceive_once.assert_called_once_with(
            cmd, timeout=None)

    def test_send_accepts_status_only_unknown_msg_flag(self: TestComms) -> None:
        wallet_comms = comms.WalletComms(transport=unittest.mock.Mock())

        cmd = wallet_pb.wallet_cmd()
        cmd.meta_cmd.SetInParent()
        expected_tag = comms.WalletComms.response_tag_for_command(cmd)

        unknown_rsp = wallet_pb.wallet_rsp()
        unknown_rsp.unknown_msg = True
        wallet_comms._transceive_once = unittest.mock.Mock(
            return_value=unknown_rsp)

        rsp = wallet_comms.send(cmd, expected_tag)
        self.assertIs(rsp, unknown_rsp)
        wallet_comms._transceive_once.assert_called_once_with(
            cmd, timeout=None)

    def test_transceive_applies_response_validation_for_wallet_cmd(self: TestComms) -> None:
        wallet_comms = comms.WalletComms(transport=unittest.mock.Mock())
        wallet_comms.send = unittest.mock.Mock(
            return_value=wallet_pb.wallet_rsp())

        cmd = wallet_pb.wallet_cmd()
        cmd.meta_cmd.SetInParent()
        expected_tag = comms.WalletComms.response_tag_for_command(cmd)

        wallet_comms.transceive(cmd, timeout=5)
        wallet_comms.send.assert_called_once_with(cmd, expected_tag, timeout=5)


if __name__ == "__main__":
    unittest.main()

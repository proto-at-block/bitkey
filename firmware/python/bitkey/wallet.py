from __future__ import annotations

import hashlib
import logging
import time
from typing import Any, Optional

import semver
from bitkey_proto import display_pb2 as display_pb
from bitkey_proto import mfgtest_pb2 as mfgtest_pb
from bitkey_proto import ops_keys_pb2 as ops_keys
from bitkey_proto import ops_keybundle_pb2 as ops_keybundle
from bitkey_proto import ops_seal_pb2 as ops_seal
from bitkey_proto import wallet_pb2 as wallet_pb

from .comms import WalletComms
from .fwup import FwupParams
from .secure_channel import SecureChannel
from .grant_protocol import Grant


class Wallet:
    def __init__(self, comms: WalletComms = None, product: str = "w1") -> None:
        self.comms: Optional[WalletComms] = comms
        self.product: str = product.lower()

    def __enter__(self) -> Wallet:
        return self

    def __exit__(self, type, value, tb) -> None:
        self.comms.close()

    def _build_cmd(self):
        cmd = wallet_pb.wallet_cmd()
        cmd.timestamp = int(time.time())
        return cmd

    @staticmethod
    def chip_name_to_role(product: str, mcu: Optional[str]) -> int:
        """Returns the MCU role associated with a chip.

        :param product: the product name.
        :param mcu: optional MCU name.
        :returns: proto MCU role identifier.
        :raises: ``NotImplementedError``.
        """
        _product_to_chip_mapping = {
            "w1": {
                "efr32": wallet_pb.mcu_role.MCU_ROLE_CORE,
            },
            "w3": {
                "stm32u5": wallet_pb.mcu_role.MCU_ROLE_UXC,
                "efr32": wallet_pb.mcu_role.MCU_ROLE_CORE,
            },
        }

        if not mcu:
            # Default to 'CORE' if not specified.
            return wallet_pb.mcu_role.MCU_ROLE_CORE

        chip_mapping = _product_to_chip_mapping.get(product.lower(), {})
        role = chip_mapping.get(mcu.lower(), None)
        if role is None:
            raise NotImplementedError(f"No role found for MCU: {mcu=}")
        return role

    def fwup_params(self: Wallet, mcu: Optional[str] = "efr32") -> FwupParams:
        """Retrieves the FWUP parameters for the target MCU.

        :param self: the wallet instance.
        :param mcu: target MCU to retrieve the FWUP parameters for.
        :returns: FWUP parameters for target MCU of target product.
        """
        _product_to_partition_mapping = {
            "w1": {
                "efr32": "w1a",
            },
            "w3": {
                "stm32u5": "w3a-uxc",
                "efr32": "w3a-core",
            },
        }

        _product = _product_to_partition_mapping.get(
            self.product, {}).get(mcu.lower(), "w1a")
        return FwupParams.from_product(_product)

    def start_fingerprint_enrollment(self, index=0, label=""):
        cmd = self._build_cmd()
        msg = wallet_pb.start_fingerprint_enrollment_cmd()
        handle = wallet_pb.fingerprint_handle()
        handle.index = index
        handle.label = label
        msg.handle.CopyFrom(handle)
        cmd.start_fingerprint_enrollment_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def get_fingerprint_enrollment_status(self):
        cmd = self._build_cmd()
        msg = wallet_pb.get_fingerprint_enrollment_status_cmd()
        msg.app_knows_about_this_field = True  # Set this to false simulate an old app
        cmd.get_fingerprint_enrollment_status_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def query_authentication(self):
        cmd = self._build_cmd()
        msg = wallet_pb.query_authentication_cmd()
        cmd.query_authentication_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def seal_csek(self, csek, legacy: bool = False):
        if legacy:
            # The CSEK used to not be wrapped in a SecureChannel
            cmd = self._build_cmd()
            msg = ops_seal.seal_csek_cmd()
            msg.unsealed_csek = bytes(csek, encoding="utf8")
            cmd.seal_csek_cmd.CopyFrom(msg)
            return self.comms.transceive(cmd)
        else:
            with SecureChannel(self) as secure_channel:
                cmd = wallet_pb.wallet_cmd()
                msg = ops_seal.seal_csek_cmd()
                msg.csek.CopyFrom(secure_channel.encrypt(
                    bytes(csek, encoding="utf8")))
                cmd.seal_csek_cmd.CopyFrom(msg)
                return self.comms.transceive(cmd)

    def unseal_csek(self, csek, iv, tag):
        cmd = self._build_cmd()
        msg = ops_seal.unseal_csek_cmd()

        msg.sealed_csek.data = bytes(csek, encoding="utf8")
        msg.sealed_csek.nonce = bytes(iv, encoding="utf8")
        msg.sealed_csek.tag = bytes(tag, encoding="utf8")

        cmd.unseal_csek_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def wipe_state(self):
        cmd = self._build_cmd()
        msg = wallet_pb.wipe_state_cmd()
        cmd.wipe_state_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_fingerprint_calibrate(self):
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_fingerprint_cmd()
        msg.calibrate.CopyFrom(
            mfgtest_pb.mfgtest_fingerprint_cmd.calibrate_cmd())
        cmd.mfgtest_fingerprint_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_fingerprint_image_capture_run(self, update_callback=None):
        response = self.mfgtest_fingerprint_image_capture()

        image_size = 0
        bytes_remaining = 1
        image = []
        offset = 0
        counter = 0
        while bytes_remaining > 0:
            counter += 1
            response = self.mfgtest_fingerprint_image_get_capture(offset)

            assert response.mfgtest_fingerprint_rsp.rsp_status == \
                response.mfgtest_fingerprint_rsp.SUCCESS, "error while getting image chunk"

            response = response.mfgtest_fingerprint_rsp.image_get_capture
            bytes_remaining = response.bytes_remaining

            image += response.image_chunk
            offset += len(response.image_chunk)

            if image_size == 0:
                image_size = bytes_remaining + len(response.image_chunk)

            if update_callback:
                update_callback(image_size, len(response.image_chunk))

        if update_callback:
            update_callback(image_size, len(response.image_chunk))

        return image

    def mfgtest_fingerprint_image_capture(self):
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_fingerprint_cmd()
        msg.image_capture.CopyFrom(
            mfgtest_pb.mfgtest_fingerprint_cmd.image_capture_cmd())
        cmd.mfgtest_fingerprint_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_fingerprint_image_get_capture(self, offset):
        cmd = self._build_cmd()
        mfgtest_cmd = mfgtest_pb.mfgtest_fingerprint_cmd.image_get_capture_cmd()
        mfgtest_cmd.image_offset = offset
        msg = mfgtest_pb.mfgtest_fingerprint_cmd()
        msg.image_get_capture.CopyFrom(mfgtest_cmd)
        cmd.mfgtest_fingerprint_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_fingerprint_selftest_start(self):
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_fingerprint_cmd()
        msg.selftest_start.CopyFrom(
            mfgtest_pb.mfgtest_fingerprint_cmd.selftest_start_cmd())
        cmd.mfgtest_fingerprint_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_fingerprint_selftest_get_result(self):
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_fingerprint_cmd()
        msg.selftest_get_result.CopyFrom(
            mfgtest_pb.mfgtest_fingerprint_cmd.selftest_get_result_cmd())
        cmd.mfgtest_fingerprint_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_fingerprint_security_mode(self):
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_fingerprint_cmd()
        msg.security_mode.CopyFrom(
            mfgtest_pb.mfgtest_fingerprint_cmd.security_mode_cmd())
        cmd.mfgtest_fingerprint_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_fingerprint_security_enable(self, real):
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_fingerprint_cmd()
        sub_cmd = mfgtest_pb.mfgtest_fingerprint_cmd.security_enable_cmd()
        sub_cmd.dry_run = not real
        msg.security_enable.CopyFrom(sub_cmd)
        cmd.mfgtest_fingerprint_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_fingerprint_security_test(self):
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_fingerprint_cmd()
        msg.security_test.CopyFrom(
            mfgtest_pb.mfgtest_fingerprint_cmd.security_test_cmd())
        cmd.mfgtest_fingerprint_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_fingerprint_image_analysis(self):
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_fingerprint_cmd()
        msg.image_analysis.CopyFrom(
            mfgtest_pb.mfgtest_fingerprint_cmd.image_analysis_cmd())
        cmd.mfgtest_fingerprint_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_serial_write(self, serial: str, which: int):
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_serial_write_cmd()
        msg.type = which
        msg.serial = serial
        cmd.mfgtest_serial_write_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_battery_variant(self, variant: int):
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_battery_variant_cmd()
        msg.variant = variant
        cmd.mfgtest_battery_variant_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_runin_get_data(self):
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_runin_get_data_cmd()
        cmd.mfgtest_runin_get_data_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_tap_test_start(self, nfc_test: int, timeout: int, delay: int) -> Any:
        """Starts an NFC loopback test.

        :param nfc_test: the NFC test to perform.
        :param timeout: timeout (ms) to perform the test for.
        :param delay: delay (ms) before starting the test.
        :returns: the response proto.
        """
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_nfc_loopback_cmd()
        msg.cmd = mfgtest_pb.mfgtest_nfc_loopback_cmd.START
        msg.test = nfc_test
        msg.delay_ms = delay
        msg.timeout_ms = timeout
        cmd.mfgtest_nfc_loopback_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_tap_test_result(self) -> Any:
        """Retrieves the result of the last tap test from the wallet.

        If no test was run or an attempt is made to retrieve the result twice
        for the previous test, the result will always indicate a failure.

        :returns: command response proto.
        """
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_nfc_loopback_cmd()
        msg.cmd = mfgtest_pb.mfgtest_nfc_loopback_cmd.REQUEST_DATA
        cmd.mfgtest_nfc_loopback_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def device_id(self):
        cmd = self._build_cmd()
        msg = wallet_pb.device_id_cmd()
        cmd.device_id_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def metadata(self, mcu: Optional[str] = None) -> Any:
        """Requests the metadata from the product.

        If an ``mcu`` argument is provided, requests the metadata of the
        specific target MCU.

        :param mcu: optional target MCU name.
        :returns: proto on success, otherwise ``None``.
        """
        cmd = self._build_cmd()
        msg = wallet_pb.meta_cmd()
        if mcu:
            msg.mcu_role = self.chip_name_to_role(self.product, mcu)
        cmd.meta_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def reset(self):
        cmd = self._build_cmd()
        msg = wallet_pb.reset_cmd()
        cmd.reset_cmd.CopyFrom(msg)
        try:
            self.comms.transceive(cmd, timeout=0)
        except IOError:
            pass  # Timeout is expected

    def fuel(self):
        cmd = self._build_cmd()
        msg = wallet_pb.fuel_cmd()
        cmd.fuel_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_gpio(
        self: Wallet,
        action: int,
        port: int,
        pin: int,
        mcu: Optional[str] = None
    ) -> wallet_pb.wallet_rsp:
        """Reads/sets/clears a target GPIO.

        :param action: GPIO action to perform.
        :param port: GPIO port identifier.
        :param pin: GPIO pin identifier.
        :param mcu: optional target MCU name.
        :returns: wallet response on success, otherwise ``None``.
        """
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_gpio_cmd()
        msg.action = action
        msg.port = port
        msg.pin = pin
        if mcu:
            msg.mcu_role = self.chip_name_to_role(self.product, mcu)
        cmd.mfgtest_gpio_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_button(self, action, bypass_enabled=False):
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_button_cmd()
        msg.action = action
        msg.bypass_enabled = bypass_enabled
        cmd.mfgtest_button_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_show_screen(self, screen_mode, custom_rgb=None, brightness=None):
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_show_screen_cmd()
        msg.screen_mode = screen_mode
        if custom_rgb is not None:
            msg.custom_rgb = custom_rgb
        if brightness is not None:
            msg.brightness = brightness
        cmd.mfgtest_show_screen_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def mfgtest_spi_loopback(self, instance: int, data: bytes) -> mfgtest_pb.mfgtest_spi_loopback_rsp:
        """Performs a SPI loopback test.

        Sends the specified data over the SPI instance indicated by the given
        ``instance``. Returns the proto response indicating whether the data
        was looped back successfully as well as the data received.

        :param instance: the SPI instance identifier.
        :param data: bytes to send for the loopback test.
        :returns: SPI loopback response.
        """
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_spi_loopback_cmd()
        msg.instance = instance
        msg.data = bytes(data)
        cmd.mfgtest_spi_loopback_cmd.CopyFrom(msg)
        rsp = self.comms.transceive(cmd)
        return rsp.mfgtest_spi_loopback_rsp

    def mfgtest_board_id(self: Wallet) -> Optional[int]:
        """Reads the board ID from the device.

        :returns: board identifier (``int``) on success, otherwise ``None``.
        """
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_board_id_cmd()
        cmd.mfgtest_board_id_cmd.CopyFrom(msg)
        rsp = self.comms.transceive(cmd)
        if rsp.mfgtest_board_id_rsp.rsp_status != rsp.mfgtest_board_id_rsp.SUCCESS:
            return None
        return rsp.mfgtest_board_id_rsp.board_id

    def mfgtest_touch_test_start(self: Wallet, timeout: int) -> bool:
        """Starts a touch test over NFC.

        The given ``timeout`` (in seconds) identifies how long the device
        should wait for a touch event before deciding that the touch test
        has failed. To retrieve the result of the touch test, the caller
        should call ``mfgtest_touch_test_finish()``.

        :param timeout: timeout (seconds) to wait for a touch event.
        :returns: ``True`` if test started successfully, otherwise ``False``
        """
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_touch_test_cmd()
        msg.timeout = timeout
        msg.cmd_id = mfgtest_pb.mfgtest_touch_test_cmd.START
        cmd.mfgtest_touch_test_cmd.CopyFrom(msg)
        rsp = self.comms.transceive(cmd)
        return rsp.mfgtest_touch_test_rsp.rsp_status == mfgtest_pb.mfgtest_touch_test_rsp.SUCCESS

    def mfgtest_touch_test_finish(self: Wallet) -> mfgtest_pb.mfgtest_touch_test_rsp:
        """Retrieves the result of the touch test started with
        ``mfgtest_touch_test_start()``.

        :returns: touch test response proto.
        """
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_touch_test_cmd()
        msg.cmd_id = mfgtest_pb.mfgtest_touch_test_cmd.REQUEST_DATA
        cmd.mfgtest_touch_test_cmd.CopyFrom(msg)
        rsp = self.comms.transceive(cmd)
        # Return response even on failure so caller can show boxes_remaining
        return rsp.mfgtest_touch_test_rsp

    def mfgtest_charger_info(self) -> Optional[mfgtest_pb.mfgtest_charger_rsp]:
        """Retrieves information about the charger from the device.

        :returns: charger response proto on success, otherwise ``None``.
        """
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_charger_cmd()
        msg.cmd_id = mfgtest_pb.mfgtest_charger_cmd.GET_INFO
        cmd.mfgtest_charger_cmd.CopyFrom(msg)

        rsp = self.comms.transceive(cmd)
        if rsp.mfgtest_charger_rsp.rsp_status == mfgtest_pb.mfgtest_charger_rsp.SUCCESS:
            return rsp.mfgtest_charger_rsp
        return None

    def mfgtest_charger_registers(self) -> Optional[mfgtest_pb.mfgtest_charger_rsp]:
        """Retrieves the charger registers from the device.

        :returns: charger response proto on success, otherwise ``None``.
        """
        cmd = self._build_cmd()
        msg = mfgtest_pb.mfgtest_charger_cmd()
        msg.cmd_id = mfgtest_pb.mfgtest_charger_cmd.READ_REGISTERS
        cmd.mfgtest_charger_cmd.CopyFrom(msg)

        rsp = self.comms.transceive(cmd)
        if rsp.mfgtest_charger_rsp.rsp_status == mfgtest_pb.mfgtest_charger_rsp.SUCCESS:
            return rsp.mfgtest_charger_rsp
        return None

    def coredump(self, offset=0, operation='COREDUMP'):
        cmd = self._build_cmd()
        msg = wallet_pb.coredump_get_cmd()
        msg.offset = offset
        msg.type = wallet_pb.coredump_get_cmd.coredump_get_type.Value(
            operation)
        cmd.coredump_get_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def events(self):
        cmd = self._build_cmd()
        msg = wallet_pb.events_get_cmd()
        cmd.events_get_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def feature_flags_get(self):
        cmd = self._build_cmd()
        msg = wallet_pb.feature_flags_get_cmd()
        cmd.feature_flags_get_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def feature_flags_set(self, flags):
        cmd = self._build_cmd()
        msg = wallet_pb.feature_flags_set_cmd()
        msg.flags.extend(flags)
        cmd.feature_flags_set_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def telemetry_id(self):
        cmd = self._build_cmd()
        msg = wallet_pb.telemetry_id_get_cmd()
        cmd.telemetry_id_get_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def secinfo(self):
        cmd = self._build_cmd()
        msg = wallet_pb.secinfo_get_cmd()
        cmd.secinfo_get_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def cert_get(self, kind):
        cmd = self._build_cmd()
        msg = wallet_pb.cert_get_cmd()
        msg.kind = kind
        cmd.cert_get_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def pubkeys_get(self):
        cmd = self._build_cmd()
        msg = wallet_pb.pubkeys_get_cmd()
        cmd.pubkeys_get_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def pubkey_get(self, kind):
        cmd = self._build_cmd()
        msg = wallet_pb.pubkey_get_cmd()
        msg.kind = kind
        cmd.pubkey_get_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def fingerprint_settings_get(self):
        cmd = self._build_cmd()
        msg = wallet_pb.fingerprint_settings_get_cmd()
        cmd.fingerprint_settings_get_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def cap_touch_cal(self):
        cmd = self._build_cmd()
        msg = wallet_pb.cap_touch_cal_cmd()
        cmd.cap_touch_cal_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def device_info(self):
        cmd = self._build_cmd()
        msg = wallet_pb.device_info_cmd()
        cmd.device_info_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def lock_device(self):
        cmd = self._build_cmd()
        msg = wallet_pb.lock_device_cmd()
        cmd.lock_device_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def version(self):
        v = self.device_info().device_info_rsp.version
        return semver.Version(v.major, v.minor, v.patch)

    def active_slot(self):
        return self.metadata().meta_rsp.active_slot

    def derive(self, network: int, path: list):
        cmd = self._build_cmd()
        msg = ops_keys.derive_key_descriptor_cmd()
        msg.network = network
        p = ops_keybundle.derivation_path()
        p.child.extend(path)
        msg.derivation_path.CopyFrom(p)
        cmd.derive_key_descriptor_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def derive_and_sign(self, digest: bytes, path: list, async_sign: bool):
        cmd = self._build_cmd()
        msg = ops_keys.derive_key_descriptor_and_sign_cmd()
        msg.hash = digest
        msg.async_sign = async_sign
        p = ops_keybundle.derivation_path()
        p.child.extend(path)
        msg.derivation_path.CopyFrom(p)
        cmd.derive_key_descriptor_and_sign_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def derive_public_key(self, curve, label: str):
        cmd = self._build_cmd()
        msg = ops_keys.derive_public_key_cmd()
        msg.curve = curve
        msg.label = bytes(label, encoding='ascii')
        cmd.derive_public_key_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def derive_public_key_and_sign(self, curve, label, digest: bytes):
        cmd = self._build_cmd()
        msg = ops_keys.derive_public_key_and_sign_cmd()
        msg.curve = curve
        msg.label = bytes(label, encoding='ascii')
        msg.hash = digest
        cmd.derive_public_key_and_sign_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def hardware_attestation(self, nonce):
        cmd = self._build_cmd()
        msg = wallet_pb.hardware_attestation_cmd()
        msg.nonce = nonce
        cmd.hardware_attestation_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def unlock_secret(self, secret: str):
        with SecureChannel(self) as secure_channel:
            secret_hash = hashlib.sha256(secret.encode('ascii')).digest()
            cmd = wallet_pb.wallet_cmd()
            msg = wallet_pb.send_unlock_secret_cmd()
            logger = logging.getLogger()
            logger.info(cmd)
            logger.info(msg)
            msg.secret.CopyFrom(secure_channel.encrypt(secret_hash))
            cmd.send_unlock_secret_cmd.CopyFrom(msg)
            return self.comms.transceive(cmd)

    def provision_unlock_secret(self, secret: str):
        with SecureChannel(self) as secure_channel:
            secret_hash = hashlib.sha256(secret.encode('ascii')).digest()
            cmd = wallet_pb.wallet_cmd()
            msg = wallet_pb.provision_unlock_secret_cmd()
            msg.secret.CopyFrom(secure_channel.encrypt(secret_hash))
            cmd.provision_unlock_secret_cmd.CopyFrom(msg)
            return self.comms.transceive(cmd)

    def configure_unlock_limit_response(self, response):
        cmd = self._build_cmd()
        msg = wallet_pb.configure_unlock_limit_response_cmd()
        msg.unlock_limit_response = response
        cmd.configure_unlock_limit_response_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def delete_fingerprint(self, index):
        cmd = self._build_cmd()
        msg = wallet_pb.delete_fingerprint_cmd()
        msg.index = index
        cmd.delete_fingerprint_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def get_enrolled_fingerprints(self):
        cmd = self._build_cmd()
        msg = wallet_pb.get_enrolled_fingerprints_cmd()
        cmd.get_enrolled_fingerprints_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def get_unlock_method(self):
        cmd = self._build_cmd()
        msg = wallet_pb.get_unlock_method_cmd()
        cmd.get_unlock_method_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def set_fingerprint_label(self, index, label):
        cmd = self._build_cmd()
        msg = wallet_pb.set_fingerprint_label_cmd()
        handle = wallet_pb.fingerprint_handle()
        handle.index = index
        handle.label = label
        msg.handle.CopyFrom(handle)
        cmd.set_fingerprint_label_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def cancel_fingerprint_enrollment(self):
        cmd = self._build_cmd()
        msg = wallet_pb.cancel_fingerprint_enrollment_cmd()
        cmd.cancel_fingerprint_enrollment_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def fingerprint_reset_request(self):
        cmd = self._build_cmd()
        msg = wallet_pb.fingerprint_reset_request_cmd()
        cmd.fingerprint_reset_request_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def fingerprint_reset_finalize(self, grant: Grant):
        serialized_grant = grant.serialize()
        cmd = self._build_cmd()
        msg = wallet_pb.fingerprint_reset_finalize_cmd()
        msg.grant = serialized_grant
        cmd.fingerprint_reset_finalize_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def provision_app_auth_pubkey(self, pubkey):
        cmd = self._build_cmd()
        msg = wallet_pb.provision_app_auth_pubkey_cmd()
        msg.pubkey = pubkey
        cmd.provision_app_auth_pubkey_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)


__all__ = ["Wallet"]

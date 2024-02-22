import yaml
import semver
import json

from dataclasses import dataclass
from functools import cached_property
from datetime import datetime

from os import stat
from tqdm import tqdm
from math import ceil

from pathlib import Path
from bitkey_proto import wallet_pb2 as wallet_pb
from .comms import WalletComms


@dataclass
class FwupBundle:
    path: Path

    @property
    def manifest_path(self) -> Path:
        return next(self.path.glob('*.json'))

    @cached_property
    def manifest(self) -> dict:
        return json.loads(self.manifest_path.read_text())

    @cached_property
    def mode(self) -> wallet_pb.fwup_mode:
        if 'delta' in self.manifest_path.name:
            return wallet_pb.fwup_mode.FWUP_MODE_DELTA_ONESHOT
        else:
            return wallet_pb.fwup_mode.FWUP_MODE_NORMAL


@dataclass
class FwupParams:
    # TODO Remove defaults eventually. These exist to support flashing binary / signature directly,
    # but won't scale for new products which may have different defaults.
    version: int = 0
    chunk_size: int = 452
    signature_offset: int = (632 * 1024) - 64
    app_props_offset: int = 1024
    signature_size: int = 64


class Fwup:
    FWUP_BUNDLE_YAML_NAME = "fwup-manifest.yml"

    def __init__(self, bundle_dir: Path,
                 binary: Path = None,
                 signature: Path = None,
                 start_sequence_id: int = 0,
                 comms: WalletComms = None,
                 mode="FWUP_MODE_NORMAL"):
        """Firmware update handler.

        A directory containing a 'fwup bundle' (a manifest yaml and associated firmware
        images and signatures) may be provided in `bundle_dir`.

        If `binary` and `signature` are set, then the update is forced using those files, and default FwupParams
        are used.
        """
        self.bundle_dir = bundle_dir
        self.comms = comms or WalletComms()
        self.binary = binary
        self.signature = signature
        self.start_sequence_id = start_sequence_id
        self.mode = wallet_pb.fwup_mode.Value(mode)
        self.delta = (mode == 'FWUP_MODE_DELTA_INLINE') or (
            mode == 'FWUP_MODE_DELTA_ONESHOT')

        self.force = self.binary or self.signature
        if self.bundle_dir and self.force:
            raise ValueError("May only set bundle_dir or binary+signature")

        if self.bundle_dir:
            with open(self.bundle_dir / self.FWUP_BUNDLE_YAML_NAME, 'r') as f:
                self.manifest_dict = yaml.safe_load(f)['fwup_bundle']
                self.params = FwupParams(
                    version=self.manifest_dict['version'],
                    chunk_size=self.manifest_dict['parameters']['wca_chunk_size'],
                    signature_offset=self.manifest_dict['parameters']['signature_offset'],
                    app_props_offset=self.manifest_dict['parameters']['app_properties_offset'],
                )
        else:
            self.params = FwupParams()

    def _prepare(self):
        if self.force:
            return True

        # Determine if update is needed.
        target_slot, version = self._update_info()
        needed = version < self.params.version
        needed = True
        if not needed:
            print(
                f"Update not needed. Current version is {version}, but requested an update to {self.params.version}")

        # Select binary and signature.
        if target_slot == wallet_pb.firmware_slot.SLOT_A:
            app = 'application_a'
        elif target_slot == wallet_pb.firmware_slot.SLOT_B:
            app = 'application_b'

        self.binary = Path(self.bundle_dir) / \
            self.manifest_dict['assets'][app]['image']['name']
        self.signature = Path(self.bundle_dir) / \
            self.manifest_dict['assets'][app]['signature']['name']

        print(
            f"Updating into slot {app} from {version} to {self.params.version} with {self.binary}, {self.signature}")
        return needed

    def start(self) -> bool:
        """Start the firmware update."""
        if self.start_sequence_id > 0:
            return True
        if not self._prepare():
            return False
        cmd = wallet_pb.wallet_cmd()
        msg = wallet_pb.fwup_start_cmd()
        msg.mode = self.mode
        if self.delta:
            msg.patch_size = stat(self.binary).st_size
        cmd.fwup_start_cmd.CopyFrom(msg)
        result = self.comms.transceive(cmd, timeout=2)
        return result.fwup_start_rsp.rsp_status == result.fwup_start_rsp.SUCCESS

    def finish(self, bl_upgrade: bool = False) -> wallet_pb.fwup_finish_rsp:
        """Finalize the firmware update."""
        cmd = wallet_pb.wallet_cmd()
        msg = wallet_pb.fwup_finish_cmd()
        msg.app_properties_offset = self.params.app_props_offset
        msg.signature_offset = self.params.signature_offset
        msg.bl_upgrade = bl_upgrade
        msg.mode = self.mode
        cmd.fwup_finish_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd, timeout=2)

    def transfer(self, update_gui=False, operator_interface=None, timeout=None):
        start_time = datetime.now()

        with open(self.binary, 'rb') as f:
            id = self.start_sequence_id
            data = f.read(self.params.chunk_size)
            ui = tqdm(total=ceil(
                stat(self.binary).st_size / self.params.chunk_size))

            while data:
                if timeout:
                    elapsed_time = datetime.now() - start_time
                    if elapsed_time.total_seconds() >= timeout:
                        return False

                self._transfer_chunk(id, data, 0, mode=self.mode)
                data = f.read(self.params.chunk_size)
                ui.update(1)
                id += 1

                percent = round(ui.n/ui.total*100, 1)
                if update_gui and percent % 10 == 0:
                    operator_interface.print_to_console(
                        'FWUP {0}% Complete...\n'.format(round(percent)))

            ui.close()

        # Delta or not, the last transfer of the signature is always a
        # "normal" transfer.
        with open(self.signature, 'rb') as f:
            data = f.read(self.params.signature_size)
            self._transfer_chunk(0, data, self.params.signature_offset)

        return True

    def transfer_bytes(self, data: bytes, id: int = 0, offset: int = 0):
        """Transfer several chunks of data to the device."""
        chunk_size = self.params.chunk_size
        for i in range(0, len(data), chunk_size):
            self._transfer_chunk(id + i // chunk_size,
                                 data[i:i + chunk_size], offset)

    def _update_info(self) -> (wallet_pb.firmware_slot, semver.VersionInfo):
        cmd = wallet_pb.wallet_cmd()
        msg = wallet_pb.meta_cmd()
        cmd.meta_cmd.CopyFrom(msg)
        result = self.comms.transceive(cmd)

        active_slot = result.meta_rsp.active_slot
        if active_slot == wallet_pb.firmware_slot.SLOT_A:
            version = result.meta_rsp.meta_slot_a.version
            target_slot = wallet_pb.firmware_slot.SLOT_B
        elif active_slot == wallet_pb.firmware_slot.SLOT_B:
            version = result.meta_rsp.meta_slot_b.version
            target_slot = wallet_pb.firmware_slot.SLOT_A
        else:
            raise AssertionError("invalid slot")

        return target_slot, semver.VersionInfo(version.major, version.minor, version.patch)

    def _transfer_chunk(self, id: int, data: bytes, offset: int, mode=wallet_pb.fwup_mode.FWUP_MODE_NORMAL):
        """Transfer a single chunk of data to the device."""
        cmd = wallet_pb.wallet_cmd()
        msg = wallet_pb.fwup_transfer_cmd()
        msg.sequence_id = id
        msg.offset = offset
        msg.fwup_data = data
        msg.mode = mode
        cmd.fwup_transfer_cmd.CopyFrom(msg)
        result = self.comms.transceive(cmd, timeout=2)
        return result.fwup_transfer_rsp.rsp_status == result.fwup_transfer_rsp.SUCCESS


class FirmwareUpdater:
    """Convenience wrapper around the lower-level Fwup object.
    """

    def __init__(self, wallet, gui=None):
        self.wallet = wallet
        self.gui = gui

    def _build_updater(self, fwup_bundle: FwupBundle) -> Fwup:
        """Determine if we should do a normal or delta fwup, and return the appropriately
        configured Fwup object."""
        mode = fwup_bundle.mode

        if mode == wallet_pb.fwup_mode.FWUP_MODE_NORMAL:
            return Fwup(fwup_bundle.path, None, None, 0, comms=self.wallet.comms,
                        mode=wallet_pb.fwup_mode.Name(fwup_bundle.mode))
        elif mode == wallet_pb.fwup_mode.FWUP_MODE_DELTA_ONESHOT:
            manifest = fwup_bundle.manifest

            # Which slot is active?
            active_slot = self.wallet.active_slot()

            if active_slot == wallet_pb.firmware_slot.SLOT_A:
                patch_name = 'a2b_patch'
            elif active_slot == wallet_pb.firmware_slot.SLOT_B:
                patch_name = 'b2a_patch'
            else:
                assert False, active_slot

            binary = fwup_bundle.path / \
                manifest['fwup_bundle']['assets'][patch_name]['image']['name']
            signature = fwup_bundle.path / \
                manifest['fwup_bundle']['assets'][patch_name]['signature']['name']

            return Fwup(None, binary, signature, 0, comms=self.wallet.comms,
                        mode=wallet_pb.fwup_mode.Name(fwup_bundle.mode))
        else:
            assert False, mode

    def fwup(self):
        print("Not implemented yet, sorry!")

    def fwup_local(self, bundle: Path, timeout=None):
        """Firmware update using the specified `bundle`."""
        assert bundle.exists()

        update = self._build_updater(FwupBundle(bundle))

        result = update.start()
        if not result:
            print("Failed to start")
            return

        print("Firmware update in progress...")

        if self.gui:
            ok = update.transfer(update_gui=True,
                                 operator_interface=self.gui, timeout=timeout)
        else:
            ok = update.transfer(timeout=timeout)

        if not ok:
            print("Timed out!")
            return False

        result = update.finish()
        if result.fwup_finish_rsp.rsp_status == result.fwup_finish_rsp.SUCCESS:
            print("Firmware update finished successfully.")
        elif result.fwup_finish_rsp.rsp_status == result.fwup_finish_rsp.WILL_APPLY_PATCH:
            print("Firmware update transferred, applying patch now...")
        else:
            print("Firmware update failed.")
            print(result)

        return True

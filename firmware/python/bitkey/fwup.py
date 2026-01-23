from __future__ import annotations

import importlib.resources
import json
import semver
import yaml
from dataclasses import dataclass
from datetime import datetime
from functools import cached_property
from math import ceil
from os import stat
from pathlib import Path
from typing import Dict, Optional

import yaml
from bitkey_proto import wallet_pb2 as wallet_pb
from nanopb_pb2 import nanopb
from tqdm import tqdm

from . import util
from .comms import WalletComms


@dataclass
class FwupBundle:
    path: Path

    @property
    def manifest_path(self) -> Path:
        return next(self.path.glob('*.json'))

    @cached_property
    def manifest(self) -> Dict:
        return json.loads(self.manifest_path.read_text())

    @cached_property
    def mode(self) -> wallet_pb.fwup_mode:
        # Check if this is a delta bundle by looking for from_version/to_version in manifest
        if 'from_version' in self.manifest.get('fwup_bundle', {}):
            return wallet_pb.fwup_mode.FWUP_MODE_DELTA_ONESHOT
        elif 'delta' in self.manifest_path.name:
            # Fallback to filename check for backwards compatibility
            return wallet_pb.fwup_mode.FWUP_MODE_DELTA_ONESHOT
        else:
            return wallet_pb.fwup_mode.FWUP_MODE_NORMAL


@dataclass
class FwupParams:
    version: int
    chunk_size: int
    signature_offset: int
    app_props_offset: int
    signature_size: int

    @staticmethod
    def from_product(product: str) -> Optional[FwupParams]:
        """Returns the FWUP parameters for the target product.

        :param product: the product name.
        :returns: FWUP parameters for the target product on success.
        """

        try:
            try:
                path = importlib.resources.files("bitkey_config").joinpath(
                    f"partitions/{product}/partitions.yml"
                )
            except ModuleNotFoundError:
                # If the `bitkey_config` is installed as editable, then the path
                # will not contain the `bitkey_config`.
                path = importlib.resources.files("partitions").joinpath(
                    f"{product}/partitions.yml"
                )
            with path.open("rb") as f:
                config = yaml.safe_load(f)
        except FileNotFoundError:
            return None

        flash = config.get("flash", {})
        partitions = flash.get("partitions", [])
        partition = None
        for _partition in partitions:
            name = _partition.get("name", "")
            if name.startswith("application"):
                partition = _partition
                break
        else:
            raise RuntimeError(
                f"No application partition found for {product=}")

        # Create a mapping of sections to their sizes.
        sections = partition.get("sections", [])
        sections_to_sizes: Dict[str, int] = {}
        for section in sections:
            name = section.get("name", "")
            size = util.size_to_bytes(section.get("size", 0))
            sections_to_sizes[name] = size

        # The program fills the remaining space, so its size may be 0. If that is
        # the case, then we have to compute the remaining size.
        if "program" in sections_to_sizes and sections_to_sizes["program"] == 0:
            partition_size = util.size_to_bytes(partition.get("size"))
            program_size = partition_size - sum(sections_to_sizes.values())
            sections_to_sizes["program"] = program_size

        # Finally iterate over the sections, now with sizes, to compute the
        # offset and populate the FWUP parameters.
        params: FwupParams = FwupParams(0, 0, 0, 0, 0)
        offset: int = 0
        for section in sections:
            name = section.get("name", "")
            size = sections_to_sizes[name]
            if name.startswith("properties"):
                params.app_props_offset = offset
            elif name.endswith("signature"):
                params.signature_offset = offset
                params.signature_size = size
            offset += size

        # Determine the maximum chunk size by looking at the FWUP proto.
        fd = wallet_pb.fwup_transfer_cmd.DESCRIPTOR.fields_by_name["fwup_data"]
        opts = fd.GetOptions()
        if not opts.HasExtension(nanopb):
            raise RuntimeError("nanopb extension missing from proto.")

        # Retrieve the max size from nanopb.
        max_chunk_size = opts.Extensions[nanopb].max_size

        # Compute the maximum write size that is flash aligned for the target.
        write_alignment = flash.get("alignment")
        if write_alignment is None or not isinstance(write_alignment, int):
            raise ValueError(
                f"Invalid or missing flash alignment for {product=}")

        params.chunk_size = max_chunk_size - (max_chunk_size % write_alignment)
        return params


class Fwup:
    FWUP_BUNDLE_YAML_NAME = "fwup-manifest.yml"

    def __init__(self, bundle_dir: Optional[Path],
                 binary: Path = None,
                 signature: Path = None,
                 start_sequence_id: int = 0,
                 comms: WalletComms = None,
                 mode="FWUP_MODE_NORMAL",
                 fwup_params: Optional[FwupParams] = None,
                 mcu_role: Optional[int] = None):
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
        self.mcu_role: int = mcu_role

        self.force = self.binary or self.signature
        if self.bundle_dir and self.force:
            raise ValueError("May only set bundle_dir or binary+signature")

        # Initialize attributes that may be used in _prepare()
        self.is_multi_mcu = False
        self.mcu_manifest = None
        self.manifest_dict = None

        if self.bundle_dir:
            with open(self.bundle_dir / self.FWUP_BUNDLE_YAML_NAME, 'r') as f:
                self.manifest_dict = yaml.safe_load(f)['fwup_bundle']

                # Detect multi-MCU manifest
                self.is_multi_mcu = 'mcus' in self.manifest_dict

                if self.is_multi_mcu:
                    # Find the MCU entry matching our mcu_role
                    self.mcu_manifest = self._find_mcu_manifest(self.mcu_role)
                    params_dict = self.mcu_manifest['parameters']
                    version = self.manifest_dict.get(
                        'version', self.manifest_dict.get('to_version'))
                else:
                    # Single-MCU (W1)
                    params_dict = self.manifest_dict['parameters']
                    version = self.manifest_dict.get(
                        'version', self.manifest_dict.get('to_version'))

                self.params = fwup_params or FwupParams(
                    version=version,
                    chunk_size=params_dict['wca_chunk_size'],
                    signature_offset=params_dict['signature_offset'],
                    app_props_offset=params_dict['app_properties_offset'],
                    signature_size=64,
                )
        elif fwup_params:
            self.params = fwup_params
        else:
            raise ValueError(
                "No bundle directory or FWUP parameters provided.")

    def _find_mcu_manifest(self, mcu_role):
        """Find the manifest entry for specific MCU role."""
        if mcu_role is None:
            # Default to CORE if not specified
            mcu_role = wallet_pb.mcu_role.MCU_ROLE_CORE

        # Map proto enum to manifest key
        role_map = {
            wallet_pb.mcu_role.MCU_ROLE_CORE: 'core',
            wallet_pb.mcu_role.MCU_ROLE_UXC: 'uxc',
        }

        role_key = role_map.get(mcu_role)
        if role_key and role_key in self.manifest_dict['mcus']:
            return self.manifest_dict['mcus'][role_key]

        raise ValueError(f"MCU role {mcu_role} not found in manifest")

    def _prepare(self):
        if self.force:
            return True

        # Determine if update is needed.
        target_slot, version = self._update_info()
        bundle_version = semver.VersionInfo.parse(self.params.version)
        needed = version < bundle_version
        if not needed:
            print(
                f"Update not needed. Current version is {version}, but requested an update to {self.params.version}")

        # Select binary and signature from MCU-specific or single-MCU assets
        if target_slot == wallet_pb.firmware_slot.SLOT_A:
            app = 'application_a'
        elif target_slot == wallet_pb.firmware_slot.SLOT_B:
            app = 'application_b'

        if self.is_multi_mcu:
            # Use MCU-specific assets
            assets = self.mcu_manifest['assets']
        else:
            # Single-MCU (W1)
            assets = self.manifest_dict['assets']

        self.binary = Path(self.bundle_dir) / assets[app]['image']['name']
        self.signature = Path(self.bundle_dir) / \
            assets[app]['signature']['name']

        print(
            f"Updating into slot {app} from {version} to {self.params.version} with {self.binary}, {self.signature}")
        return needed

    def start(self: FirmwareUpdater) -> bool:
        """Start the firmware update.

        :param self: the updater instance.
        :returns: ``True`` on success, otherwise ``False``.
        """
        if not self._prepare():
            return False
        if self.start_sequence_id > 0:
            return True

        cmd = wallet_pb.wallet_cmd()
        msg = wallet_pb.fwup_start_cmd()
        msg.mode = self.mode
        if self.delta:
            msg.patch_size = stat(self.binary).st_size
        if self.mcu_role:
            msg.mcu_role = self.mcu_role
        cmd.fwup_start_cmd.CopyFrom(msg)
        result = self.comms.transceive(cmd, timeout=2)
        success = result.fwup_start_rsp.rsp_status == result.fwup_start_rsp.SUCCESS
        if success and result.fwup_start_rsp.max_chunk_size:
            # Update the chunk size based on what was returned by the MCU.
            self.params.chunk_size = result.fwup_start_rsp.max_chunk_size
        return success

    def finish(self, bl_upgrade: bool = False) -> wallet_pb.fwup_finish_rsp:
        """Finalize the firmware update."""
        cmd = wallet_pb.wallet_cmd()
        msg = wallet_pb.fwup_finish_cmd()
        msg.app_properties_offset = self.params.app_props_offset
        msg.signature_offset = self.params.signature_offset
        msg.bl_upgrade = bl_upgrade
        msg.mode = self.mode
        if self.mcu_role:
            msg.mcu_role = self.mcu_role
        cmd.fwup_finish_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd, timeout=2)

    def transfer(self, update_gui=False, operator_interface=None, timeout=None) -> bool:
        start_time = datetime.now()

        with open(self.binary, 'rb') as f:
            id = self.start_sequence_id
            if id != 0:
                print(f"Starting at sequence id {id}")

            # Start data at the offset indicated by the start_sequence_id.
            f.seek(id * self.params.chunk_size)
            remaining = stat(self.binary).st_size - f.tell()

            data = f.read(self.params.chunk_size)
            ui = tqdm(total=ceil(remaining / self.params.chunk_size))

            while data:
                if timeout:
                    elapsed_time = datetime.now() - start_time
                    if elapsed_time.total_seconds() >= timeout:
                        return False

                if not self._transfer_chunk(id, data, 0, mode=self.mode):
                    return False
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
        if self.signature:
            with open(self.signature, 'rb') as f:
                data = f.read(self.params.signature_size)
                if not self._transfer_chunk(0, data, self.params.signature_offset):
                    return False

        return True

    def transfer_bytes(self, data: bytes, id: int = 0, offset: int = 0) -> bool:
        """Transfer several chunks of data to the device."""
        chunk_size = self.params.chunk_size
        for i in range(0, len(data), chunk_size):
            if not self._transfer_chunk(id + i // chunk_size,
                                        data[i:i + chunk_size], offset):
                return False
        return True

    def _update_info(self) -> (wallet_pb.firmware_slot, semver.VersionInfo):
        cmd = wallet_pb.wallet_cmd()
        msg = wallet_pb.meta_cmd()
        if self.mcu_role:
            msg.mcu_role = self.mcu_role
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

    def _transfer_chunk(self, id: int, data: bytes, offset: int, mode=wallet_pb.fwup_mode.FWUP_MODE_NORMAL) -> bool:
        """Transfer a single chunk of data to the device."""
        cmd = wallet_pb.wallet_cmd()
        msg = wallet_pb.fwup_transfer_cmd()
        msg.sequence_id = id
        msg.offset = offset
        msg.fwup_data = data
        msg.mode = mode
        if self.mcu_role:
            msg.mcu_role = self.mcu_role
        cmd.fwup_transfer_cmd.CopyFrom(msg)
        result = self.comms.transceive(cmd, timeout=2)
        return result.fwup_transfer_rsp.rsp_status == result.fwup_transfer_rsp.SUCCESS


class FirmwareUpdater:
    """Convenience wrapper around the lower-level Fwup object.
    """

    def __init__(self, wallet, gui=None):
        self.wallet = wallet
        self.gui = gui

    def _build_updater(self, fwup_bundle: FwupBundle, mcu: Optional[str] = "efr32") -> Fwup:
        """Determine if we should do a normal or delta fwup, and return the appropriately
        configured Fwup object."""
        mode = fwup_bundle.mode
        mcu_role = self.wallet.chip_name_to_role(self.wallet.product, mcu)

        # For normal mode, get params from wallet; for delta mode, use manifest params
        if mode == wallet_pb.fwup_mode.FWUP_MODE_NORMAL:
            params = self.wallet.fwup_params(mcu)
            params.version = fwup_bundle.manifest['fwup_bundle']['version']
        elif mode == wallet_pb.fwup_mode.FWUP_MODE_DELTA_ONESHOT:
            # For delta updates, use parameters from the manifest (target firmware params)
            fwup_bundle_data = fwup_bundle.manifest['fwup_bundle']
            if 'mcus' in fwup_bundle_data:
                # Multi-MCU: get params from mcus[role]
                role_map = {
                    wallet_pb.mcu_role.MCU_ROLE_CORE: 'core',
                    wallet_pb.mcu_role.MCU_ROLE_UXC: 'uxc',
                }
                role_key = role_map.get(mcu_role)
                if not role_key:
                    raise ValueError(f"Unknown MCU role: {mcu_role}")
                manifest_params = fwup_bundle_data['mcus'][role_key]['parameters']
            else:
                # Single-MCU: params at top level
                manifest_params = fwup_bundle_data.get('parameters', {})
                if not manifest_params:
                    # Fallback to wallet params if not in manifest
                    params = self.wallet.fwup_params(mcu)
                    params.version = fwup_bundle.manifest['fwup_bundle']['to_version']
                    manifest_params = None

            if manifest_params:
                params = FwupParams(
                    version=int(
                        fwup_bundle_data['to_version'].replace('.', '')),
                    chunk_size=manifest_params['wca_chunk_size'],
                    signature_offset=manifest_params['signature_offset'],
                    app_props_offset=manifest_params['app_properties_offset'],
                    signature_size=64  # ECC P256 signatures are always 64 bytes
                )
        else:
            assert False, mode

        if mode == wallet_pb.fwup_mode.FWUP_MODE_NORMAL:
            return Fwup(fwup_bundle.path, None, None, 0, comms=self.wallet.comms,
                        fwup_params=params, mode=wallet_pb.fwup_mode.Name(
                            fwup_bundle.mode),
                        mcu_role=mcu_role)
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

            # Handle multi-MCU delta manifests
            fwup_bundle_data = manifest['fwup_bundle']
            if 'mcus' in fwup_bundle_data:
                # Multi-MCU delta: assets are under mcus[role]
                # Convert proto role number to string key ('core' or 'uxc')
                role_map = {
                    wallet_pb.mcu_role.MCU_ROLE_CORE: 'core',
                    wallet_pb.mcu_role.MCU_ROLE_UXC: 'uxc',
                }
                role_key = role_map.get(mcu_role)
                if not role_key:
                    raise ValueError(f"Unknown MCU role: {mcu_role}")
                assets = fwup_bundle_data['mcus'][role_key]['assets']
            else:
                # Single-MCU delta: assets are at top level
                assets = fwup_bundle_data['assets']

            binary = fwup_bundle.path / assets[patch_name]['image']['name']
            signature = fwup_bundle.path / \
                assets[patch_name]['signature']['name']

            return Fwup(None, binary, signature, 0, comms=self.wallet.comms,
                        fwup_params=params, mode=wallet_pb.fwup_mode.Name(
                            fwup_bundle.mode),
                        mcu_role=mcu_role)
        else:
            assert False, mode

    def fwup(
        self: FirmwareUpdater,
        mcu: str,
        image: Path,
        signature: Path,
        params: FwupParams,
        timeout: Optional[int] = None
    ) -> bool:
        """Performs a firmware update of the specified target chip.

        The update performed is a complete update using a specific image. It is
        assumed that the image contains the signature.

        :param self: the update instance.
        :param mcu: target MCU name.
        :param image: path to the image to download to the target.
        :param signature: path to the image signature.
        :param params: FWUP parameters.
        :param timeout: optional timeout (in seconds) to wait for the update to finish.
        :returns: ``True`` if update was successful, otherwise ``False``.
        """
        role = self.wallet.chip_name_to_role(mcu)
        fwup = Fwup(
            bundle_dir=None,
            binary=image,
            signature=signature,
            comms=self.wallet.comms,
            mcu_role=role,
            fwup_params=params,
        )

        # Send a custom start message.
        ok = fwup.start()
        if ok:
            print("Firmware update successfully started.")
        else:
            print("Firmware update failed to start.")
            return False

        ok = fwup.transfer(timeout=timeout)
        if not ok:
            print("Firmware update failed.")
            return False

        result = fwup.finish()
        if result.fwup_finish_rsp.rsp_status == result.fwup_finish_rsp.SUCCESS:
            print("Firmware update finished successfully.")
        elif result.fwup_finish_rsp.rsp_status == result.fwup_finish_rsp.WILL_APPLY_PATCH:
            print("Firmware update transferred, applying patch now...")
        else:
            print("Firmware update failed.")
            print(result)
            return False

        return True

    def fwup_local(
        self,
        bundle: Path,
        timeout: Optional[int] = None,
        mcu: Optional[str] = "efr32"
    ):
        """Firmware update using the specified `bundle`."""
        assert bundle.exists()

        update = self._build_updater(FwupBundle(bundle), mcu=mcu)

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

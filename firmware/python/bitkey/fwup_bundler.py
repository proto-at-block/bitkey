import json
import os
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from shutil import copy

import jinja2
import semver
import yaml
from bitkey_proto import wallet_pb2 as wallet_pb

from .firmware_signer import FwupDeltaPatchGenerator
from .fwup import FwupParams


@dataclass
class McuConfig:
    """Configuration for an MCU in a multi-MCU FWUP bundle."""
    role: str  # "core" or "uxc"
    mcu_name: str  # "efr32" or "stm32u5"
    partition_product: str  # "w3a-core" or "w3a-uxc"
    include_bootloader: bool


@dataclass
class FwupDeltaInfo:
    """Information describing a delta update transition."""
    from_version: str
    to_version: str
    from_dir: Path  # Directory containing the files we're updating from.
    to_dir: Path  # Directory containing the files we're updating to.
    # Optional suffix override for from_dir filenames.
    from_image_type: Optional[str] = None

    @property
    def bundle_name(self):
        return f"fwup-bundle-delta-{self.from_version}-to-{self.to_version}"


@dataclass
class Patch:
    path: Path
    size: int


@dataclass
class DeltaBundle:
    a2b: Patch
    b2a: Patch
    zip_file: Path

    @property
    def max_size(self):
        """Return the larger of the two patch sizes."""
        return max(self.a2b.size, self.b2a.size)

    @property
    def valid(self):
        """Check if the patch passes validity rules."""
        MAX_PATCH_SIZE = 128 * 1024  # Matches fwup_delta.c
        return self.max_size < MAX_PATCH_SIZE


def load_patch_signing_key(image_type: str, version: str, product="w1a", base_directory=None) -> str:
    # For w1a: If the version is less than 1.0.52, use the dev key; we created the prod delta patch
    # signing key in 1.0.52.

    if semver.compare(version, "1.0.52") < 0 and product == "w1a":
        image_type = "dev"

    if image_type == "prod":
        # On Github Actions
        return os.environ["DELTA_PATCH_SIGNING_KEY_PROD"]
    else:
        if base_directory:
            directory = base_directory
        else:
            # Assume the script is being used from a development environment.
            # Locate the config directory.
            here = Path(__file__).parent.resolve()
            keys_dir = here / ".." / ".." / "config" / "keys"

            # For multi-MCU products (w3a), use the core platform keys
            if product == "w3a":
                directory = os.path.join(keys_dir, "w3a-core-" + image_type.lower())
                key_prefix = "w3a-core"
            else:
                directory = os.path.join(keys_dir, product.lower() + "-" + image_type.lower())
                key_prefix = product

        pem_path = os.path.join(
            directory, f"{key_prefix}-patch-signing-key-{image_type}.1.priv.pem")
        return open(pem_path, "r").read()


class FwupBundler:
    ENV = jinja2.Environment(
        loader=jinja2.FileSystemLoader(Path(
            __file__).parent.resolve() / ".." / ".." / "config" / "fwup"),
        trim_blocks=True,
        lstrip_blocks=True)

    def __init__(self, product, hardware_revision, image_type):
        self.product = product
        self.hardware_revision = hardware_revision
        self.image_type = image_type
        self.is_multi_mcu = self._is_multi_mcu_product(product)
        self.mcu_configs = self._get_mcu_configs(
            product) if self.is_multi_mcu else None

    def _is_multi_mcu_product(self, product):
        """Detect if product is multi-MCU (W3+). W1 products are single-MCU."""
        return product is not None and product.lower().startswith('w3')

    def _get_mcu_configs(self, product):
        """Return list of MCU configurations for multi-MCU products."""
        if product and product.lower().startswith('w3'):
            return [
                McuConfig(
                    role="core",
                    mcu_name="efr32",
                    partition_product=f"{product}-core",
                    include_bootloader=True
                ),
                McuConfig(
                    role="uxc",
                    mcu_name="stm32u5",
                    partition_product=f"{product}-uxc",
                    include_bootloader=False  # STM32U5 doesn't have signed bootloaders
                )
            ]
        return None

    def _render_template(self, template_name, output_dir, dict) -> Path:
        template = self.ENV.get_template(template_name)
        out_file = os.path.join(output_dir, os.path.basename(
            template.filename.replace(".jinja", "")))
        open(out_file, "w+").write(template.render(dict))
        return Path(out_file)

    def _write_json(self, yaml_file: Path):
        with open(yaml_file, 'r') as f:
            contents = yaml.safe_load(f)
            json_contents = json.dumps(contents)
        with open(yaml_file.with_suffix(".json"), 'w+') as f:
            f.write(json_contents)

    def bootloader_name(self):
        return f"{self.product}-{self.hardware_revision}-loader-{self.image_type}"

    def application_name(self, slot, image_type=None):
        image_type = image_type or self.image_type
        return f"{self.product}-{self.hardware_revision}-app-{slot}-{image_type}"

    def patch_name(self, from_slot, to_slot):
        return f"""{self.product}-{self.hardware_revision}-{from_slot}-to-{to_slot}"""

    def bootloader_name_for_mcu(self, mcu_config):
        """Generate bootloader name for specific MCU."""
        return f"{self.product}-{mcu_config.role}-{self.hardware_revision}-loader-{self.image_type}"

    def application_name_for_mcu(self, slot, mcu_config):
        """Generate application name for specific MCU."""
        return f"{self.product}-{mcu_config.role}-{self.hardware_revision}-app-{slot}-{self.image_type}"

    def application_name_for_mcu_with_type(self, slot, image_type, mcu_config):
        """Generate application name for specific MCU with explicit image type."""
        return f"{self.product}-{mcu_config.role}-{self.hardware_revision}-app-{slot}-{image_type}"

    def patch_name_for_mcu(self, from_slot, to_slot, mcu_config):
        """Generate patch name for specific MCU."""
        return f"{self.product}-{mcu_config.role}-{self.hardware_revision}-{from_slot}-to-{to_slot}"

    def _ensure_clean_dir(self, output_dir):
        """Empty the output dir if it exists. Otherwise, create it."""
        output_dir = Path(output_dir)
        if not output_dir.exists():
            os.makedirs(output_dir)
        for path in output_dir.glob("**/*"):
            if path.is_file():
                path.unlink()

    def generate_full(self, output_dir, files, version, include_bootloader=True):
        """Generate a FWUP bundle for a full firmware release.

        Routes to single-MCU or multi-MCU implementation based on product.
        """
        if self.is_multi_mcu:
            return self._generate_full_multi_mcu(output_dir, files, version)
        else:
            return self._generate_full_single_mcu(output_dir, files, version, include_bootloader)

    def _generate_full_single_mcu(self, output_dir, files, version, include_bootloader):
        """Generate a FWUP bundle for single-MCU products (W1)."""

        self._ensure_clean_dir(output_dir)

        params = {
            "manifest_version": "0.0.1",
            "product": self.product,
            "version": version,
            "bootloader_name": self.bootloader_name(),
            "application_a_name": self.application_name("a"),
            "application_b_name": self.application_name("b"),
            "fwup_params": FwupParams.from_product(self.product),
            "include_bootloader": include_bootloader,
        }
        yaml_file = self._render_template(
            "fwup-manifest.jinja.yml", output_dir, params)

        # Write JSON in addition to YAML since some mobile clients
        # have builtin support for JSON, but not YAML.
        self._write_json(yaml_file)

        for file in files:
            copy(file, output_dir)

        shutil.make_archive(output_dir, "zip", output_dir)

    def _generate_full_multi_mcu(self, output_dir, files, version):
        """Generate a FWUP bundle for multi-MCU products (W3+)."""

        self._ensure_clean_dir(output_dir)

        # Build MCU-specific parameters
        mcu_data = []
        for mcu_config in self.mcu_configs:
            fwup_params = FwupParams.from_product(mcu_config.partition_product)
            mcu_data.append({
                "role": mcu_config.role,
                "mcu_name": mcu_config.mcu_name,
                "bootloader_name": self.bootloader_name_for_mcu(mcu_config),
                "application_a_name": self.application_name_for_mcu("a", mcu_config),
                "application_b_name": self.application_name_for_mcu("b", mcu_config),
                "fwup_params": fwup_params,
                "include_bootloader": mcu_config.include_bootloader,
            })

        params = {
            "manifest_version": "0.0.2",
            "product": self.product,
            "version": version,
            "is_multi_mcu": True,
            "mcus": mcu_data,
        }

        # Render multi-MCU template but output as standard fwup-manifest.yml
        template = self.ENV.get_template("fwup-manifest-multi-mcu.jinja.yml")
        yaml_file = Path(output_dir) / "fwup-manifest.yml"
        yaml_file.write_text(template.render(params))
        self._write_json(yaml_file)

        for file in files:
            copy(file, output_dir)

        shutil.make_archive(output_dir, "zip", output_dir)

    def _generate_patch_and_copy_sig(self, from_slot, to_slot, patch_name, info, output_dir, params, key_pem) -> Patch:
        from_image_type = info.from_image_type or self.image_type
        to_image_type = self.image_type

        from_file = os.path.join(
            info.from_dir, self.application_name(from_slot, from_image_type) + ".signed.bin")
        to_file = os.path.join(
            info.to_dir, self.application_name(to_slot, to_image_type) + ".signed.bin")
        patch_file = os.path.join(output_dir,
                                  params[patch_name] + ".signed.patch")
        sig_file = os.path.join(
            info.to_dir, self.application_name(to_slot, to_image_type) + ".detached_signature")

        print(
            f"Generating {from_slot}->{to_slot} patch from {from_file} to {to_file}")
        FwupDeltaPatchGenerator().create_and_sign(
            key_pem, from_file, to_file, patch_file)
        copy(sig_file, output_dir)

        return Patch(path=Path(patch_file), size=os.stat(patch_file).st_size)

    def generate_delta(self, info: FwupDeltaInfo, output_dir: Path, patch_signing_key_pem: str) -> DeltaBundle:
        """Generate a FWUP bundle for a delta firmware release.

        Routes to single-MCU or multi-MCU implementation based on product.
        """
        if self.is_multi_mcu:
            return self._generate_delta_multi_mcu(info, output_dir, patch_signing_key_pem)
        else:
            return self._generate_delta_single_mcu(info, output_dir, patch_signing_key_pem)

    def _generate_delta_single_mcu(self, info: FwupDeltaInfo, output_dir: Path, patch_signing_key_pem: str) -> DeltaBundle:
        """Generate a FWUP bundle for delta firmware release (single-MCU products like W1)."""
        bundle_dir = Path(output_dir).joinpath(info.bundle_name)

        self._ensure_clean_dir(bundle_dir)

        params = {
            "manifest_version": "0.0.1",
            "product": self.product,
            "from_version": info.from_version,
            "to_version": info.to_version,
            "a2b_patch_name": self.patch_name("a", "b"),
            "b2a_patch_name": self.patch_name("b", "a"),
            "application_a_name": self.application_name("a"),
            "application_b_name": self.application_name("b"),
            "fwup_params": FwupParams.from_product(self.product),
        }
        yaml_file = self._render_template(
            "fwup-delta-manifest.jinja.yml", bundle_dir, params)
        self._write_json(yaml_file)

        a2b = self._generate_patch_and_copy_sig(
            "a", "b", "a2b_patch_name", info, bundle_dir, params, patch_signing_key_pem)
        b2a = self._generate_patch_and_copy_sig(
            "b", "a", "b2a_patch_name", info, bundle_dir, params, patch_signing_key_pem)

        shutil.make_archive(bundle_dir, "zip", bundle_dir)

        # Note: don't use with_suffix here, since it'll lop off the stuff
        # after the last `.`
        return DeltaBundle(a2b, b2a, Path(str(bundle_dir) + ".zip"))

    def _generate_delta_multi_mcu(self, info: FwupDeltaInfo, output_dir: Path, patch_signing_key_pem: str) -> DeltaBundle:
        """Generate a FWUP bundle for delta firmware release (multi-MCU products like W3)."""
        bundle_dir = Path(output_dir).joinpath(info.bundle_name)

        self._ensure_clean_dir(bundle_dir)

        # Build MCU-specific parameters
        mcu_data = []
        all_patches = []
        for mcu_config in self.mcu_configs:
            fwup_params = FwupParams.from_product(mcu_config.partition_product)

            # Generate patches for this MCU
            a2b_patch_name = self.patch_name_for_mcu("a", "b", mcu_config)
            b2a_patch_name = self.patch_name_for_mcu("b", "a", mcu_config)

            mcu_params = {
                "a2b_patch_name": a2b_patch_name,
                "b2a_patch_name": b2a_patch_name,
                "application_a_name": self.application_name_for_mcu("a", mcu_config),
                "application_b_name": self.application_name_for_mcu("b", mcu_config),
            }

            a2b = self._generate_patch_and_copy_sig_for_mcu(
                "a", "b", "a2b_patch_name", info, bundle_dir, mcu_params, patch_signing_key_pem, mcu_config)
            b2a = self._generate_patch_and_copy_sig_for_mcu(
                "b", "a", "b2a_patch_name", info, bundle_dir, mcu_params, patch_signing_key_pem, mcu_config)

            all_patches.extend([a2b, b2a])

            mcu_data.append({
                "role": mcu_config.role,
                "mcu_name": mcu_config.mcu_name,
                "a2b_patch_name": a2b_patch_name,
                "b2a_patch_name": b2a_patch_name,
                "application_a_name": self.application_name_for_mcu("a", mcu_config),
                "application_b_name": self.application_name_for_mcu("b", mcu_config),
                "fwup_params": fwup_params,
            })

        params = {
            "manifest_version": "0.0.2",
            "product": self.product,
            "from_version": info.from_version,
            "to_version": info.to_version,
            "is_multi_mcu": True,
            "mcus": mcu_data,
        }

        # Render multi-MCU template but output as standard fwup-manifest.yml
        template = self.ENV.get_template(
            "fwup-delta-manifest-multi-mcu.jinja.yml")
        yaml_file = Path(bundle_dir) / "fwup-manifest.yml"
        yaml_file.write_text(template.render(params))
        self._write_json(yaml_file)

        shutil.make_archive(bundle_dir, "zip", bundle_dir)

        # Return largest patches for compatibility with DeltaBundle interface
        if not all_patches or len(all_patches) < 2:
            raise ValueError(
                f"Expected at least 2 patches (a2b and b2a), got {len(all_patches)}")

        a2b_patches = [p for i, p in enumerate(all_patches) if i % 2 == 0]
        b2a_patches = [p for i, p in enumerate(all_patches) if i % 2 == 1]
        max_a2b = max(a2b_patches, key=lambda p: p.size)
        max_b2a = max(b2a_patches, key=lambda p: p.size)
        return DeltaBundle(max_a2b, max_b2a, Path(str(bundle_dir) + ".zip"))

    def _generate_patch_and_copy_sig_for_mcu(self, from_slot, to_slot, patch_name, info, output_dir, params, key_pem, mcu_config) -> Patch:
        """Generate patch for specific MCU."""
        from_image_type = info.from_image_type or self.image_type
        to_image_type = self.image_type

        from_file = os.path.join(
            info.from_dir, self.application_name_for_mcu_with_type(from_slot, from_image_type, mcu_config) + ".signed.bin")
        to_file = os.path.join(
            info.to_dir, self.application_name_for_mcu_with_type(to_slot, to_image_type, mcu_config) + ".signed.bin")
        patch_file = os.path.join(output_dir,
                                  params[patch_name] + ".signed.patch")
        sig_file = os.path.join(
            info.to_dir, self.application_name_for_mcu_with_type(to_slot, to_image_type, mcu_config) + ".detached_signature")

        print(
            f"Generating {mcu_config.role} {from_slot}->{to_slot} patch from {from_file} to {to_file}")
        FwupDeltaPatchGenerator().create_and_sign(
            key_pem, from_file, to_file, patch_file)
        copy(sig_file, output_dir)

        return Patch(path=Path(patch_file), size=os.stat(patch_file).st_size)

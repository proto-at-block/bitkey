import io
import os
import tempfile
from pathlib import Path
from typing import Optional, Union

import click
import detools
import semver
import sh
from bitkey import fw_version
from bitkey.comms import ShellTransaction, WalletComms
from bitkey.firmware_signer import (ECC_P256_SIG_SIZE, apply_patch,
                                    verify_firmware_signature_with_padding,
                                    verify_patch_signature)
from bitkey.fwa.bitkey_fwa.constants import PRODUCTS
from bitkey.fwup import Fwup, FwupParams
from bitkey.fwup_bundler import (FwupBundler, FwupDeltaInfo,
                                 load_patch_signing_key)
from bitkey.partition_info import get_application_partition_size
from bitkey.key_manager import LocalKeyManager, PatchSigningKeys, SigningKeys
from bitkey.meson import MesonBuild
from bitkey.wallet import Wallet
from bitkey_proto import wallet_pb2
from Crypto.Hash import SHA256
from Crypto.PublicKey import ECC
from Crypto.Signature import DSS
from invoke import task

from .lib.paths import BUILD_FW_DIR, BUILD_FWUP_BUNDLE_DIR
from .memfault import fetch_release, released_versions

MIN_DELTA_VERSION = "1.0.44"

# Due to Memfault limitations around signals, we cannot have per-product
# projects, so all products are namespaced under the `w1a` project.
_MEMFAULT_PROJECT_NAME = "w1a"

# Always 'Dev' - Memfault sw_type doesn't distinguish prod/dev, that's in hw_revision
_MEMFAULT_SW_TYPES = ["Dev"]


def check_exists(path: str):
    if not path:
        return None
    p = Path(path)
    if not p.exists():
        click.echo(click.style(f"'{p}' not found", fg="red"))
        return None
    return p


def _fwup_valid_delta_update(product: str, start_version: str, end_version: str) -> bool:
    """Returns a boolean indicating if the given product can perform a delta
    FWUP from the start version to the destination end version.

    :param product: the product being updated.
    :param start_version: the initial firmware version.
    :param end_version: the destination firmware version.
    :returns: ``True`` if delta update is possible, otherwise ``False``.
    """
    if product.lower().startswith("w1"):
        if semver.compare(MIN_DELTA_VERSION, start_version) >= 0:
            return False

    return semver.compare(start_version, end_version) < 0


def _fwup_memfault_revision_name(product: str, revision: str, image_type: str) -> str:
    """Returns the revision name to use for uploading an update to Memfault.

    :param product: the product name.
    :param revision: the hardware revision of the product.
    :param image_type: the build type for the update (i.e. "dev", "prod").
    :returns: hardware revision name to use on upload to Memfault.
    """
    if product.lower().startswith("w1"):
        # Legacy for W1. Does not include the product name.
        if image_type.lower() != "prod":
            # Legacy for W1. Non-production does not include image type.
            return f"{revision}"
        return f"{revision}-{image_type.lower()}"
    return f"{product}-{revision}-{image_type.lower()}"


@task(
    default=True,
    help={
        "fwup-bundle": "",
        "binary": "",
        "signature": "",
        "start_sequence_id": "",
        "serial_port": "",
        "mode": "",
        "mcu": "",
        "product": "",
    },
)
def fwup(
    c,
    fwup_bundle=None,
    binary=None,
    signature=None,
    start_sequence_id=0,
    serial_port=None,
    mode="FWUP_MODE_NORMAL",
    mcu="efr32",
    product="w1",
):
    """Firmware update"""
    bundle = check_exists(fwup_bundle)
    bin = check_exists(binary)
    sig = check_exists(signature)

    if not bundle and not (bin and sig):
        click.echo("Invalid arguments. Need a bundle, or (binary and signature).")
        return

    # Determine which MCU is being updated (defaults to EFR32).
    mcu_role = Wallet.chip_name_to_role(product, mcu)
    # Only pass fwup_params if not using a bundle (bundle has params in manifest)
    fwup_params = Wallet(product=product).fwup_params(
        mcu=mcu) if not bundle else None

    if serial_port != None:
        comms = WalletComms(ShellTransaction(port=serial_port))
        update = Fwup(bundle, bin, sig, start_sequence_id, comms=comms,
                      fwup_params=fwup_params, mode=mode, mcu_role=mcu_role)
    else:
        update = Fwup(bundle, bin, sig, start_sequence_id,
                      mode=mode, fwup_params=fwup_params, mcu_role=mcu_role)
    result = update.start()
    if not result:
        click.secho("Failed to start", fg="red")
        return

    click.echo("Firmware update in progress...")
    update.transfer()
    result = update.finish()
    if result.fwup_finish_rsp.rsp_status == result.fwup_finish_rsp.SUCCESS:
        click.echo("Firmware update finished successfully.")
    elif result.fwup_finish_rsp.rsp_status == result.fwup_finish_rsp.WILL_APPLY_PATCH:
        click.echo("Firmware update transferred, applying patch now...")
    else:
        click.echo("Firmware update failed.")
        click.echo(result)


@task(
    help={
        "binary": "Path to bootloader .signed.bin",
        "signature": "Path to bootloader .detached_signature",
        "metadata": "Path to bootloader .detached_metadata",
        "serial_port": "",
        "mcu": "",
        "product": "",
        "variant": "",
    }
)
def bl_upgrade(
    c,
    binary=None,
    signature=None,
    metadata=None,
    serial_port=None,
    bl_size=(48 * 1024),
    mcu="efr32",
    product="w1",
    variant="a",
):
    bin = check_exists(binary)
    sig = check_exists(signature)
    meta = check_exists(metadata)

    if not (bin and sig and meta):
        click.echo("Need --binary, --signature, and --metadata")
        return

    # Determine which MCU is being updated (defaults to EFR32).
    mcu_role = Wallet.chip_name_to_role(product, mcu)
    role_name = wallet_pb2.mcu_role.Name(mcu_role).split("_")[-1].lower()
    if product.startswith("w1"):
        params = FwupParams.from_product("w1")
    else:
        _product_name = f"{product}{variant}-{role_name}"
        params = FwupParams.from_product(_product_name)

    if serial_port != None:
        comms = WalletComms(ShellTransaction(port=serial_port))
        update = Fwup(None, bin, sig, 0, comms=comms,
                      mcu_role=mcu_role, fwup_params=params)
    else:
        update = Fwup(None, bin, sig, 0, mcu_role=mcu_role, fwup_params=params)
    result = update.start()
    if not result:
        click.secho("Firmware update failed to start.", fg="red")
        return

    click.echo("Firmware update in progress...")

    SIGNATURE_SIZE = 64
    METADATA_SIZE = 1024

    update.params.app_props_offset = 0  # Not used for BL upgrade
    update.params.signature_offset = bl_size - SIGNATURE_SIZE

    update.transfer_bytes(bin.read_bytes(), 0, 0)

    sig_offset = bl_size - SIGNATURE_SIZE
    update.transfer_bytes(sig.read_bytes(), 0, sig_offset)

    meta_offset = bl_size - SIGNATURE_SIZE - METADATA_SIZE
    update.transfer_bytes(meta.read_bytes(), 0, meta_offset)

    click.echo("About to finish")

    result = update.finish(True)
    click.echo("Finished")
    if result.fwup_finish_rsp.rsp_status == result.fwup_finish_rsp.SUCCESS:
        click.echo("Firmware update finished successfully.")
    else:
        click.echo("Firmware update failed.")
        click.echo(result)


@task(
    help={
        "product": "",
        "platform": "",
        "hardware_revision": "",
        "image_type": "",
        "version": "",
        "build_dir": "",
        "bundle_dir": "",
    }
)
def bundle(
    c,
    product: Optional[str] = None,
    platform: str = "w1",
    hardware_revision: Optional[str] = None,
    image_type: Optional[str] = None,
    version: Optional[str] = None,
    build_dir: Optional[str] = None,
    bundle_dir: Optional[str] = None
):
    """Generate fwup bundle (single-MCU for W1, unified multi-MCU for W3)"""
    if not version:
        version = fw_version.get()

    bundle_dir = Path(bundle_dir) if bundle_dir else BUILD_FWUP_BUNDLE_DIR
    bundler = FwupBundler(product, hardware_revision, image_type)

    if bundler.is_multi_mcu:
        # W3: Collect files from multiple build directories
        files = []
        for mcu_config in bundler.mcu_configs:
            # Build platform name: w3-core, w3-uxc
            if platform.startswith("w3a"):
                mcu_platform = f"w3-{mcu_config.role}"
            else:
                mcu_platform = f"{platform}-{mcu_config.role}"

            if build_dir:
                build_dir_mcu = Path(build_dir) / mcu_platform

                def f(file):
                    return build_dir_mcu.joinpath(file)
            else:
                build_dir_mcu = BUILD_FW_DIR.joinpath(mcu_platform)
                meson = MesonBuild(c, build_dir=build_dir_mcu)

                def f(file):
                    return meson.find_file(file)

            # Collect application files
            files.extend(
                [
                    f(bundler.application_name_for_mcu(
                        "a", mcu_config) + ".signed.bin"),
                    f(bundler.application_name_for_mcu(
                        "a", mcu_config) + ".detached_signature"),
                    f(bundler.application_name_for_mcu(
                        "b", mcu_config) + ".signed.bin"),
                    f(bundler.application_name_for_mcu(
                        "b", mcu_config) + ".detached_signature"),
                ]
            )

            # Collect bootloader (if applicable for this MCU)
            if mcu_config.include_bootloader:
                files.extend(
                    [
                        f(bundler.bootloader_name_for_mcu(
                            mcu_config) + ".signed.bin"),
                        f(bundler.bootloader_name_for_mcu(
                            mcu_config) + ".detached_signature"),
                    ]
                )

        bundler.generate_full(bundle_dir, files, version)
    else:
        # W1: Use existing logic (UNCHANGED)
        build_dir = Path(
            build_dir) if build_dir else BUILD_FW_DIR.joinpath(platform)
        meson = MesonBuild(c, build_dir=build_dir)

        def f(file):
            return meson.find_file(file)

        # STM32U5-based platforms (w3-uxc) don't have signed bootloaders
        stm32u5_platforms = ["w3-uxc"]
        include_bootloader = platform not in stm32u5_platforms

        files = [
            f(bundler.application_name("a") + ".signed.bin"),
            f(bundler.application_name("a") + ".detached_signature"),
            f(bundler.application_name("b") + ".signed.bin"),
            f(bundler.application_name("b") + ".detached_signature"),
        ]

        if include_bootloader:
            files.extend(
                [
                    f(bundler.bootloader_name() + ".signed.bin"),
                    f(bundler.bootloader_name() + ".detached_signature"),
                ]
            )

        bundler.generate_full(bundle_dir, files, version,
                              include_bootloader=include_bootloader)


@task(
    help={
        "product": "",
        "hardware_revision": "",
        "image_type": "",
        "from_version": "",
        "to_version": "",
        "from_dir": "",
        "to_dir": "",
        "bundle_dir": "",
        "from_image_type": "",
    }
)
def bundle_delta(
    c,
    product=None,
    hardware_revision=None,
    image_type=None,
    from_version=None,
    to_version=None,
    from_dir=None,
    to_dir=None,
    bundle_dir=None,
    from_image_type=None,
):
    """Generate a FWUP delta bundle"""

    # All args are required.
    if (
        not product
        or not hardware_revision
        or not image_type
        or not from_version
        or not to_version
        or not from_dir
        or not to_dir
        or not bundle_dir
    ):
        click.echo("Invalid arguments.")
        return

    key_pem = load_patch_signing_key(image_type, from_version, product)

    bundler = FwupBundler(product, hardware_revision, image_type)
    bundler.generate_delta(FwupDeltaInfo(from_version, to_version, from_dir,
                           to_dir, from_image_type), bundle_dir, key_pem)


@task(
    help={
        "to_version": "",
        "to_dir": "",
        "hw_revision": "",
        "revision": "",
        "bearer_token": "",
        "image_type": "",
        "product": "",
        "dont_upload": "",
        "from_version": "Single from_version to generate patch for (use with --from-dir)",
        "from_dir": "Local directory with from_version files (use with --from-version)",
        "output_dir": "Output directory to write patch files to",
    }
)
def delta_release_local(
    c,
    to_version: Optional[str] = None,
    to_dir: Optional[str] = None,
    hw_revision: Optional[str] = None,
    revision: Optional[str] = None,
    bearer_token: Optional[str] = None,
    image_type: str = 'dev',
    dont_upload: bool = False,
    from_version: Optional[str] = None,
    from_dir: Optional[str] = None,
    output_dir: Optional[str] = None,
    product: str = "",
) -> None:
    """Generate and upload delta releases using local files for to_version.

    Use this when you have signed images locally (e.g., from the firmware signer)
    but haven't uploaded a full bundle to Memfault yet.

    If --from-version and --from-dir are provided, generates a single patch from
    local files (for versions not on Memfault). Otherwise, iterates through all
    versions on Memfault.
    """

    if not to_version or not to_dir or not hw_revision or not revision or not bearer_token:
        raise click.UsageError(
            "Need: to_version, to_dir, hw_revision, revision, bearer_token")

    if not product:
        raise click.UsageError("Missing product argument: --product <product>")

    if (from_version is None) != (from_dir is None):
        raise click.UsageError(
            "Must provide both --from-version and --from-dir, or neither")

    os.environ["MEMFAULT_ORG_TOKEN"] = bearer_token

    memfault_hw_revision = _fwup_memfault_revision_name(
        product, hw_revision, image_type)
    sw_types = _MEMFAULT_SW_TYPES

    if output_dir:
        output_dir = Path(output_dir)
    else:
        output_dir = tempfile.TemporaryDirectory()
    click.echo(f"Will write patches to {output_dir.name}")
    click.echo(f"Using local to_version dir: {to_dir}")

    def generate_and_upload_patch(version, from_bundle):
        """Generate and upload a single delta patch."""
        bundler = FwupBundler(product, hw_revision, image_type)

        key_pem = load_patch_signing_key(image_type, version, product)
        delta_bundle = bundler.generate_delta(
            FwupDeltaInfo(version, to_version, from_bundle,
                          to_dir), output_dir.name, key_pem
        )

        click.echo(f"Patch max size: {delta_bundle.max_size}")

        if delta_bundle.valid:
            if dont_upload:
                click.echo("Skipping upload")
            else:
                for sw_type in sw_types:
                    sh.memfault(
                        "--org-token",
                        bearer_token,
                        "--org",
                        "block-wallet",
                        "--project",
                        _MEMFAULT_PROJECT_NAME,
                        "upload-ota-payload",
                        "--hardware-version",
                        memfault_hw_revision,
                        "--software-type",
                        sw_type,
                        "--delta-from",
                        version,
                        "--delta-to",
                        to_version,
                        "--revision",
                        revision,
                        delta_bundle.zip_file,
                    )
                    click.echo(
                        f"Uploaded {version} -> {to_version} {memfault_hw_revision} {sw_type}")
        else:
            click.echo(
                f"Can't release {version} -- patch too large ({delta_bundle.max_size} bytes)")

    if from_version and from_dir:
        click.echo(f"Using local from_version dir: {from_dir}")
        generate_and_upload_patch(from_version, Path(from_dir))
    else:
        versions = []
        for version in released_versions(c, _MEMFAULT_PROJECT_NAME, memfault_hw_revision, quiet=True):
            if _fwup_valid_delta_update(product, version, to_version):
                versions.append(version)

        for version in versions:
            for sw_type in sw_types:
                fwup_bundle = fetch_release(
                    c, version, memfault_hw_revision, sw_type, output_dir.name, project=_MEMFAULT_PROJECT_NAME)
                if not fwup_bundle:
                    click.echo(f"Skipping {version} - not found on Memfault")
                    continue
                click.echo(
                    f"Downloaded {memfault_hw_revision} {sw_type} {version}")
                generate_and_upload_patch(version, fwup_bundle)

    click.echo("Done")
    if hasattr(output_dir, "cleanup"):
        output_dir.cleanup()


@task(
    help={
        "from_binary": "Path to the source firmware .signed.bin",
        "patch_file": "Path to the .signed.patch file",
        "signature": "Optional path to a signature file",
        "to_binary": "Path to the expected target firmware .signed.bin (optional, for comparison)",
        "image_type": "dev or prod (default: dev)",
        "from_version": "Source firmware version (e.g. 1.0.100) - used to select signing key",
        "product": "Product type: w1a, w3a-core, w3a-uxc (default: w1a for backwards compatibility)",
    }
)
def verify_delta(
    c,
    from_binary: Optional[Union[str, Path]] = None,
    patch_file: Optional[Union[str, Path]] = None,
    to_binary: Optional[Union[str, Path]] = None,
    image_type: str = "dev",
    from_version: Optional[str] = None,
    signature: Optional[str] = None,
    product: str = "w1a"
):
    """Verify a delta patch by applying it and comparing to expected output.

    This tool:
    1. Verifies the patch signature
    2. Applies the patch to from_binary
    3. Compares result to to_binary (if provided)

    Uses firmware_signer functions for patch verification and application.
    """
    if not from_binary or not patch_file:
        click.echo("Need at least --from-binary and --patch-file")
        return

    from_binary = Path(from_binary)
    patch_file = Path(patch_file)

    if not from_binary.exists():
        click.echo(f"From binary not found: {from_binary}")
        return
    if not patch_file.exists():
        click.echo(f"Patch file not found: {patch_file}")
        return

    with open(patch_file, "rb") as f:
        patch_with_sig = f.read()

    patch_data = patch_with_sig[:-ECC_P256_SIG_SIZE]

    click.echo(f"Patch file: {patch_file}")
    click.echo(
        f"Patch size: {len(patch_data)} bytes + {ECC_P256_SIG_SIZE} byte signature")

    here: Path = Path(__file__).parent.resolve()
    keys_dir: Path = here / ".." / "config" / "keys"

    # W1A backwards compatibility: versions < 1.0.52 always used dev keys
    if product == "w1a" and from_version and semver.compare(from_version, "1.0.52") < 0:
        key_type: str = "dev"
    else:
        key_type: str = image_type

    # Verify patch signature using firmware_signer
    if from_version:
        try:
            patch_keys = PatchSigningKeys(keys_dir, product, key_type)
            key_manager = LocalKeyManager(patch_keys)

            success, error = verify_patch_signature(patch_file, key_manager)
            if success:
                click.echo(click.style("✓ Patch signature VALID", fg="green"))
            else:
                click.echo(click.style(
                    f"✗ Patch signature INVALID: {error}", fg="red"))
                return
        except AssertionError as e:
            click.echo(
                f"Key directory not found: {e}, skipping signature verification")
    else:
        click.echo("No --from-version provided, skipping signature verification")

    # Apply patch using firmware_signer
    click.echo(f"\nApplying patch to: {from_binary}")

    with tempfile.NamedTemporaryFile(delete=False, suffix=".signed.bin") as tmp:
        output_path = Path(tmp.name)

    try:
        success, error = apply_patch(from_binary, patch_file, output_path)
        if not success:
            click.echo(click.style(
                f"✗ Patch application FAILED: {error}", fg="red"))
            return

        with open(output_path, "rb") as f:
            result_data = f.read()

        click.echo(f"Result size: {len(result_data)} bytes")
        click.echo(click.style("✓ Patch applied successfully", fg="green"))

        if signature:
            with open(signature, "rb") as f:
                signature_data: bytes = f.read()

            partition_name = next(
                (p for p in PRODUCTS if p in str(signature)), None)
            if partition_name:
                slot_size = get_application_partition_size(partition_name)

                firmware_keys = SigningKeys(
                    keys_dir, partition_name, key_type, "app")
                firmware_key_manager = LocalKeyManager(firmware_keys)

                success, error = verify_firmware_signature_with_padding(
                    result_data,
                    signature_data,
                    slot_size,
                    firmware_key_manager)
                if not success:
                    click.secho(
                        f"✗ Signature verification failed: {error}", fg="red")
                    return
                click.secho("✓ Signature verification succeeded", fg="green")
            else:
                click.secho(
                    "Skipping firmware signature verification - could not determine "
                    "partition name from signature path",
                    fg="yellow",
                )
    finally:
        output_path.unlink(missing_ok=True)

    # Compare to expected binary if provided
    if to_binary:
        to_binary = Path(to_binary)
        if not to_binary.exists():
            click.echo(f"To binary not found: {to_binary}")
            return

        with open(to_binary, "rb") as f:
            expected_data = f.read()

        if result_data == expected_data:
            click.echo(click.style(
                "✓ Result matches expected target binary", fg="green"))
        else:
            click.echo(click.style(
                "✗ Result does NOT match expected target binary", fg="red"))
            click.echo(
                f"  Expected size: {len(expected_data)}, Got: {len(result_data)}")

            for i, (a, b) in enumerate(zip(result_data, expected_data)):
                if a != b:
                    click.echo(
                        f"  First difference at offset {i}: expected {b:02x}, got {a:02x}")
                    break
            return

    click.echo("\n✓ Delta verification complete")


@task(
    help={
        "from_version": "Source firmware version (e.g. 1.0.100)",
        "to_version": "Target firmware version (e.g. 1.0.101)",
        "hw_revision": "Hardware revision (e.g. dvt or dvt-prod)",
        "bearer_token": "Memfault org token",
        "image_type": "dev or prod (default: dev)",
        "from_dir": "Local directory with from_version files (optional, uses Memfault if not provided)",
        "to_dir": "Local directory with to_version files (optional, uses Memfault if not provided)",
        "product": "Target product (e.g. w1a, w3a)",
        "output_dir": "Output directory to write generated and downloaded files to"
    }
)
def verify_delta_release(
    c,
    from_version: Optional[str] = None,
    to_version: Optional[str] = None,
    hw_revision: Optional[str] = None,
    bearer_token: Optional[str] = None,
    image_type: str = "dev",
    from_dir: Optional[str] = None,
    to_dir: Optional[str] = None,
    product: str = "w1a",
    output_dir: Optional[str] = None
):
    """Verify a delta transition by generating a patch and applying it.

    This downloads from_version from Memfault (and to_version if not using --to-dir),
    generates a delta patch, applies it, and verifies the result matches.
    """
    if not from_version or not to_version or not hw_revision or not bearer_token:
        raise click.UsageError(
            "Need: --from-version, --to-version, --hw-revision, --bearer-token")

    os.environ["MEMFAULT_ORG_TOKEN"] = bearer_token

    sw_type = "Dev"

    if output_dir:
        output_dir = Path(output_dir)
    else:
        output_dir = tempfile.TemporaryDirectory()

    memfault_hw_rev = _fwup_memfault_revision_name(
        product, hw_revision, image_type)
    bundler_hw_rev = hw_revision.split("-")[0]

    click.echo(f"=== Verifying delta: {from_version} -> {to_version} ===")
    click.echo(
        f"Memfault hw_revision: {memfault_hw_rev}, file hw_rev: {bundler_hw_rev}, image_type: {image_type}\n")

    if from_dir:
        from_release = Path(from_dir)
        click.echo(f"Using local from_version dir: {from_dir}")
    else:
        from_release = fetch_release(
            c, from_version, memfault_hw_rev, sw_type, output_dir.name, project=_MEMFAULT_PROJECT_NAME)
        if not from_release:
            click.echo(
                f"Could not fetch from_version {from_version} from Memfault (hw={memfault_hw_rev})")
            click.echo("Hint: Use --from-dir with local signed images")
            return
        click.echo(f"Downloaded {from_version}")

    if to_dir:
        to_release = Path(to_dir)
        click.echo(f"Using local to_version dir: {to_dir}")
    else:
        to_release = fetch_release(
            c, to_version, memfault_hw_rev, sw_type, output_dir.name, project=_MEMFAULT_PROJECT_NAME)
        if not to_release:
            click.echo(
                f"Could not fetch to_version {to_version} from Memfault (hw={memfault_hw_rev})")
            click.echo(
                "Hint: If only delta releases were uploaded, use --to-dir with local signed images")
            return
        click.echo(f"Downloaded {to_version}")

    key_pem = load_patch_signing_key(image_type, from_version, product)

    bundler = FwupBundler(product, bundler_hw_rev, image_type)
    delta_info = FwupDeltaInfo(
        from_version, to_version, from_release, to_release)

    click.echo("\nGenerating delta bundle...")
    delta_bundle = bundler.generate_delta(delta_info, output_dir.name, key_pem)
    click.echo(f"Patch max size: {delta_bundle.max_size} bytes")

    if not delta_bundle.valid:
        click.echo(click.style(
            f"✗ Patch too large ({delta_bundle.max_size} > 128KB)", fg="red"))
        return

    try:
        for binary_name in os.listdir(delta_info.from_dir):
            a2b = f"-a-{image_type}.signed.bin" in binary_name
            b2a = f"-b-{image_type}.signed.bin" in binary_name
            if not a2b and not b2a:
                continue

            from_binary: Path = delta_info.from_dir / binary_name
            from_slot: str = "a" if a2b else "b"
            to_slot: str = "b" if a2b else "a"
            to_binary: Path = delta_info.to_dir / \
                binary_name.replace(f"-{from_slot}-", f"-{to_slot}-")
            if bundler.is_multi_mcu:
                patches = (
                    delta_bundle.a2b_patches if a2b else delta_bundle.b2a_patches) or []
                for patch in patches:
                    basename = os.path.basename(patch.path)
                    target_name = basename.replace(
                        f"-{from_slot}-to-{to_slot}.signed.patch", "")
                    if target_name in os.path.basename(from_binary):
                        patch_file: Path = patch.path
                        signature: Path = Path(os.path.dirname(patch.path)) / binary_name.replace(
                            f"-{from_slot}-", f"-{to_slot}-").replace(".signed.bin", ".detached_signature")
                        break
                else:
                    raise RuntimeError(
                        f"No matching patch found for {from_binary}")
            else:
                patch_file: Path = delta_bundle.a2b.path if a2b else delta_bundle.b2a.path
                signature: Optional[Path] = None

            to_version = delta_info.to_version
            from_version = delta_info.from_version
            click.echo(
                f"{os.linesep}Verifying patch ({patch_file}) from {from_version} ({from_binary}) to {to_version} ({to_binary}) ({signature=})")
            verify_delta(
                c,
                from_binary=from_binary,
                patch_file=patch_file,
                to_binary=to_binary,
                image_type=image_type,
                from_version=from_version,
                product=product,
                signature=signature,
            )
    finally:
        if hasattr(output_dir, "cleanup"):
            output_dir.cleanup()


@task(
    help={
        "to_version": "",
        "revision": "",
        "bearer_token": "",
        "image_type": "",
        "dont_upload": "",
        "product": "str"
    }
)
def delta_release(
    c,
    to_version: Optional[str] = None,
    revision: Optional[str] = None,
    bearer_token: Optional[str] = None,
    image_type: str = 'dev',
    product: str = "",
    dont_upload: bool = False
) -> None:
    """Generate and upload all possible delta releases"""

    if not to_version or not revision or not bearer_token:
        raise click.UsageError("Need: to_version, revision, bearer_token")

    if not product:
        raise click.UsageError("Missing product argument: --product <product>")

    os.environ["MEMFAULT_ORG_TOKEN"] = bearer_token

    hw_revisions = ["evt", "dvt"]
    memfault_hw_revisions = []
    sw_types = _MEMFAULT_SW_TYPES

    if image_type in ["prod", "dev"]:
        for i, rev in enumerate(hw_revisions):
            if product.lower() == "w1a" and image_type != "prod":
                # For W1A dev images, keep the hardware revision without a suffix.
                pass
            else:
                hw_revisions[i] = f"{rev}-{image_type}"
            memfault_hw_revisions.append(
                _fwup_memfault_revision_name(product, rev, image_type))

    versions = set()
    for memfault_hw_revision in memfault_hw_revisions:
        for version in released_versions(c, _MEMFAULT_PROJECT_NAME, memfault_hw_revision, quiet=True):
            if _fwup_valid_delta_update(product, version, to_version):
                versions.add(version)

    to_version_dirs = {}

    output_dir = tempfile.TemporaryDirectory()

    click.echo(f"Will write patches to {output_dir.name}")

    # Download the full release for the version that we're generating delta releases for
    for memfault_hw_revision in memfault_hw_revisions:
        to_version_dirs[memfault_hw_revision] = {}
        for sw_type in sw_types:
            release = fetch_release(
                c, to_version, memfault_hw_revision, sw_type, output_dir.name, project=_MEMFAULT_PROJECT_NAME)
            if release:
                to_version_dirs[memfault_hw_revision][sw_type] = release
                click.echo(
                    f"Downloaded {memfault_hw_revision} {sw_type} {to_version}")

    click.echo("\nGenerating patches...\n")

    for version in versions:
        # Download each release, generate a patch, and upload it
        for hw_revision, memfault_hw_revision in zip(hw_revisions, memfault_hw_revisions):
            for sw_type in sw_types:
                fwup_bundle = fetch_release(
                    c, version, memfault_hw_revision, sw_type, output_dir.name, project=_MEMFAULT_PROJECT_NAME)
                if not fwup_bundle:
                    click.echo("Continuing...")
                    continue
                click.echo(
                    f"Downloaded {memfault_hw_revision} {sw_type} {version}")

                to_version_dir = to_version_dirs[memfault_hw_revision][sw_type]

                # This is gross. But, the split('-') here is because Memfault requires us to
                # upload prod vs dev as different hardware revisions, e.g. dvt-prod vs dvt.
                # But, our file naming scheme separates hw_revision and image_type into
                # different fields.
                # So here, we transform from the Memfault hw revision to our own.
                bundler = FwupBundler(
                    product, hw_revision.split("-")[0], image_type)

                key_pem = load_patch_signing_key(image_type, version, product)
                delta_bundle = bundler.generate_delta(
                    FwupDeltaInfo(version, to_version, fwup_bundle,
                                  to_version_dir), output_dir.name, key_pem
                )

                click.echo(f"Patch max size: {delta_bundle.max_size}")

                # Upload the release.
                if delta_bundle.valid:
                    if dont_upload:
                        click.echo("Skipping upload")
                    else:
                        sh.memfault(
                            "--org-token",
                            bearer_token,
                            "--org",
                            "block-wallet",
                            "--project",
                            _MEMFAULT_PROJECT_NAME,
                            "upload-ota-payload",
                            "--hardware-version",
                            memfault_hw_revision,
                            "--software-type",
                            sw_type,
                            "--delta-from",
                            version,
                            "--delta-to",
                            to_version,
                            "--revision",
                            revision,
                            delta_bundle.zip_file,
                        )
                        click.echo(
                            f"Uploaded {version} -> {to_version} {memfault_hw_revision} {sw_type}")
                else:
                    click.echo(f"Can't release {version} -- not valid")

    click.echo("Done")
    output_dir.cleanup()

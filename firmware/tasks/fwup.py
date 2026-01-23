import io
import os
import tempfile

import click
import detools
import semver
import sh
from Crypto.Hash import SHA256
from Crypto.PublicKey import ECC
from Crypto.Signature import DSS
from bitkey import fw_version
from bitkey.comms import WalletComms, ShellTransaction
from bitkey.fwup import Fwup, FwupParams
from bitkey.fwup_bundler import FwupBundler, FwupDeltaInfo, load_patch_signing_key
from bitkey.meson import MesonBuild
from bitkey.wallet import Wallet
from bitkey_proto import wallet_pb2
from invoke import task
from pathlib import Path

from .lib.paths import (BUILD_FW_DIR, BUILD_FWUP_BUNDLE_DIR)
from .memfault import released_versions, fetch_release

MIN_DELTA_VERSION = '1.0.44'


def check_exists(path: str):
    if not path:
        return None
    p = Path(path)
    if not p.exists():
        click.echo(click.style(
            f"'{p}' not found", fg='red'))
        return None
    return p


@task(default=True,
      help={
          "fwup-bundle": "",
          "binary": "",
          "signature": "",
          "start_sequence_id": "",
          "serial_port": "",
          "mode": "",
          "mcu": "",
          "product": "",
      })
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
        comms = WalletComms(
            ShellTransaction(port=serial_port))
        update = Fwup(bundle, bin, sig, start_sequence_id,
                      comms=comms, fwup_params=fwup_params, mode=mode, mcu_role=mcu_role)
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


@task(help={
    "binary": "Path to bootloader .signed.bin",
    "signature": "Path to bootloader .detached_signature",
    "metadata": "Path to bootloader .detached_metadata",
    "serial_port": "",
    "mcu": "",
    "product": "",
    "variant": "",
})
def bl_upgrade(c, binary=None, signature=None, metadata=None, serial_port=None, bl_size=(48*1024), mcu="efr32", product="w1", variant="a"):
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
        comms = WalletComms(
            ShellTransaction(port=serial_port))
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


@task(help={
    "product": "",
    "platform": "",
    "hardware_revision": "",
    "image_type": "",
    "version": "",
    "build_dir": "",
    "bundle_dir": "",
})
def bundle(
    c,
    product=None,
    platform="w1",
    hardware_revision=None,
    image_type=None,
    version=None,
    build_dir=None,
    bundle_dir=None
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
            mcu_platform = f"{platform}-{mcu_config.role}"
            build_dir_mcu = BUILD_FW_DIR.joinpath(mcu_platform)
            meson = MesonBuild(c, build_dir=build_dir_mcu)

            def f(file): return meson.find_file(file)

            # Collect application files
            files.extend([
                f(bundler.application_name_for_mcu(
                    "a", mcu_config) + ".signed.bin"),
                f(bundler.application_name_for_mcu(
                    "a", mcu_config) + ".detached_signature"),
                f(bundler.application_name_for_mcu(
                    "b", mcu_config) + ".signed.bin"),
                f(bundler.application_name_for_mcu(
                    "b", mcu_config) + ".detached_signature"),
            ])

            # Collect bootloader (if applicable for this MCU)
            if mcu_config.include_bootloader:
                files.extend([
                    f(bundler.bootloader_name_for_mcu(
                        mcu_config) + ".signed.bin"),
                    f(bundler.bootloader_name_for_mcu(
                        mcu_config) + ".detached_signature"),
                ])

        bundler.generate_full(bundle_dir, files, version)
    else:
        # W1: Use existing logic (UNCHANGED)
        build_dir = Path(
            build_dir) if build_dir else BUILD_FW_DIR.joinpath(platform)
        meson = MesonBuild(c, build_dir=build_dir)

        def f(file): return meson.find_file(file)

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
            files.extend([
                f(bundler.bootloader_name() + ".signed.bin"),
                f(bundler.bootloader_name() + ".detached_signature"),
            ])

        bundler.generate_full(bundle_dir, files, version,
                              include_bootloader=include_bootloader)


@task(help={
    "product": "",
    "hardware_revision": "",
    "image_type": "",
    "from_version": "",
    "to_version": "",
    "from_dir": "",
    "to_dir": "",
    "bundle_dir": "",
    "from_image_type": "",
})
def bundle_delta(c, product=None, hardware_revision=None, image_type=None,
                 from_version=None, to_version=None, from_dir=None, to_dir=None, bundle_dir=None, from_image_type=None):
    """Generate a FWUP delta bundle"""

    # All args are required.
    if not product or not hardware_revision \
            or not image_type or not from_version \
            or not to_version or not from_dir or not to_dir or not bundle_dir:
        click.echo("Invalid arguments.")
        return

    key_pem = load_patch_signing_key(image_type, from_version, product)

    bundler = FwupBundler(product, hardware_revision, image_type)
    bundler.generate_delta(FwupDeltaInfo(
        from_version, to_version, from_dir, to_dir, from_image_type), bundle_dir, key_pem)


@task(help={
    "to_version": "",
    "to_dir": "",
    "hw_revision": "",
    "revision": "",
    "bearer_token": "",
    "image_type": "",
    "dont_upload": "",
    "from_version": "Single from_version to generate patch for (use with --from-dir)",
    "from_dir": "Local directory with from_version files (use with --from-version)",
})
def delta_release_local(c, to_version=None, to_dir=None, hw_revision=None, revision=None, bearer_token=None, image_type='dev', dont_upload=False, from_version=None, from_dir=None):
    """Generate and upload delta releases using local files for to_version.

    Use this when you have signed images locally (e.g., from the firmware signer)
    but haven't uploaded a full bundle to Memfault yet.

    If --from-version and --from-dir are provided, generates a single patch from
    local files (for versions not on Memfault). Otherwise, iterates through all
    versions on Memfault.
    """

    if not to_version or not to_dir or not hw_revision or not revision or not bearer_token:
        raise click.UsageError(
            'Need: to_version, to_dir, hw_revision, revision, bearer_token')

    if (from_version is None) != (from_dir is None):
        raise click.UsageError(
            'Must provide both --from-version and --from-dir, or neither')

    os.environ['MEMFAULT_ORG_TOKEN'] = bearer_token

    memfault_hw_revision = f"{hw_revision}-prod" if image_type == 'prod' else hw_revision
    # Always 'Dev' - Memfault sw_type doesn't distinguish prod/dev, that's in hw_revision
    sw_types = ['Dev']

    output_dir = tempfile.TemporaryDirectory()
    click.echo(f"Will write patches to {output_dir.name}")
    click.echo(f"Using local to_version dir: {to_dir}")

    def generate_and_upload_patch(version, from_bundle):
        """Generate and upload a single delta patch."""
        bundler = FwupBundler('w1a', hw_revision, image_type)

        key_pem = load_patch_signing_key(image_type, version, 'w1a')
        delta_bundle = bundler.generate_delta(FwupDeltaInfo(
            version, to_version, from_bundle, to_dir), output_dir.name, key_pem)

        click.echo(f"Patch max size: {delta_bundle.max_size}")

        if delta_bundle.valid:
            if dont_upload:
                click.echo("Skipping upload")
            else:
                for sw_type in sw_types:
                    sh.memfault(
                        "--org-token", bearer_token,
                        "--org", "block-wallet",
                        "--project", "w1a",
                        "upload-ota-payload",
                        "--hardware-version", memfault_hw_revision,
                        "--software-type", sw_type,
                        "--delta-from", version,
                        "--delta-to", to_version,
                        "--revision", revision,
                        delta_bundle.zip_file
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
        for version in released_versions(c, quiet=True):
            if semver.compare(MIN_DELTA_VERSION, version) < 0 and \
                    semver.compare(version, to_version) < 0:
                versions.append(version)

        for version in versions:
            for sw_type in sw_types:
                fwup_bundle = fetch_release(
                    c, version, memfault_hw_revision, sw_type, output_dir.name)
                if not fwup_bundle:
                    click.echo(f"Skipping {version} - not found on Memfault")
                    continue
                click.echo(
                    f'Downloaded {memfault_hw_revision} {sw_type} {version}')
                generate_and_upload_patch(version, fwup_bundle)

    click.echo("Done")
    output_dir.cleanup()


@task(help={
    "from_binary": "Path to the source firmware .signed.bin",
    "patch_file": "Path to the .signed.patch file",
    "to_binary": "Path to the expected target firmware .signed.bin (optional, for comparison)",
    "image_type": "dev or prod (default: dev)",
    "from_version": "Source firmware version (e.g. 1.0.100) - used to select signing key",
})
def verify_delta(c, from_binary=None, patch_file=None, to_binary=None, image_type='dev', from_version=None):
    """Verify a delta patch by applying it and comparing to expected output.

    This tool:
    1. Verifies the patch signature
    2. Applies the patch to from_binary
    3. Compares result to to_binary (if provided)
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

    ECC_P256_SIG_SIZE = 64

    with open(patch_file, 'rb') as f:
        patch_with_sig = f.read()

    patch_data = patch_with_sig[:-ECC_P256_SIG_SIZE]
    patch_sig = patch_with_sig[-ECC_P256_SIG_SIZE:]

    click.echo(f"Patch file: {patch_file}")
    click.echo(
        f"Patch size: {len(patch_data)} bytes + {ECC_P256_SIG_SIZE} byte signature")

    if from_version:
        here = Path(__file__).parent.resolve()
        keys_dir = here / ".." / "config" / "keys"

        if from_version and semver.compare(from_version, "1.0.52") < 0:
            key_type = "dev"
        else:
            key_type = image_type

        pubkey_path = keys_dir / f"w1a-{key_type}" / \
            f"w1a-patch-signing-key-{key_type}.1.pub.pem"

        if pubkey_path.exists():
            with open(pubkey_path, 'r') as f:
                pubkey = ECC.import_key(f.read())

            digest = SHA256.new(patch_data)
            try:
                DSS.new(pubkey, 'deterministic-rfc6979').verify(digest, patch_sig)
                click.echo(click.style("✓ Patch signature VALID", fg='green'))
            except ValueError:
                click.echo(click.style("✗ Patch signature INVALID", fg='red'))
                return
        else:
            click.echo(
                f"Public key not found at {pubkey_path}, skipping signature verification")
    else:
        click.echo("No --from-version provided, skipping signature verification")

    click.echo(f"\nApplying patch to: {from_binary}")
    with open(from_binary, 'rb') as f:
        from_data = f.read()

    try:
        ffrom = io.BytesIO(from_data)
        fpatch = io.BytesIO(patch_data)
        fto = io.BytesIO()

        result_size = detools.apply_patch(ffrom, fpatch, fto)
        result_data = fto.getvalue()

        click.echo(f"Result size: {result_size} bytes")
        click.echo(click.style("✓ Patch applied successfully", fg='green'))
    except Exception as e:
        click.echo(click.style(f"✗ Patch application FAILED: {e}", fg='red'))
        return

    if to_binary:
        to_binary = Path(to_binary)
        if not to_binary.exists():
            click.echo(f"To binary not found: {to_binary}")
            return

        with open(to_binary, 'rb') as f:
            expected_data = f.read()

        if result_data == expected_data:
            click.echo(click.style(
                "✓ Result matches expected target binary", fg='green'))
        else:
            click.echo(click.style(
                "✗ Result does NOT match expected target binary", fg='red'))
            click.echo(
                f"  Expected size: {len(expected_data)}, Got: {len(result_data)}")

            for i, (a, b) in enumerate(zip(result_data, expected_data)):
                if a != b:
                    click.echo(
                        f"  First difference at offset {i}: expected {b:02x}, got {a:02x}")
                    break
            return

    click.echo("\n✓ Delta verification complete")


@task(help={
    "from_version": "Source firmware version (e.g. 1.0.100)",
    "to_version": "Target firmware version (e.g. 1.0.101)",
    "hw_revision": "Hardware revision (e.g. dvt or dvt-prod)",
    "bearer_token": "Memfault org token",
    "image_type": "dev or prod (default: dev)",
    "from_dir": "Local directory with from_version files (optional, uses Memfault if not provided)",
    "to_dir": "Local directory with to_version files (optional, uses Memfault if not provided)",
})
def verify_delta_release(c, from_version=None, to_version=None, hw_revision=None, bearer_token=None, image_type='dev', from_dir=None, to_dir=None):
    """Verify a delta transition by generating a patch and applying it.

    This downloads from_version from Memfault (and to_version if not using --to-dir),
    generates a delta patch, applies it, and verifies the result matches.
    """
    if not from_version or not to_version or not hw_revision or not bearer_token:
        raise click.UsageError(
            "Need: --from-version, --to-version, --hw-revision, --bearer-token")

    os.environ['MEMFAULT_ORG_TOKEN'] = bearer_token

    ECC_P256_SIG_SIZE = 64
    sw_type = 'Dev'

    output_dir = tempfile.TemporaryDirectory()

    memfault_hw_rev = hw_revision if '-' in hw_revision else (
        f"{hw_revision}-prod" if image_type == 'prod' else hw_revision)
    bundler_hw_rev = hw_revision.split('-')[0]

    click.echo(f"=== Verifying delta: {from_version} -> {to_version} ===")
    click.echo(
        f"Memfault hw_revision: {memfault_hw_rev}, file hw_rev: {bundler_hw_rev}, image_type: {image_type}\n")

    if from_dir:
        from_release = Path(from_dir)
        click.echo(f"Using local from_version dir: {from_dir}")
    else:
        from_release = fetch_release(
            c, from_version, memfault_hw_rev, sw_type, output_dir.name)
        if not from_release:
            click.echo(
                f"Could not fetch from_version {from_version} from Memfault (hw={memfault_hw_rev})")
            click.echo(
                f"Hint: Use --from-dir with local signed images")
            return
        click.echo(f"Downloaded {from_version}")

    if to_dir:
        to_release = Path(to_dir)
        click.echo(f"Using local to_version dir: {to_dir}")
    else:
        to_release = fetch_release(
            c, to_version, memfault_hw_rev, sw_type, output_dir.name)
        if not to_release:
            click.echo(
                f"Could not fetch to_version {to_version} from Memfault (hw={memfault_hw_rev})")
            click.echo(
                f"Hint: If only delta releases were uploaded, use --to-dir with local signed images")
            return
        click.echo(f"Downloaded {to_version}")

    key_pem = load_patch_signing_key(image_type, from_version, 'w1a')

    bundler = FwupBundler('w1a', bundler_hw_rev, image_type)
    delta_info = FwupDeltaInfo(
        from_version, to_version, from_release, to_release)

    click.echo(f"\nGenerating delta bundle...")
    delta_bundle = bundler.generate_delta(delta_info, output_dir.name, key_pem)
    click.echo(f"Patch max size: {delta_bundle.max_size} bytes")

    if not delta_bundle.valid:
        click.echo(click.style(
            f"✗ Patch too large ({delta_bundle.max_size} > 128KB)", fg='red'))
        return

    FW_SIGNATURE_OFFSET = 647104  # 632K - 64, content size that was signed
    FW_SIGNATURE_SIZE = 64
    FLASH_ERASED_VALUE = 0xff

    def verify_firmware_signature(fw_content, detached_sig, slot_name):
        """Verify the firmware image signature using detached signature."""
        here = Path(__file__).parent.resolve()
        keys_dir = here / ".." / "config" / "keys"

        if image_type == "prod":
            app_pubkey_path = keys_dir / "w1a-prod" / \
                "w1a-app-signing-key-prod-production.2.pub.pem"
        else:
            app_pubkey_path = keys_dir / "w1a-dev" / "w1a-app-signing-key-dev.2.pub.pem"

        if not app_pubkey_path.exists():
            click.echo(
                f"  (skipping fw sig verification - key not at {app_pubkey_path})")
            return True

        with open(app_pubkey_path, 'r') as f:
            app_pubkey = ECC.import_key(f.read())

        # Pad content to FW_SIGNATURE_OFFSET with 0xff (flash erased value)
        padded_content = fw_content + \
            bytes([FLASH_ERASED_VALUE] * (FW_SIGNATURE_OFFSET - len(fw_content)))

        digest = SHA256.new(padded_content)
        try:
            DSS.new(app_pubkey, 'deterministic-rfc6979').verify(digest, detached_sig)
            click.echo(click.style(
                f"  ✓ Firmware signature valid ({slot_name})", fg='green'))
            return True
        except ValueError as e:
            click.echo(click.style(
                f"  ✗ Firmware signature INVALID ({slot_name}): {e}", fg='red'))
            return False

    def verify_patch(patch: 'Patch', from_slot, to_slot):
        click.echo(f"\n--- Verifying {from_slot} -> {to_slot} patch ---")

        patch_file = patch.path
        with open(patch_file, 'rb') as f:
            patch_with_sig = f.read()

        patch_data = patch_with_sig[:-ECC_P256_SIG_SIZE]
        patch_sig = patch_with_sig[-ECC_P256_SIG_SIZE:]

        click.echo(
            f"Patch: {patch_file.name} ({len(patch_data)} bytes + {ECC_P256_SIG_SIZE} byte sig)")

        here = Path(__file__).parent.resolve()
        keys_dir = here / ".." / "config" / "keys"

        if semver.compare(from_version, "1.0.52") < 0:
            key_type = "dev"
        else:
            key_type = image_type

        pubkey_path = keys_dir / f"w1a-{key_type}" / \
            f"w1a-patch-signing-key-{key_type}.1.pub.pem"

        if pubkey_path.exists():
            with open(pubkey_path, 'r') as f:
                pubkey = ECC.import_key(f.read())

            digest = SHA256.new(patch_data)
            try:
                DSS.new(pubkey, 'deterministic-rfc6979').verify(digest, patch_sig)
                click.echo(click.style(
                    "  ✓ Patch signature valid", fg='green'))
            except ValueError:
                click.echo(click.style(
                    "  ✗ Patch signature INVALID", fg='red'))
                return False
        else:
            click.echo(
                f"  (skipping patch sig verification - key not at {pubkey_path})")

        from_bin_name = f"w1a-{bundler_hw_rev}-app-{from_slot}-{image_type}.signed.bin"
        to_bin_name = f"w1a-{bundler_hw_rev}-app-{to_slot}-{image_type}.signed.bin"

        from_bin = from_release / from_bin_name
        to_bin = to_release / to_bin_name

        if not from_bin.exists():
            click.echo(f"  From binary not found: {from_bin}")
            return False
        if not to_bin.exists():
            click.echo(f"  To binary not found: {to_bin}")
            return False

        with open(from_bin, 'rb') as f:
            from_data = f.read()
        with open(to_bin, 'rb') as f:
            expected_data = f.read()

        try:
            ffrom = io.BytesIO(from_data)
            fpatch = io.BytesIO(patch_data)
            fto = io.BytesIO()

            result_size = detools.apply_patch(ffrom, fpatch, fto)
            result_data = fto.getvalue()

            click.echo(click.style(
                "  ✓ Patch applied successfully", fg='green'))
        except Exception as e:
            click.echo(click.style(
                f"  ✗ Patch application FAILED: {e}", fg='red'))
            return False

        if result_data == expected_data:
            click.echo(click.style(
                "  ✓ Result matches expected target", fg='green'))
        else:
            click.echo(click.style(
                "  ✗ Result does NOT match expected target", fg='red'))
            click.echo(
                f"    Expected: {len(expected_data)} bytes, Got: {len(result_data)} bytes")
            for i, (a, b) in enumerate(zip(result_data, expected_data)):
                if a != b:
                    click.echo(
                        f"    First diff at offset {i}: expected {b:02x}, got {a:02x}")
                    break
            return False

        detached_sig_name = f"w1a-{bundler_hw_rev}-app-{to_slot}-{image_type}.detached_signature"
        detached_sig_path = to_release / detached_sig_name
        if not detached_sig_path.exists():
            click.echo(
                f"  (skipping fw sig - detached_signature not found: {detached_sig_name})")
            return True

        with open(detached_sig_path, 'rb') as f:
            detached_sig = f.read()

        if not verify_firmware_signature(result_data, detached_sig, f"patched app-{to_slot}"):
            return False

        return True

    success = True
    success = verify_patch(delta_bundle.a2b, 'a', 'b') and success
    success = verify_patch(delta_bundle.b2a, 'b', 'a') and success

    output_dir.cleanup()

    if success:
        click.echo(click.style(
            f"\n✓ Delta {from_version} -> {to_version} verified successfully", fg='green'))
    else:
        click.echo(click.style(f"\n✗ Delta verification FAILED", fg='red'))


@task(help={
    "to_version": "",
    "revision": "",
    "bearer_token": "",
    "image_type": "",
    "dont_upload": "",
})
def delta_release(c, to_version=None, revision=None, bearer_token=None, image_type='dev', dont_upload=False):
    """Generate and upload all possible delta releases"""

    if not to_version or not revision or not bearer_token:
        raise click.UsageError('Need: to_version, revision, bearer_token')

    os.environ['MEMFAULT_ORG_TOKEN'] = bearer_token

    versions = []
    for version in released_versions(c, quiet=True):
        if semver.compare(MIN_DELTA_VERSION, version) < 0 and \
                semver.compare(version, to_version) < 0:
            versions.append(version)

    hw_revisions = [
        'evt',
        'dvt'
    ]
    # Always 'Dev' - Memfault sw_type doesn't distinguish prod/dev, that's in hw_revision
    sw_types = ['Dev']

    if image_type == 'prod':
        for i, rev in enumerate(hw_revisions):
            hw_revisions[i] = rev + '-prod'

    to_version_dirs = {}

    output_dir = tempfile.TemporaryDirectory()

    click.echo(f"Will write patches to {output_dir.name}")

    # Download the full release for the version that we're generating delta releases for
    for hw_revision in hw_revisions:
        to_version_dirs[hw_revision] = {}
        for sw_type in sw_types:
            release = fetch_release(
                c, to_version, hw_revision, sw_type, output_dir.name)
            if release:
                to_version_dirs[hw_revision][sw_type] = release
                click.echo(f'Downloaded {hw_revision} {sw_type} {to_version}')

    click.echo("\nGenerating patches...\n")

    for version in versions:
        # Download each release, generate a patch, and upload it
        for hw_revision in hw_revisions:
            for sw_type in sw_types:
                fwup_bundle = fetch_release(
                    c, version, hw_revision, sw_type, output_dir.name)
                if not fwup_bundle:
                    click.echo("Continuing...")
                    continue
                click.echo(f'Downloaded {hw_revision} {sw_type} {version}')

                to_version_dir = to_version_dirs[hw_revision][sw_type]

                # This is gross. But, the split('-') here is because Memfault requires us to
                # upload prod vs dev as different hardware revisions, e.g. dvt-prod vs dvt.
                # But, our file naming scheme separates hw_revision and image_type into
                # different fields.
                # So here, we transform from the Memfault hw revision to our own.
                bundler = FwupBundler(
                    'w1a', hw_revision.split('-')[0], image_type)

                key_pem = load_patch_signing_key(image_type, version, 'w1a')
                delta_bundle = bundler.generate_delta(FwupDeltaInfo(
                    version, to_version, fwup_bundle, to_version_dir), output_dir.name, key_pem)

                click.echo(f"Patch max size: {delta_bundle.max_size}")

                # Upload the release.
                if delta_bundle.valid:
                    if dont_upload:
                        click.echo("Skipping upload")
                    else:
                        sh.memfault(
                            "--org-token", bearer_token,
                            "--org", "block-wallet",
                            "--project", "w1a",
                            "upload-ota-payload",
                            "--hardware-version", hw_revision,
                            "--software-type", sw_type,
                            "--delta-from", version,
                            "--delta-to", to_version,
                            "--revision", revision,
                            delta_bundle.zip_file
                        )
                        click.echo(
                            f"Uploaded {version} -> {to_version} {hw_revision} {sw_type}")
                else:
                    click.echo(f"Can't release {version} -- not valid")

    click.echo("Done")
    output_dir.cleanup()

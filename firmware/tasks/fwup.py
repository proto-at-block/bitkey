import click
import tempfile
import semver
import sh

from invoke import task
from pathlib import Path

from bitkey.fwup import Fwup
from bitkey.fwup_bundler import FwupBundler, FwupDeltaInfo, load_patch_signing_key
from bitkey.meson import MesonBuild

from .lib.paths import (BUILD_FW_DIR, BUILD_FWUP_BUNDLE_DIR)
from bitkey import fw_version
from bitkey.comms import WalletComms, ShellTransaction
from .memfault import released_versions, fetch_release


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
      })
def fwup(c, fwup_bundle=None, binary=None, signature=None, start_sequence_id=0, serial_port=None, mode="FWUP_MODE_NORMAL"):
    """Firmware update"""
    bundle = check_exists(fwup_bundle)
    bin = check_exists(binary)
    sig = check_exists(signature)

    if not bundle and not (bin and sig):
        click.echo("Invalid arguments. Need a bundle, or (binary and signature).")
        return

    if serial_port != None:
        comms = WalletComms(
            ShellTransaction(port=serial_port))
        update = Fwup(bundle, bin, sig, start_sequence_id,
                      comms=comms, mode=mode)
    else:
        update = Fwup(bundle, bin, sig, start_sequence_id, mode=mode)
    result = update.start()
    if not result:
        print("Failed to start")
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
    "binary": "",
    "signature": "",
    "metadata": "",
    "serial_port": "",
})
def bl_upgrade(c, binary=None, signature=None, metadata=None, serial_port=None, bl_size=(48*1024)):
    bin = check_exists(binary)
    sig = check_exists(signature)
    meta = check_exists(metadata)

    if not bin and not sig and not meta:
        click.echo("Need a binary and signature file and metadata")
        return

    if serial_port != None:
        comms = WalletComms(
            ShellTransaction(port=serial_port))
        update = Fwup(None, bin, sig, 0, comms=comms)
    else:
        update = Fwup(None, bin, sig, 0)
    result = update.start()
    if not result:
        print(result)
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
    "hardware_revision": "",
    "image_type": "",
    "version": "",
    "build_dir": "",
    "bundle_dir": "",
})
def bundle(c, product=None, hardware_revision=None, image_type=None, version=None, build_dir=None, bundle_dir=None):
    """Generate fwup bundle"""
    if not version:
        version = fw_version.get()

    build_dir = Path(build_dir) if build_dir else BUILD_FW_DIR
    bundle_dir = Path(bundle_dir) if bundle_dir else BUILD_FWUP_BUNDLE_DIR

    bundler = FwupBundler(product, hardware_revision, image_type)
    meson = MesonBuild(c, build_dir=build_dir)

    def f(file): return meson.find_file(file)

    files = [
        f(bundler.bootloader_name() + ".signed.bin"),
        f(bundler.bootloader_name() + ".detached_signature"),
        f(bundler.bootloader_name() + ".detached_metadata"),
        f(bundler.application_name("a") + ".signed.bin"),
        f(bundler.application_name("a") + ".detached_signature"),
        f(bundler.application_name("b") + ".signed.bin"),
        f(bundler.application_name("b") + ".detached_signature"),
    ]

    bundler.generate_full(bundle_dir, files, version)


@task(help={
    "product": "",
    "hardware_revision": "",
    "image_type": "",
    "from_version": "",
    "to_version": "",
    "from_dir": "",
    "to_dir": "",
    "bundle_dir": "",
})
def bundle_delta(c, product=None, hardware_revision=None, image_type=None,
                 from_version=None, to_version=None, from_dir=None, to_dir=None, bundle_dir=None):
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
        from_version, to_version, from_dir, to_dir), bundle_dir, key_pem)


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
        click.echo('Invalid arguments.')
        return

    min_version = '1.0.44'

    versions = []
    for version in released_versions(c, bearer_token, quiet=True):
        # Prune versions which are < min_version and >= to_version.
        if semver.compare(min_version, version) < 0 and \
                semver.compare(version, to_version) < 0:
            versions.append(version)

    hw_revisions = [
        'evt',
        'dvt'
    ]
    sw_types = [
        'Dev'
    ]

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
                c, to_version, hw_revision, sw_type, output_dir.name, bearer_token)
            if release:
                to_version_dirs[hw_revision][sw_type] = release
                click.echo(f'Downloaded {hw_revision} {sw_type} {to_version}')

    click.echo("\nGenerating patches...\n")

    for version in versions:
        # Download each release, generate a patch, and upload it
        for hw_revision in hw_revisions:
            for sw_type in sw_types:
                fwup_bundle = fetch_release(
                    c, version, hw_revision, sw_type, output_dir.name, bearer_token)
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

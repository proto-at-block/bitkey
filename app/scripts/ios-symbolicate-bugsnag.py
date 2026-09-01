#!/usr/bin/env python3
"""Symbolicate Bitkey iOS Bugsnag stack frames with a matching dSYM.

Given a Bugsnag binary UUID plus either a release archive or dSYM, this script
finds the matching dSYM and runs atos for the provided frame addresses.
"""

from __future__ import annotations

import argparse
import plistlib
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile
from pathlib import Path

HEX_ADDRESS_RE = re.compile(r"0x[0-9a-fA-F]+")
UUID_RE = re.compile(r"UUID:\s*([0-9A-Fa-f-]+)\s*\(([^)]+)\)\s*(.*)")


def normalize_uuid(value: str | None) -> str | None:
    if value is None:
        return None
    return value.strip().upper().replace("-", "")


def run(command: list[str]) -> str:
    try:
        return subprocess.check_output(command, stderr=subprocess.STDOUT, text=True)
    except FileNotFoundError as error:
        raise SystemExit(f"Missing required tool: {command[0]}") from error
    except subprocess.CalledProcessError as error:
        raise SystemExit(
            f"Command failed: {' '.join(command)}\n{error.output.strip()}"
        ) from error


def safe_member_path(destination: Path, member_name: str) -> Path:
    """Return a destination path for an archive member, rejecting path traversal."""
    target = destination / member_name
    resolved_destination = destination.resolve()
    resolved_target = target.resolve(strict=False)
    if not resolved_target.is_relative_to(resolved_destination):
        raise SystemExit(f"Archive member would extract outside destination: {member_name}")
    return target


def extract_tar_archive(archive: Path, destination: Path) -> None:
    with tarfile.open(archive) as tar:
        for member in tar.getmembers():
            target = safe_member_path(destination, member.name)
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            elif member.isfile():
                target.parent.mkdir(parents=True, exist_ok=True)
                source = tar.extractfile(member)
                if source is None:
                    raise SystemExit(f"Could not read tar member: {member.name}")
                with source, target.open("wb") as output:
                    shutil.copyfileobj(source, output)
            else:
                raise SystemExit(f"Unsupported tar member type: {member.name}")


def extract_zip_archive(archive: Path, destination: Path) -> None:
    with zipfile.ZipFile(archive) as zipped:
        for member in zipped.infolist():
            target = safe_member_path(destination, member.filename)
            if member.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with zipped.open(member) as source, target.open("wb") as output:
                    shutil.copyfileobj(source, output)


def extract_archive(archive: Path, work_dir: Path) -> Path:
    if not archive.exists():
        raise SystemExit(f"Archive not found: {archive}")

    destination = work_dir / "archive"
    destination.mkdir(parents=True, exist_ok=True)

    archive_name = archive.name.lower()
    if archive.is_dir():
        return archive
    if archive_name.endswith((".tar.gz", ".tgz", ".tar")):
        extract_tar_archive(archive, destination)
    elif archive_name.endswith((".zip", ".ipa")):
        extract_zip_archive(archive, destination)
    else:
        raise SystemExit(
            f"Unsupported archive type: {archive}. Use .xcarchive, .dSYM, .tar.gz, .zip, or .ipa."
        )
    return destination


def find_dsyms(root: Path) -> list[Path]:
    if root.name.endswith(".dSYM") and root.is_dir():
        return [root]
    return sorted(path for path in root.rglob("*.dSYM") if path.is_dir())


def dwarfdump_uuids(path: Path) -> list[tuple[str, str, str]]:
    output = run(["dwarfdump", "--uuid", str(path)])
    uuids: list[tuple[str, str, str]] = []
    for line in output.splitlines():
        match = UUID_RE.search(line)
        if match:
            uuid, arch, binary_path = match.groups()
            uuids.append((uuid.upper(), arch, binary_path))
    return uuids


def find_matching_dsym(dsyms: list[Path], uuid: str | None, arch: str) -> Path:
    if not dsyms:
        raise SystemExit("No .dSYM bundles found.")

    normalized_target = normalize_uuid(uuid)
    matches: list[Path] = []
    all_uuids: list[str] = []

    for dsym in dsyms:
        for dsym_uuid, dsym_arch, _ in dwarfdump_uuids(dsym):
            all_uuids.append(f"{dsym_uuid} ({dsym_arch}) {dsym}")
            if normalized_target is None:
                continue
            if normalize_uuid(dsym_uuid) == normalized_target and dsym_arch == arch:
                matches.append(dsym)

    if normalized_target is None:
        if len(dsyms) == 1:
            return dsyms[0]
        raise SystemExit(
            "Multiple dSYMs found; pass --uuid.\nAvailable UUIDs:\n" + "\n".join(all_uuids)
        )

    if not matches:
        raise SystemExit(
            f"No dSYM matched UUID {uuid} for arch {arch}.\nAvailable UUIDs:\n" +
            ("\n".join(all_uuids) or "<none>")
        )
    if len(matches) > 1:
        raise SystemExit("Multiple matching dSYMs found:\n" + "\n".join(str(m) for m in matches))
    return matches[0]


def find_dwarf_binary(dsym: Path, binary_name: str | None) -> Path:
    dwarf_dir = dsym / "Contents" / "Resources" / "DWARF"
    if not dwarf_dir.is_dir():
        raise SystemExit(f"dSYM is missing DWARF directory: {dsym}")

    candidates = sorted(path for path in dwarf_dir.iterdir() if path.is_file())
    if binary_name:
        named = [path for path in candidates if path.name == binary_name]
        if named:
            return named[0]
        raise SystemExit(
            f"No DWARF binary named {binary_name} found in {dwarf_dir}. "
            f"Found: {', '.join(p.name for p in candidates)}"
        )
    if len(candidates) == 1:
        return candidates[0]
    raise SystemExit(
        "Multiple DWARF binaries found; pass --binary-name.\n" +
        "\n".join(str(path) for path in candidates)
    )


def addresses_from_args(values: list[str], stack_file: Path | None) -> list[str]:
    addresses: list[str] = []
    for value in values:
        addresses.extend(HEX_ADDRESS_RE.findall(value))
    if stack_file:
        for line in stack_file.read_text().splitlines():
            line_addresses = HEX_ADDRESS_RE.findall(line)
            if not line_addresses:
                continue
            # Bugsnag stack lines commonly look like:
            #   Wallet:0 - 0x100aee480 (0x100aee10c + 884)
            # The first address is the frame PC to symbolicate; the parenthesized
            # address is the nearest function start and should not be emitted as a
            # second stack frame.
            if " - " in line and "(" in line and "+" in line:
                addresses.append(line_addresses[0])
            else:
                addresses.extend(line_addresses)
    # Preserve order; stacks may intentionally repeat frames.
    return addresses


def verify_ipa(ipa: Path, uuid: str | None, arch: str, work_dir: Path, binary_name: str) -> None:
    if uuid is None:
        raise SystemExit("--ipa verification requires --uuid from the Bugsnag event App tab.")
    extract_root = extract_archive(ipa, work_dir / "ipa")
    apps = sorted(extract_root.rglob("*.app"))
    if not apps:
        raise SystemExit(f"No .app bundle found in IPA: {ipa}")
    app = apps[0]
    binary = app / binary_name
    if not binary.exists():
        raise SystemExit(f"No binary named {binary_name} found in {app}")

    bundle_id = None
    version = None
    build = None
    info_plist = app / "Info.plist"
    if info_plist.exists():
        with info_plist.open("rb") as file:
            info = plistlib.load(file)
        bundle_id = info.get("CFBundleIdentifier")
        version = info.get("CFBundleShortVersionString")
        build = info.get("CFBundleVersion")

    print("IPA binary:")
    print(f"  app: {app}")
    print(f"  bundle id: {bundle_id or '<unknown>'}")
    print(f"  version: {version or '<unknown>'}")
    print(f"  build: {build or '<unknown>'}")

    target_uuid = normalize_uuid(uuid)
    arch_uuid_seen = False
    uuid_matched = False
    uuid_lines: list[str] = []
    for binary_uuid, binary_arch, _ in dwarfdump_uuids(binary):
        status = ""
        if binary_arch == arch:
            arch_uuid_seen = True
            if target_uuid is not None:
                uuid_matched = normalize_uuid(binary_uuid) == target_uuid
                status = " MATCH" if uuid_matched else " MISMATCH"
        uuid_lines.append(f"  uuid: {binary_uuid} ({binary_arch}){status}")

    for line in uuid_lines:
        print(line)

    if target_uuid is not None:
        if not arch_uuid_seen:
            raise SystemExit(f"IPA does not contain a {arch} binary UUID to compare with {uuid}.")
        if not uuid_matched:
            raise SystemExit(f"IPA binary UUID does not match requested UUID {uuid} for arch {arch}.")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Find a matching dSYM by UUID and symbolicate iOS Bugsnag frame addresses."
    )
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--archive", type=Path, help="Path to release archive (.xcarchive, .tar.gz, .zip).")
    source.add_argument("--dsym", type=Path, help="Path to an extracted .dSYM bundle.")
    parser.add_argument("--uuid", help="Bugsnag binary UUID for Wallet, dashes optional.")
    parser.add_argument("--arch", default="arm64", help="Architecture to match and pass to atos. Default: arm64.")
    parser.add_argument("--binary-name", default="Wallet", help="DWARF/app binary name. Default: Wallet.")
    parser.add_argument("--addresses", nargs="*", default=[], help="Frame addresses from Bugsnag, e.g. 0x100bf13dc.")
    parser.add_argument("--stack-file", type=Path, help="File containing a stack trace; all hex addresses are extracted.")
    parser.add_argument(
        "--load-address",
        help="Optional Bugsnag machoLoadAddress / binary image load address to pass to atos -l.",
    )
    parser.add_argument("--ipa", type=Path, help="Optional IPA to verify against --uuid.")
    parser.add_argument("--verify-only", action="store_true", help="Only verify UUIDs; do not run atos.")
    parser.add_argument("--keep-temp", action="store_true", help="Keep extracted temporary files and print their path.")
    args = parser.parse_args()

    temp_dir = Path(tempfile.mkdtemp(prefix="ios-symbolicate-bugsnag-"))
    try:
        if args.ipa:
            verify_ipa(args.ipa.expanduser(), args.uuid, args.arch, temp_dir, args.binary_name)

        dsym: Path | None = None
        if args.dsym:
            dsym = args.dsym.expanduser()
            if not dsym.exists():
                raise SystemExit(f"dSYM not found: {dsym}")
            # Validate requested UUID if provided.
            find_matching_dsym([dsym], args.uuid, args.arch)
        elif args.archive:
            archive = args.archive.expanduser()
            if not archive.exists():
                raise SystemExit(f"Archive not found: {archive}")
            extracted_root = extract_archive(archive, temp_dir)
            dsym = find_matching_dsym(find_dsyms(extracted_root), args.uuid, args.arch)

        if dsym:
            print("Matched dSYM:")
            print(f"  {dsym}")
            for dsym_uuid, dsym_arch, _ in dwarfdump_uuids(dsym):
                marker = " MATCH" if args.uuid and normalize_uuid(dsym_uuid) == normalize_uuid(args.uuid) else ""
                print(f"  uuid: {dsym_uuid} ({dsym_arch}){marker}")

        if args.verify_only:
            return 0

        addresses = addresses_from_args(args.addresses, args.stack_file)
        if not addresses:
            raise SystemExit("No addresses provided. Pass --addresses or --stack-file, or use --verify-only.")
        if dsym is None:
            raise SystemExit("Symbolication requires --archive or --dsym.")

        dwarf_binary = find_dwarf_binary(dsym, args.binary_name)
        atos_command = ["atos", "-o", str(dwarf_binary), "-arch", args.arch]
        if args.load_address:
            atos_command.extend(["-l", args.load_address])
        symbols = run([*atos_command, *addresses]).splitlines()
        print("\nSymbolicated stack:")
        if args.load_address:
            print(f"Using load address: {args.load_address}")
        for index, (address, symbol) in enumerate(zip(addresses, symbols)):
            print(f"{index:>2}  {address}  {symbol}")
        return 0
    finally:
        if args.keep_temp:
            print(f"\nKept temp dir: {temp_dir}")
        else:
            shutil.rmtree(temp_dir, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())

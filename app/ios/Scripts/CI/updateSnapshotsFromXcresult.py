#!/usr/bin/env python3
"""Update iOS snapshot references directly from xcresult failure metadata."""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from urllib.parse import unquote, urlparse

SNAPSHOT_MISMATCH_MARKER = "does not match reference"
NEW_REFERENCE_MARKER = "No reference was found on disk. Automatically recorded snapshot"
SNAPSHOT_REFERENCE_RELATIVE_DIRS = (
    Path("app/ui/features/public/snapshots/images"),
    Path("app/ios/Wallet/Tests/SnapshotTests/__Snapshots__"),
)

FILE_URL_PATTERN = re.compile(r'"(file://[^"]+)"')
ABSOLUTE_PATH_PATTERN = re.compile(r'"(/[^"]+)"')


def run_xcresulttool_tests(result_bundle: Path) -> dict:
    command = [
        "xcrun",
        "xcresulttool",
        "get",
        "test-results",
        "tests",
        "--path",
        str(result_bundle),
        "--compact",
    ]
    try:
        completed = subprocess.run(
            command,
            check=True,
            capture_output=True,
            text=True,
        )
    except FileNotFoundError as exc:
        raise RuntimeError("xcrun was not found. Xcode command line tools are required.") from exc
    except subprocess.CalledProcessError as exc:
        stderr = (exc.stderr or "").strip()
        stdout = (exc.stdout or "").strip()
        message = stderr if stderr else stdout
        raise RuntimeError(f"xcresulttool failed: {message}") from exc

    try:
        return json.loads(completed.stdout)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Failed to parse xcresulttool JSON output: {exc}") from exc


def decode_path(value: str) -> Path:
    if value.startswith("file://"):
        parsed = urlparse(value)
        return Path(unquote(parsed.path)).resolve()
    return Path(unquote(value)).resolve()


def extract_paths(failure_message: str) -> list[Path]:
    file_urls = FILE_URL_PATTERN.findall(failure_message)
    if file_urls:
        return [decode_path(url) for url in file_urls]

    absolute_paths = ABSOLUTE_PATH_PATTERN.findall(failure_message)
    return [decode_path(path) for path in absolute_paths]


def is_within(path: Path, directory: Path) -> bool:
    try:
        path.relative_to(directory)
        return True
    except ValueError:
        return False


def is_within_any(path: Path, directories: list[Path]) -> bool:
    return any(is_within(path, directory) for directory in directories)


def build_allowed_reference_roots(repo_root: Path) -> list[Path]:
    return [(repo_root / relative_dir).resolve() for relative_dir in SNAPSHOT_REFERENCE_RELATIVE_DIRS]


def build_allowed_failure_roots(result_bundle: Path, repo_root: Path) -> list[Path]:
    return [
        result_bundle.resolve(),
        result_bundle.parent.resolve(),
        Path(tempfile.gettempdir()).resolve(),
        repo_root.resolve(),
    ]


def walk_failure_messages(
    nodes: list[dict] | None,
    current_test: str | None = None,
):
    if not nodes:
        return

    for node in nodes:
        if not isinstance(node, dict):
            continue

        node_type = node.get("nodeType")
        node_name = node.get("name", "")
        next_test = current_test

        if node_type == "Test Case":
            next_test = node_name

        if node_type == "Failure Message":
            details = node.get("details") or node_name
            if isinstance(details, str):
                yield (next_test or "<unknown test>"), details

        children = node.get("children")
        if isinstance(children, list):
            yield from walk_failure_messages(children, next_test)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Update iOS snapshots directly from xcresult failure metadata."
    )
    parser.add_argument(
        "--result-bundle",
        required=True,
        help="Path to the .xcresult bundle.",
    )
    parser.add_argument(
        "--repo-root",
        default=".",
        help="Repository root used to validate snapshot output paths.",
    )
    parser.add_argument(
        "--changed-files-output",
        help="Optional file path to write changed snapshot file paths (one per line).",
    )
    args = parser.parse_args()

    result_bundle = Path(args.result_bundle).resolve()
    repo_root = Path(args.repo_root).resolve()
    allowed_reference_roots = build_allowed_reference_roots(repo_root)
    allowed_failure_roots = build_allowed_failure_roots(result_bundle, repo_root)

    if not result_bundle.exists():
        print(f"Result bundle not found: {result_bundle}", file=sys.stderr)
        return 1

    try:
        tests_payload = run_xcresulttool_tests(result_bundle)
    except RuntimeError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    test_nodes = tests_payload.get("testNodes")
    if not isinstance(test_nodes, list):
        print("xcresult payload missing `testNodes`.", file=sys.stderr)
        return 1

    changed_paths: set[Path] = set()
    mismatch_count = 0
    recorded_count = 0
    skipped_count = 0
    error_count = 0

    for test_name, failure_message in walk_failure_messages(test_nodes):
        if SNAPSHOT_MISMATCH_MARKER in failure_message:
            mismatch_count += 1
            paths = extract_paths(failure_message)
            if len(paths) < 2:
                print(
                    f"Could not extract reference and failure paths from failure message in {test_name}.",
                    file=sys.stderr,
                )
                skipped_count += 1
                error_count += 1
                continue

            reference_path = paths[0]
            failed_path = paths[1]

            if not is_within_any(reference_path, allowed_reference_roots):
                print(
                    f"Refusing to write outside allowed snapshot directories: {reference_path}",
                    file=sys.stderr,
                )
                skipped_count += 1
                error_count += 1
                continue

            if not failed_path.is_file():
                print(
                    f"Failed snapshot artifact not found for {test_name}: {failed_path}",
                    file=sys.stderr,
                )
                skipped_count += 1
                error_count += 1
                continue

            if not is_within_any(failed_path, allowed_failure_roots):
                print(
                    f"Refusing to read failed snapshot from unexpected location: {failed_path}",
                    file=sys.stderr,
                )
                skipped_count += 1
                error_count += 1
                continue

            reference_path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(failed_path, reference_path)
            changed_paths.add(reference_path)
            print(f"Updated snapshot: {reference_path}")
            continue

        if NEW_REFERENCE_MARKER in failure_message:
            paths = extract_paths(failure_message)
            if not paths:
                print(
                    f"Could not extract recorded snapshot path from failure message in {test_name}.",
                    file=sys.stderr,
                )
                skipped_count += 1
                error_count += 1
                continue

            reference_path = paths[0]
            if not is_within_any(reference_path, allowed_reference_roots):
                print(
                    f"Recorded snapshot path is outside allowed snapshot directories: {reference_path}",
                    file=sys.stderr,
                )
                skipped_count += 1
                error_count += 1
                continue

            if not reference_path.is_file():
                print(
                    f"Recorded snapshot path does not exist for {test_name}: {reference_path}",
                    file=sys.stderr,
                )
                skipped_count += 1
                error_count += 1
                continue

            changed_paths.add(reference_path)
            recorded_count += 1
            print(f"Recorded snapshot already on disk: {reference_path}")

    if args.changed_files_output:
        output_file = Path(args.changed_files_output).resolve()
        output_file.parent.mkdir(parents=True, exist_ok=True)
        output = "\n".join(sorted(str(path) for path in changed_paths))
        if output:
            output += "\n"
        output_file.write_text(output, encoding="utf-8")

    print(
        "Snapshot update summary: "
        f"{len(changed_paths)} files changed, "
        f"{mismatch_count} mismatches, "
        f"{recorded_count} newly recorded, "
        f"{skipped_count} skipped."
    )

    if error_count > 0:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

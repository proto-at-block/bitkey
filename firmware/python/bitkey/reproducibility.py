"""Generate checksums for the production firmware reproducibility contract."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
from typing import Sequence


_COMMON_SUFFIXES = (
    "bin",
    "elf",
    "ld",
    "metadata.bin",
    "nometa.elf",
    "signed.elf",
)
_SIGNED_UPDATE_SUFFIXES = (
    "detached_signature",
    "signed.bin",
)

_APPLICATION_SUFFIXES = _COMMON_SUFFIXES + _SIGNED_UPDATE_SUFFIXES
_EFR32_LOADER_SUFFIXES = _APPLICATION_SUFFIXES + ("detached_metadata",)
_STM32_LOADER_SUFFIXES = _COMMON_SUFFIXES

_IMAGE_ARTIFACTS = (
    (
        "w1/app/w1/application/w1a-dvt-app-a-prod",
        _APPLICATION_SUFFIXES,
    ),
    (
        "w1/app/w1/application/w1a-dvt-app-b-prod",
        _APPLICATION_SUFFIXES,
    ),
    (
        "w1/app/w1/loader/w1a-dvt-loader-prod",
        _EFR32_LOADER_SUFFIXES,
    ),
    (
        "w3-core/app/w3-core/application/w3a-core-pdvt-app-a-prod",
        _APPLICATION_SUFFIXES,
    ),
    (
        "w3-core/app/w3-core/application/w3a-core-pdvt-app-b-prod",
        _APPLICATION_SUFFIXES,
    ),
    (
        "w3-core/app/w3-core/loader/w3a-core-pdvt-loader-prod",
        _EFR32_LOADER_SUFFIXES,
    ),
    (
        "w3-uxc/app/w3-uxc/application/w3a-uxc-pdvt-app-a-prod",
        _APPLICATION_SUFFIXES,
    ),
    (
        "w3-uxc/app/w3-uxc/application/w3a-uxc-pdvt-app-b-prod",
        _APPLICATION_SUFFIXES,
    ),
    (
        "w3-uxc/app/w3-uxc/loader/w3a-uxc-pdvt-loader-prod",
        _STM32_LOADER_SUFFIXES,
    ),
)

EXPECTED_ARTIFACT_COUNT = 72


def expected_artifact_paths() -> tuple[Path, ...]:
    """Return the image artifacts covered by the reproducibility contract."""
    paths = tuple(
        sorted(
            (
                Path(f"{image}.{suffix}")
                for image, suffixes in _IMAGE_ARTIFACTS
                for suffix in suffixes
            ),
            key=lambda path: path.as_posix(),
        )
    )
    if len(paths) != EXPECTED_ARTIFACT_COUNT or len(set(paths)) != len(paths):
        raise AssertionError("invalid firmware reproducibility artifact specification")
    return paths


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as artifact:
        for chunk in iter(lambda: artifact.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def manifest(build_dir: Path) -> str:
    """Return a path-independent SHA-256 manifest for a firmware build."""
    build_dir = Path(build_dir)
    artifact_paths = expected_artifact_paths()
    missing = [path for path in artifact_paths if not (build_dir / path).is_file()]
    if missing:
        missing_list = "\n".join(f"  {path.as_posix()}" for path in missing)
        raise FileNotFoundError(
            f"missing {len(missing)} reproducibility artifact(s):\n{missing_list}"
        )

    return "".join(
        f"{_sha256(build_dir / path)}  {path.as_posix()}\n"
        for path in artifact_paths
    )


def write_manifest(build_dir: Path, output: Path) -> None:
    contents = manifest(build_dir)
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(contents, encoding="utf-8")


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Generate the production firmware reproducibility manifest."
    )
    parser.add_argument("--build-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args(argv)

    try:
        write_manifest(args.build_dir, args.output)
    except OSError as error:
        parser.error(str(error))

    print(
        f"Wrote {EXPECTED_ARTIFACT_COUNT} firmware artifact checksums to "
        f"{args.output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

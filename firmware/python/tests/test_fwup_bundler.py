"""Tests for FWUP bundler delta patch size validation."""

from pathlib import Path
from typing import Optional

from bitkey.fwup_bundler import DeltaBundle, Patch


def _bundle_with_patch_size(
    size: int,
    role: Optional[str],
    from_image_type: str = "dev",
) -> DeltaBundle:
    return DeltaBundle(
        a2b=Patch(
            path=Path("a2b.patch"),
            size=size,
            role=role,
            from_image_type=from_image_type,
        ),
        b2a=Patch(
            path=Path("b2a.patch"),
            size=1,
            role=role,
            from_image_type=from_image_type,
        ),
        zip_file=Path("bundle.zip"),
    )


def test_delta_bundle_allows_patch_at_uxc_limit():
    bundle = _bundle_with_patch_size(104 * 1024, "uxc")

    assert bundle.valid
    assert bundle.invalid_details == []


def test_delta_bundle_rejects_patch_above_uxc_limit():
    bundle = _bundle_with_patch_size((104 * 1024) + 1, "uxc")

    assert not bundle.valid
    assert bundle.invalid_details == ["uxc: a2b.patch=106497 (limit 106496)"]


def test_delta_bundle_allows_patch_at_core_limit():
    bundle = _bundle_with_patch_size(120 * 1024, "core")

    assert bundle.valid
    assert bundle.invalid_details == []


def test_delta_bundle_rejects_patch_above_core_limit():
    bundle = _bundle_with_patch_size((120 * 1024) + 1, "core")

    assert not bundle.valid
    assert bundle.invalid_details == ["core: a2b.patch=122881 (limit 122880)"]


def test_delta_bundle_allows_core_mfgtest_patch_at_limit():
    bundle = _bundle_with_patch_size(
        168 * 1024,
        "core",
        from_image_type="mfgtest-dev",
    )

    assert bundle.valid
    assert bundle.invalid_details == []


def test_delta_bundle_rejects_core_mfgtest_patch_above_limit():
    bundle = _bundle_with_patch_size(
        (168 * 1024) + 1,
        "core",
        from_image_type="mfgtest-dev",
    )

    assert not bundle.valid
    assert bundle.invalid_details == ["core: a2b.patch=172033 (limit 172032)"]


def test_delta_bundle_allows_single_mcu_mfgtest_patch_at_limit():
    bundle = _bundle_with_patch_size(
        168 * 1024,
        None,
        from_image_type="mfgtest-dev",
    )

    assert bundle.valid
    assert bundle.invalid_details == []


def test_delta_bundle_rejects_single_mcu_patch_above_limit():
    bundle = _bundle_with_patch_size((120 * 1024) + 1, None)

    assert not bundle.valid
    assert bundle.invalid_details == ["single: a2b.patch=122881 (limit 122880)"]

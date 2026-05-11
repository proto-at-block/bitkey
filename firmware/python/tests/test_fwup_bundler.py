"""Tests for FWUP bundler delta patch size validation."""

from pathlib import Path
from typing import Optional

import pytest

from bitkey.fwup_bundler import DeltaBundle, Patch, load_patch_signing_key


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


def _write_patch_key(base_dir: Path, key_prefix: str, key_type: str, content: str) -> None:
    (base_dir / f"{key_prefix}-patch-signing-key-{key_type}.1.priv.pem").write_text(content)


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


@pytest.mark.parametrize("image_type", ["mfgtest-dev", "mfgtest-foo"])
def test_load_patch_signing_key_maps_mfgtest_variants_to_dev_key(tmp_path: Path, image_type: str):
    key_dir = tmp_path / "keys"
    key_dir.mkdir()
    _write_patch_key(key_dir, "w3a-core", "dev", "dev-key-content")
    _write_patch_key(key_dir, "w3a-core", "prod", "prod-key-content")

    assert load_patch_signing_key(
        image_type=image_type,
        version="1.2.3",
        product="w3a",
        base_directory=str(key_dir),
    ) == "dev-key-content"


def test_load_patch_signing_key_uses_env_key_for_mfgtest_prod(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("DELTA_PATCH_SIGNING_KEY_PROD", "prod-env-key")

    assert load_patch_signing_key(
        image_type="mfgtest-prod",
        version="1.2.3",
        product="w3a",
    ) == "prod-env-key"


def test_load_patch_signing_key_uses_dev_key_for_w1a_pre_1_0_52(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    key_dir = tmp_path / "keys"
    key_dir.mkdir()
    _write_patch_key(key_dir, "w1a", "dev", "w1a-dev-key-content")
    _write_patch_key(key_dir, "w1a", "prod", "w1a-prod-key-content")
    monkeypatch.setenv("DELTA_PATCH_SIGNING_KEY_PROD", "env-prod-key")

    assert load_patch_signing_key(
        image_type="prod",
        version="1.0.51",
        product="w1a",
        base_directory=str(key_dir),
    ) == "w1a-dev-key-content"

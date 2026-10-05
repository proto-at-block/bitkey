import pytest

# The build entry point initializes Invoke tasks before importing bitkey.meson.
import tasks  # noqa: F401
from bitkey.meson import BuildVariant, Target


@pytest.mark.parametrize(
    ("target", "expected_variant"),
    (
        ("w1a-dev-board-rev-0-app-a-prod", BuildVariant.PROD),
        ("w1a-dev-board-rev-0-app-a-prod.signed.elf", BuildVariant.PROD),
        ("w1a-dev-board-rev-0-app-a-dev.signed.elf", BuildVariant.DEV),
        ("w3a-core-pdvt-app-a-mfgtest-dev.signed.elf", BuildVariant.DEV),
        ("w3a-core-pdvt-app-a-mfgtest-prod.signed.elf", BuildVariant.DEV),
    ),
)
def test_target_variant_uses_build_environment_suffix(target, expected_variant):
    assert Target(target).variant == expected_variant

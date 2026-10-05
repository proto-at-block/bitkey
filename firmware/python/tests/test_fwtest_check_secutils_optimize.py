import sys
from pathlib import Path

import pytest

SECUTILS_TESTS_DIR = (
    Path(__file__).parents[1] / "bitkey/fwa/bitkey_fwa/secutils_tests"
)
sys.path.insert(0, str(SECUTILS_TESTS_DIR))

import fwtest_check_secutils_optimize  # noqa: E402


def test_main_rejects_empty_elf_pair_list(monkeypatch: pytest.MonkeyPatch):
    def fail_if_called(_):
        pytest.fail("run_cscope should not be called without ELF pairs")

    monkeypatch.setattr(fwtest_check_secutils_optimize, "run_cscope", fail_if_called)
    monkeypatch.setattr(
        fwtest_check_secutils_optimize, "get_elf_pairs", lambda: []
    )

    with pytest.raises(
        RuntimeError,
        match="No matching application/loader ELF pairs found",
    ):
        fwtest_check_secutils_optimize.main([])

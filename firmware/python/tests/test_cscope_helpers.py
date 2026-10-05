import subprocess
import sys
from pathlib import Path

import pytest

SECUTILS_TESTS_DIR = (
    Path(__file__).parents[1] / "bitkey/fwa/bitkey_fwa/secutils_tests"
)
sys.path.insert(0, str(SECUTILS_TESTS_DIR))

import cscope_helpers  # noqa: E402


def test_get_cscope_sources_includes_generated_sources_and_skips_large_trees(
    tmp_path: Path,
):
    included_sources = [
        tmp_path / "app/main.c",
        tmp_path / "lib/ipc/generated/ipc_internal.c",
        tmp_path / "lib/secutils.h",
    ]
    excluded_sources = [
        tmp_path / ".hermit/package.c",
        tmp_path / "build/generated.c",
        tmp_path / "third-party/vendor.c",
    ]
    for source in included_sources + excluded_sources:
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text("")

    assert cscope_helpers._get_cscope_sources(str(tmp_path)) == [
        "app/main.c",
        "lib/ipc/generated/ipc_internal.c",
        "lib/secutils.h",
    ]


def test_run_cscope_indexes_selected_sources(monkeypatch: pytest.MonkeyPatch):
    invocation = {}

    monkeypatch.setattr(
        cscope_helpers,
        "_get_cscope_sources",
        lambda _: ["app/main.c", "lib/ipc/generated/ipc_internal.c"],
    )

    def fake_run(command, **kwargs):
        source_list_path = Path(command[-1])
        invocation["command"] = command[:-1] + [source_list_path.name]
        invocation["kwargs"] = kwargs
        invocation["sources"] = source_list_path.read_text(encoding="utf-8")
        invocation["source_list_path"] = source_list_path
        return subprocess.CompletedProcess(command, returncode=0, stdout="", stderr="")

    monkeypatch.setattr(cscope_helpers.subprocess, "run", fake_run)

    cscope_helpers.run_cscope("/firmware")

    assert invocation["command"] == [
        "cscope",
        "-bqk",
        "-I",
        ".",
        "-i",
        "cscope.files",
    ]
    assert invocation["kwargs"] == {
        "cwd": "/firmware",
        "capture_output": True,
        "text": True,
    }
    assert invocation["sources"] == (
        "app/main.c\nlib/ipc/generated/ipc_internal.c"
    )
    assert not invocation["source_list_path"].exists()


def test_run_cscope_reports_database_generation_failure(
    monkeypatch: pytest.MonkeyPatch,
):
    monkeypatch.setattr(
        cscope_helpers, "_get_cscope_sources", lambda _: ["app/main.c"]
    )

    def fake_run(command, **kwargs):
        return subprocess.CompletedProcess(
            command, returncode=1, stdout="", stderr="cannot write cscope.out"
        )

    monkeypatch.setattr(cscope_helpers.subprocess, "run", fake_run)

    with pytest.raises(
        RuntimeError,
        match="cscope database generation failed: cannot write cscope.out",
    ):
        cscope_helpers.run_cscope("/firmware")

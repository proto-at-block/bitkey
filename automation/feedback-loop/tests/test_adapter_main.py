"""Tests for the adapter entry point: provider/transport selection, exit codes, usage log."""

from __future__ import annotations

import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from adapters import common, llm_adapter  # noqa: E402
from feedback_loop.llm import strict_json_object  # noqa: E402
from tests.test_adapter_anthropic import sample_request  # noqa: E402


def run_main(argv: list[str], env: dict[str, str], request: dict | str) -> tuple[int, str, str]:
    stdin = io.StringIO(request if isinstance(request, str) else json.dumps(request))
    stdout = io.StringIO()
    stderr = io.StringIO()
    with mock.patch.dict(os.environ, env, clear=True):
        code = llm_adapter.main(argv, stdin=stdin, stdout=stdout, stderr=stderr)
    return code, stdout.getvalue(), stderr.getvalue()


def adapter_response() -> common.AdapterResponse:
    return common.AdapterResponse(
        payload={"ok": True},
        model="claude-opus-4-8",
        usage={"input_tokens": 10, "output_tokens": 5},
        http_attempts=1,
        stop_reason="end_turn",
    )


class TransportSelectionTest(unittest.TestCase):
    def test_key_present_uses_api(self) -> None:
        with mock.patch.object(
            llm_adapter.anthropic_provider, "complete_api", return_value=adapter_response()
        ) as api, mock.patch.object(
            llm_adapter.anthropic_provider, "complete_cli"
        ) as cli:
            code, stdout, _ = run_main(
                ["--provider", "claude"], {"ANTHROPIC_API_KEY": "k"}, sample_request()
            )
        self.assertEqual(code, 0)
        api.assert_called_once()
        cli.assert_not_called()
        self.assertEqual(strict_json_object(stdout), {"ok": True})

    def test_no_key_falls_back_to_cli(self) -> None:
        with mock.patch.object(
            llm_adapter.anthropic_provider, "complete_cli", return_value=adapter_response()
        ) as cli, mock.patch.object(
            llm_adapter.anthropic_provider, "complete_api"
        ) as api:
            code, _, _ = run_main(["--provider", "claude"], {}, sample_request())
        self.assertEqual(code, 0)
        cli.assert_called_once()
        api.assert_not_called()

    def test_force_cli_overrides_key(self) -> None:
        with mock.patch.object(
            llm_adapter.anthropic_provider, "complete_cli", return_value=adapter_response()
        ) as cli:
            code, _, _ = run_main(
                ["--provider", "claude"],
                {"ANTHROPIC_API_KEY": "k", "FEEDBACK_LOOP_ADAPTER_FORCE_CLI": "1"},
                sample_request(),
            )
        self.assertEqual(code, 0)
        cli.assert_called_once()

    def test_codex_provider_selected(self) -> None:
        with mock.patch.object(
            llm_adapter.openai_provider, "complete_api", return_value=adapter_response()
        ) as api:
            code, _, _ = run_main(
                ["--provider", "codex"], {"OPENAI_API_KEY": "k"}, sample_request()
            )
        self.assertEqual(code, 0)
        api.assert_called_once()

    def test_codex_without_key_fails_closed(self) -> None:
        with mock.patch.object(llm_adapter.openai_provider, "complete_api") as api:
            code, stdout, stderr = run_main(["--provider", "codex"], {}, sample_request())
        self.assertEqual(code, common.EXIT_TRANSPORT)
        self.assertEqual(stdout, "")
        self.assertIn("OPENAI_API_KEY is required", stderr)
        api.assert_not_called()
        self.assertFalse(hasattr(llm_adapter.openai_provider, "complete_cli"))

    def test_codex_force_cli_fails_closed(self) -> None:
        with mock.patch.object(llm_adapter.openai_provider, "complete_api") as api:
            code, stdout, stderr = run_main(
                ["--provider", "codex"],
                {"OPENAI_API_KEY": "k", "FEEDBACK_LOOP_ADAPTER_FORCE_CLI": "1"},
                sample_request(),
            )
        self.assertEqual(code, common.EXIT_TRANSPORT)
        self.assertEqual(stdout, "")
        self.assertIn("FEEDBACK_LOOP_ADAPTER_FORCE_CLI=1 is not supported", stderr)
        api.assert_not_called()

    def test_provider_env_default(self) -> None:
        with mock.patch.object(
            llm_adapter.openai_provider, "complete_api", return_value=adapter_response()
        ) as api:
            code, _, _ = run_main(
                [],
                {"OPENAI_API_KEY": "k", "FEEDBACK_LOOP_LLM_PROVIDER": "codex"},
                sample_request(),
            )
        self.assertEqual(code, 0)
        api.assert_called_once()


class ExitCodeTest(unittest.TestCase):
    def test_malformed_stdin_exits_2_with_empty_stdout(self) -> None:
        code, stdout, stderr = run_main(["--provider", "claude"], {}, "not json")
        self.assertEqual(code, common.EXIT_BAD_REQUEST)
        self.assertEqual(stdout, "")
        self.assertIn("invalid request JSON", stderr)

    def test_adapter_error_exits_1_with_empty_stdout(self) -> None:
        with mock.patch.object(
            llm_adapter.anthropic_provider,
            "complete_api",
            side_effect=common.AdapterError("provider exploded"),
        ):
            code, stdout, stderr = run_main(
                ["--provider", "claude"], {"ANTHROPIC_API_KEY": "k"}, sample_request()
            )
        self.assertEqual(code, common.EXIT_TRANSPORT)
        self.assertEqual(stdout, "")
        self.assertIn("provider exploded", stderr)

    def test_timeout_exits_124(self) -> None:
        with mock.patch.object(
            llm_adapter.anthropic_provider,
            "complete_api",
            side_effect=common.AdapterTimeout("deadline"),
        ):
            code, stdout, _ = run_main(
                ["--provider", "claude"], {"ANTHROPIC_API_KEY": "k"}, sample_request()
            )
        self.assertEqual(code, common.EXIT_TIMEOUT)
        self.assertEqual(stdout, "")


class UsageLogTest(unittest.TestCase):
    def test_usage_record_written_to_sidecar(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            log_path = os.path.join(tmp, "usage.jsonl")
            with mock.patch.object(
                llm_adapter.anthropic_provider, "complete_api", return_value=adapter_response()
            ):
                code, _, _ = run_main(
                    ["--provider", "claude"],
                    {
                        "ANTHROPIC_API_KEY": "k",
                        "FEEDBACK_LOOP_ADAPTER_USAGE_LOG": log_path,
                    },
                    sample_request(),
                )
            self.assertEqual(code, 0)
            lines = Path(log_path).read_text(encoding="utf-8").strip().splitlines()
        self.assertEqual(len(lines), 1)
        record = json.loads(lines[0])
        self.assertEqual(record["task"], "classify_feedback_signals")
        self.assertEqual(record["provider"], "claude")
        self.assertEqual(record["mode"], "api")
        self.assertEqual(record["model"], "claude-opus-4-8")
        self.assertEqual(record["input_tokens"], 10)
        self.assertIn("duration_ms", record)

    def test_usage_record_on_stderr_without_sidecar(self) -> None:
        with mock.patch.object(
            llm_adapter.anthropic_provider, "complete_api", return_value=adapter_response()
        ):
            code, _, stderr = run_main(
                ["--provider", "claude"], {"ANTHROPIC_API_KEY": "k"}, sample_request()
            )
        self.assertEqual(code, 0)
        self.assertIn("adapter-usage:", stderr)


class SubprocessSmokeTest(unittest.TestCase):
    """Prove the sys.path bootstrap works when invoked as a plain script."""

    def test_bad_request_via_real_subprocess(self) -> None:
        import subprocess

        script = os.path.join(
            os.path.dirname(__file__), "..", "adapters", "llm_adapter.py"
        )
        completed = subprocess.run(
            [sys.executable, script, "--provider", "claude"],
            input="not json",
            capture_output=True,
            text=True,
            timeout=30,
            env={**os.environ, "FEEDBACK_LOOP_ADAPTER_USAGE_LOG": ""},
        )
        self.assertEqual(completed.returncode, common.EXIT_BAD_REQUEST)
        self.assertEqual(completed.stdout, "")


class DocsTest(unittest.TestCase):
    def test_docs_do_not_advertise_codex_exec_fallback(self) -> None:
        root = Path(os.path.dirname(__file__)).parent
        forbidden = "codex " + "exec"
        docs = [
            root / "README.md",
            root / "trigger" / "PROVISIONING.md",
        ]
        for path in docs:
            with self.subTest(path=path.name):
                self.assertNotIn(forbidden, path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()

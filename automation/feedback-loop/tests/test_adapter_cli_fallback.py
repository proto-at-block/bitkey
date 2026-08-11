"""Tests for the Claude CLI-fallback path (`claude -p`)."""

from __future__ import annotations

import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from adapters import anthropic_provider, common  # noqa: E402
from tests.test_adapter_anthropic import sample_request  # noqa: E402


def clean_env(**extra: str) -> mock._patch_dict:
    return mock.patch.dict(os.environ, dict(extra), clear=True)


class ClaudeCliTest(unittest.TestCase):
    def test_json_extracted_from_prose(self) -> None:
        def runner(command, prompt, timeout):
            self.assertEqual(command[:2], ["claude", "-p"])
            self.assertIn("--model", command)
            self.assertIn("Response contract JSON:", prompt)
            return 0, 'Here you go:\n{"classifications": []}\nDone.', ""

        with clean_env():
            result = anthropic_provider.complete_cli(sample_request(), run=runner)
        self.assertEqual(result.payload, {"classifications": []})

    def test_model_flag_from_env(self) -> None:
        seen: list[list[str]] = []

        def runner(command, prompt, timeout):
            seen.append(command)
            return 0, "{}", ""

        with clean_env(FEEDBACK_LOOP_CLAUDE_MODEL="claude-opus-4-7"):
            anthropic_provider.complete_cli(sample_request(), run=runner)
        model_flag = seen[0].index("--model")
        self.assertEqual(seen[0][model_flag + 1], "claude-opus-4-7")

    def test_nonzero_exit_raises(self) -> None:
        def runner(command, prompt, timeout):
            return 1, "", "boom"

        with clean_env():
            with self.assertRaises(common.AdapterError) as caught:
                anthropic_provider.complete_cli(sample_request(), run=runner)
        self.assertNotIsInstance(caught.exception, common.AdapterTimeout)
        self.assertIn("boom", str(caught.exception))

    def test_no_json_raises(self) -> None:
        def runner(command, prompt, timeout):
            return 0, "no json at all", ""

        with clean_env():
            with self.assertRaises(common.AdapterError):
                anthropic_provider.complete_cli(sample_request(), run=runner)

    def test_timeout_propagates(self) -> None:
        def runner(command, prompt, timeout):
            raise common.AdapterTimeout("claude timed out")

        with clean_env():
            with self.assertRaises(common.AdapterTimeout):
                anthropic_provider.complete_cli(sample_request(), run=runner)


if __name__ == "__main__":
    unittest.main()

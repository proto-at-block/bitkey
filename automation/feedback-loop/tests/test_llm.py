"""Tests for the provider-neutral LLM client error classification and retry behavior."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.llm import (  # noqa: E402
    FakeLlmClient,
    LlmClientError,
    LlmRetryError,
    _subprocess_error_message,
    client_error_kind,
    complete_json_with_retry,
)


def _request() -> dict:
    return {
        "task": "demo",
        "prompt_version": "v1",
        "system_prompt": "x",
        "input": {},
        "response_contract": {"ok": "boolean"},
    }


class TestClientErrorKind(unittest.TestCase):
    def test_max_tokens_is_output_truncated(self):
        err = LlmClientError("adapter[claude/api]: model stopped with stop_reason=max_tokens")
        self.assertEqual(client_error_kind(err), "output_truncated")

    def test_invalid_json_is_schema_error(self):
        self.assertEqual(client_error_kind(LlmClientError("returned invalid JSON")), "schema_error")

    def test_generic_failure_is_transport_error(self):
        self.assertEqual(
            client_error_kind(LlmClientError("non-zero exit status 1")), "transport_error"
        )


class TestSubprocessErrorMessage(unittest.TestCase):
    def test_folds_stderr_into_message(self):
        class FakeCalledProcessError(Exception):
            stderr = "adapter[claude/api]: model stopped with stop_reason=max_tokens\n"

        message = _subprocess_error_message(FakeCalledProcessError("non-zero exit status 1"))
        self.assertIn("stop_reason=max_tokens", message)
        self.assertIn("non-zero exit status 1", message)

    def test_handles_missing_stderr(self):
        self.assertEqual(_subprocess_error_message(OSError("boom")), "boom")


class TestCompleteJsonWithRetry(unittest.TestCase):
    def test_output_truncated_is_not_retried(self):
        # Two responses queued, but an output_truncated failure must surface immediately without
        # consuming the second (retry) response — retrying identical input would overflow again.
        client = FakeLlmClient(
            [
                LlmClientError("model stopped with stop_reason=max_tokens"),
                {"ok": True},
            ]
        )
        with self.assertRaises(LlmRetryError) as ctx:
            complete_json_with_retry(
                client,
                _request(),
                parse=lambda response: response,
                format_retry_task="normalize",
                format_retry_system_prompt="x",
            )
        self.assertEqual(ctx.exception.error_kind, "output_truncated")
        self.assertEqual(ctx.exception.attempts, 1)
        self.assertFalse(ctx.exception.retry_attempted)
        self.assertEqual(len(client.requests), 1)

    def test_transport_error_is_retried_once(self):
        client = FakeLlmClient(
            [
                LlmClientError("non-zero exit status 1"),
                {"ok": True},
            ]
        )
        outcome = complete_json_with_retry(
            client,
            _request(),
            parse=lambda response: response,
            format_retry_task="normalize",
            format_retry_system_prompt="x",
        )
        self.assertEqual(outcome.value, {"ok": True})
        self.assertEqual(outcome.attempts, 2)
        self.assertTrue(outcome.retry_attempted)


if __name__ == "__main__":
    unittest.main()

"""Tests for the Codex (OpenAI Chat Completions API) adapter path."""

from __future__ import annotations

import json
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from adapters import common, openai_provider  # noqa: E402
from tests.test_adapter_anthropic import FakeHttp, sample_request  # noqa: E402


def chat_response(
    *,
    text: str = '{"classifications": []}',
    finish_reason: str = "stop",
    refusal: str | None = None,
) -> dict:
    return {
        "model": "gpt-5.4",
        "choices": [
            {
                "finish_reason": finish_reason,
                "message": {"role": "assistant", "content": text, "refusal": refusal},
            }
        ],
        "usage": {
            "prompt_tokens": 120,
            "completion_tokens": 40,
            "prompt_tokens_details": {"cached_tokens": 64},
        },
    }


def clean_env(**extra: str) -> mock._patch_dict:
    return mock.patch.dict(os.environ, {"OPENAI_API_KEY": "test-key", **extra}, clear=True)


class RequestBodyTest(unittest.TestCase):
    def request_body(self, fake: FakeHttp) -> dict:
        return json.loads(fake.calls[0][2].decode("utf-8"))

    def test_body_shape(self) -> None:
        fake = FakeHttp([(200, {}, chat_response())])
        with clean_env():
            openai_provider.complete_api(sample_request(), http=fake)
        body = self.request_body(fake)
        self.assertEqual(body["model"], "gpt-5.4")
        self.assertEqual(body["response_format"], {"type": "json_object"})
        self.assertEqual(body["max_completion_tokens"], common.DEFAULT_MAX_TOKENS)
        self.assertNotIn("max_tokens", body)
        for forbidden in ("temperature", "top_p"):
            self.assertNotIn(forbidden, body)
        self.assertEqual(body["messages"][0]["role"], "system")
        self.assertIn("You classify feedback signals.", body["messages"][0]["content"])
        self.assertEqual(body["messages"][1]["role"], "user")
        url, headers, _, _ = fake.calls[0]
        self.assertEqual(url, "https://api.openai.com/v1/chat/completions")
        self.assertEqual(headers["authorization"], "Bearer test-key")

    def test_model_env_precedence(self) -> None:
        fake = FakeHttp([(200, {}, chat_response())])
        with clean_env(
            FEEDBACK_LOOP_OPENAI_MODEL="gpt-5.4-mini",
            FEEDBACK_LOOP_OPENAI_MODEL_CLASSIFY_FEEDBACK_SIGNALS="gpt-5.4-nano",
        ):
            openai_provider.complete_api(sample_request(), http=fake)
        self.assertEqual(self.request_body(fake)["model"], "gpt-5.4-nano")

    def test_base_url_override(self) -> None:
        fake = FakeHttp([(200, {}, chat_response())])
        with clean_env(FEEDBACK_LOOP_ADAPTER_OPENAI_BASE_URL="https://gateway.internal/oai"):
            openai_provider.complete_api(sample_request(), http=fake)
        self.assertEqual(fake.calls[0][0], "https://gateway.internal/oai/v1/chat/completions")


class ResponseParseTest(unittest.TestCase):
    def test_payload_and_usage(self) -> None:
        fake = FakeHttp([(200, {}, chat_response())])
        with clean_env():
            result = openai_provider.complete_api(sample_request(), http=fake)
        self.assertEqual(result.payload, {"classifications": []})
        self.assertEqual(result.usage["input_tokens"], 120)
        self.assertEqual(result.usage["output_tokens"], 40)
        self.assertEqual(result.usage["cache_read_input_tokens"], 64)
        self.assertIsNone(result.usage["cache_creation_input_tokens"])
        self.assertEqual(result.stop_reason, "stop")

    def test_finish_reason_length_errors(self) -> None:
        fake = FakeHttp([(200, {}, chat_response(finish_reason="length"))])
        with clean_env():
            with self.assertRaises(common.AdapterError):
                openai_provider.complete_api(sample_request(), http=fake)

    def test_refusal_errors(self) -> None:
        fake = FakeHttp([(200, {}, chat_response(refusal="cannot help with that"))])
        with clean_env():
            with self.assertRaises(common.AdapterError):
                openai_provider.complete_api(sample_request(), http=fake)

    def test_empty_choices_errors(self) -> None:
        fake = FakeHttp([(200, {}, {"choices": []})])
        with clean_env():
            with self.assertRaises(common.AdapterError):
                openai_provider.complete_api(sample_request(), http=fake)


if __name__ == "__main__":
    unittest.main()

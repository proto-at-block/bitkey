"""Codex provider: direct OpenAI Chat Completions API only."""

from __future__ import annotations

import os
from typing import Any

from . import common

PROVIDER = "openai"
KEY_ENV = "OPENAI_API_KEY"
SUPPORTS_CLI = False
BASE_URL_ENV = "FEEDBACK_LOOP_ADAPTER_OPENAI_BASE_URL"
DEFAULT_BASE_URL = "https://api.openai.com"
# Matches the repo's existing Codex usage (.github/workflows/codex-security-review.yml).
DEFAULT_MODEL = "gpt-5.4"


def complete_api(
    request: dict[str, Any],
    *,
    http: common.HttpCallable | None = None,
) -> common.AdapterResponse:
    """One Chat Completions call in JSON mode.

    Stable-prefix-first assembly (system before user, byte-stable serialization) lets OpenAI's
    automatic prefix caching apply; reasoning models reject `max_tokens`, so the cap is sent as
    `max_completion_tokens`.
    """
    model = common.resolve_model(PROVIDER, str(request.get("task", "")), DEFAULT_MODEL)
    body = {
        "model": model,
        "max_completion_tokens": common.max_tokens(),
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": common.system_text(request)},
            {"role": "user", "content": common.user_text(request)},
        ],
    }
    headers = {
        "content-type": "application/json",
        "authorization": f"Bearer {os.environ.get(KEY_ENV, '')}",
    }
    base_url = os.environ.get(BASE_URL_ENV, "").strip() or DEFAULT_BASE_URL
    response, attempts = common.post_json_with_retry(
        f"{base_url.rstrip('/')}/v1/chat/completions", headers, body, http=http
    )

    choices = response.get("choices")
    if not isinstance(choices, list) or not choices:
        raise common.AdapterError("Chat Completions response has no choices")
    choice = choices[0] if isinstance(choices[0], dict) else {}
    finish_reason = choice.get("finish_reason")
    if finish_reason == "length":
        raise common.AdapterError("model output truncated (finish_reason=length)")
    message = choice.get("message") if isinstance(choice.get("message"), dict) else {}
    if message.get("refusal"):
        raise common.AdapterError(f"model refused: {common.truncate(str(message['refusal']), 300)}")

    text = str(message.get("content") or "")
    try:
        payload = common.first_json_object(text)
    except ValueError as err:
        raise common.AdapterError(
            f"{err}; text excerpt: {common.truncate(text, 500)}"
        ) from err

    usage = response.get("usage") if isinstance(response.get("usage"), dict) else {}
    prompt_details = (
        usage.get("prompt_tokens_details")
        if isinstance(usage.get("prompt_tokens_details"), dict)
        else {}
    )
    return common.AdapterResponse(
        payload=payload,
        model=str(response.get("model", model)),
        usage={
            "input_tokens": usage.get("prompt_tokens"),
            "output_tokens": usage.get("completion_tokens"),
            "cache_creation_input_tokens": None,
            "cache_read_input_tokens": prompt_details.get("cached_tokens"),
        },
        http_attempts=attempts,
        stop_reason=str(finish_reason) if finish_reason is not None else None,
    )

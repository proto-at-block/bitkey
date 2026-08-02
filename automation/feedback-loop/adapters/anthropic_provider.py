"""Claude provider: direct Anthropic Messages API with `claude -p` CLI fallback."""

from __future__ import annotations

import os
from typing import Any

from . import common

PROVIDER = "claude"
KEY_ENV = "ANTHROPIC_API_KEY"
SUPPORTS_CLI = True
BASE_URL_ENV = "FEEDBACK_LOOP_ADAPTER_ANTHROPIC_BASE_URL"
DEFAULT_BASE_URL = "https://api.anthropic.com"
DEFAULT_MODEL = "claude-opus-4-8"
API_VERSION = "2023-06-01"

# Stop reasons that mean the text cannot be trusted as a complete JSON answer.
_FAILED_STOP_REASONS = frozenset({"max_tokens", "refusal"})


def complete_api(
    request: dict[str, Any],
    *,
    http: common.HttpCallable | None = None,
) -> common.AdapterResponse:
    """One Messages API call. The stage-constant prefix (system prompt + contract) sits in a
    single cache_control system block so every call of the same task reads it from cache."""
    model = common.resolve_model(PROVIDER, str(request.get("task", "")), DEFAULT_MODEL)
    body = {
        "model": model,
        "max_tokens": common.max_tokens(),
        "thinking": {"type": "adaptive"},
        "system": [
            {
                "type": "text",
                "text": common.system_text(request),
                "cache_control": {"type": "ephemeral"},
            }
        ],
        "messages": [{"role": "user", "content": common.user_text(request)}],
    }
    headers = {
        "content-type": "application/json",
        "x-api-key": os.environ.get(KEY_ENV, ""),
        "anthropic-version": API_VERSION,
    }
    base_url = os.environ.get(BASE_URL_ENV, "").strip() or DEFAULT_BASE_URL
    response, attempts = common.post_json_with_retry(
        f"{base_url.rstrip('/')}/v1/messages", headers, body, http=http
    )

    stop_reason = response.get("stop_reason")
    if stop_reason in _FAILED_STOP_REASONS:
        raise common.AdapterError(f"model stopped with stop_reason={stop_reason}")

    content = response.get("content")
    if not isinstance(content, list):
        raise common.AdapterError("Messages API response has no content list")
    text = "".join(
        str(block.get("text", ""))
        for block in content
        if isinstance(block, dict) and block.get("type") == "text"
    )
    try:
        payload = common.first_json_object(text)
    except ValueError as err:
        raise common.AdapterError(
            f"{err}; text excerpt: {common.truncate(text, 500)}"
        ) from err

    usage = response.get("usage") if isinstance(response.get("usage"), dict) else {}
    return common.AdapterResponse(
        payload=payload,
        model=str(response.get("model", model)),
        usage={
            "input_tokens": usage.get("input_tokens"),
            "output_tokens": usage.get("output_tokens"),
            "cache_creation_input_tokens": usage.get("cache_creation_input_tokens"),
            "cache_read_input_tokens": usage.get("cache_read_input_tokens"),
        },
        http_attempts=attempts,
        stop_reason=str(stop_reason) if stop_reason is not None else None,
    )


def complete_cli(
    request: dict[str, Any],
    *,
    run: common.CliRunner | None = None,
) -> common.AdapterResponse:
    """Fallback for environments with an authenticated `claude` CLI but no API key."""
    runner = run or common.run_cli
    model = common.resolve_model(PROVIDER, str(request.get("task", "")), DEFAULT_MODEL)
    returncode, stdout, stderr = runner(
        ["claude", "-p", "--model", model],
        common.build_cli_prompt(request),
        common.cli_timeout_seconds(),
    )
    if returncode != 0:
        raise common.AdapterError(
            f"claude CLI exited {returncode}; stderr: {common.truncate(stderr, 500)}"
        )
    try:
        payload = common.first_json_object(stdout)
    except ValueError as err:
        raise common.AdapterError(
            f"{err}; stdout excerpt: {common.truncate(stdout, 500)}"
        ) from err
    return common.AdapterResponse(payload=payload, model=model)

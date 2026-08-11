"""Provider-neutral JSON LLM client for feedback-loop learning.

The feedback-loop core does not import provider SDKs. A configured subprocess receives one JSON
request on stdin and must return one JSON object on stdout.
"""

from __future__ import annotations

from dataclasses import dataclass
import json
import os
import shlex
import subprocess
import threading
from typing import Any, Callable, Protocol, TypeVar

LLM_COMMAND_ENV = "FEEDBACK_LOOP_LLM_COMMAND"
LLM_TIMEOUT_ENV = "FEEDBACK_LOOP_LLM_TIMEOUT"
DEFAULT_TIMEOUT_SECONDS = 300

T = TypeVar("T")


class LlmClientError(RuntimeError):
    """Raised when an LLM adapter cannot return a strict JSON object."""


class LlmRetryError(LlmClientError):
    """A retried LLM call still failed; carries retry metadata for stage artifacts."""

    def __init__(
        self,
        *,
        error_kind: str,
        message: str,
        attempts: int,
        retry_attempted: bool,
    ) -> None:
        super().__init__(message)
        self.error_kind = error_kind
        self.attempts = attempts
        self.retry_attempted = retry_attempted


class LlmClient(Protocol):
    """Provider-neutral interface used by the LLM learning pipeline."""

    def complete_json(self, request: dict[str, Any]) -> dict[str, Any]:
        """Return a strict JSON object for one prompt request."""


@dataclass(frozen=True)
class SubprocessJsonLlmClient:
    """JSON-over-stdin/stdout adapter configured by `FEEDBACK_LOOP_LLM_COMMAND`."""

    command: tuple[str, ...]
    timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS

    @classmethod
    def from_env(cls) -> "SubprocessJsonLlmClient | None":
        raw_command = os.environ.get(LLM_COMMAND_ENV, "").strip()
        if not raw_command:
            return None
        command = tuple(shlex.split(raw_command))
        if not command:
            return None
        return cls(command=command, timeout_seconds=_timeout_from_env())

    def complete_json(self, request: dict[str, Any]) -> dict[str, Any]:
        try:
            completed = subprocess.run(
                self.command,
                input=json.dumps(request, sort_keys=True),
                check=True,
                capture_output=True,
                text=True,
                timeout=self.timeout_seconds,
            )
        except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as err:
            raise LlmClientError(_subprocess_error_message(err)) from err

        return strict_json_object(completed.stdout)


class ThrottledLlmClient:
    """Caps in-flight `complete_json` calls across nested stage fan-outs.

    One shared semaphore means evaluator routes × replay cases can never exceed the configured
    concurrency in actual adapter subprocesses, regardless of pool shapes.
    """

    def __init__(self, inner: LlmClient, max_concurrent: int):
        self._inner = inner
        self._semaphore = threading.BoundedSemaphore(max(1, max_concurrent))

    def complete_json(self, request: dict[str, Any]) -> dict[str, Any]:
        with self._semaphore:
            return self._inner.complete_json(request)


class FakeLlmClient:
    """Small deterministic fake for tests.

    Responses may be dicts, raw JSON strings, or Exceptions. Each `complete_json` call consumes
    the next response. Concurrent tests pass `responder` instead — a callable keyed off the
    request — because ordered pops are meaningless when call order is nondeterministic.
    """

    def __init__(
        self,
        responses: list[dict[str, Any] | str | Exception] | None = None,
        *,
        responder: Callable[[dict[str, Any]], dict[str, Any] | str | Exception] | None = None,
    ):
        self._responses = list(responses or [])
        self._responder = responder
        self._lock = threading.Lock()
        self.requests: list[dict[str, Any]] = []

    def complete_json(self, request: dict[str, Any]) -> dict[str, Any]:
        with self._lock:
            self.requests.append(request)
            if self._responder is not None:
                response = self._responder(request)
            else:
                if not self._responses:
                    raise LlmClientError("fake LLM client has no response queued")
                response = self._responses.pop(0)
        if isinstance(response, Exception):
            raise response
        if isinstance(response, str):
            return strict_json_object(response)
        return response


@dataclass(frozen=True)
class LlmRetryOutcome:
    """Parsed value plus the attempts spent obtaining it."""

    value: Any
    attempts: int
    retry_attempted: bool


def _subprocess_error_message(err: Exception) -> str:
    """Fold any captured adapter stderr into the error message.

    `SubprocessJsonLlmClient` runs the adapter with `capture_output=True`, so the adapter's
    `adapter[provider/mode]: ...` stderr line (which carries the real failure reason, e.g.
    `model stopped with stop_reason=max_tokens`) is otherwise discarded. Surfacing it lets
    `client_error_kind` classify provider-side failures instead of seeing only an opaque
    "non-zero exit status" message.
    """
    stderr = getattr(err, "stderr", None)
    if isinstance(stderr, (bytes, bytearray)):
        stderr = stderr.decode("utf-8", "replace")
    detail = str(err)
    if stderr and stderr.strip():
        return f"{detail}: {stderr.strip()}"
    return detail


def client_error_kind(err: LlmClientError) -> str:
    """Classify an adapter failure as output_truncated, schema_error, or transport_error.

    `output_truncated` (the model hit its output-token cap, `stop_reason=max_tokens`) is a
    distinct kind: retrying the identical request is futile because the same input produces the
    same overflow. Callers that can shrink the request (e.g. the clusterer's adaptive bisection)
    handle it specially instead of burning a transport retry.
    """
    message = str(err).casefold()
    if "stop_reason=max_tokens" in message:
        return "output_truncated"
    if (
        "invalid json" in message
        or "must return a json object" in message
        or "json object" in message
    ) and "empty output" not in message:
        return "schema_error"
    return "transport_error"


def complete_json_with_retry(
    client: LlmClient,
    request: dict[str, Any],
    *,
    parse: Callable[[dict[str, Any]], T],
    format_retry_task: str,
    format_retry_system_prompt: str,
) -> LlmRetryOutcome:
    """Call the client with one transport retry and one format-normalization retry.

    Transport failures (adapter crash/timeout/empty output) retry the same request once. Schema
    failures (invalid JSON from the adapter, or `parse` raising ValueError/TypeError on a JSON
    response) get one normalization call that asks the model to reshape the malformed payload.
    Raises LlmRetryError with retry metadata when the budget is exhausted.
    """
    attempts = 0
    retry_attempted = False
    response: dict[str, Any] | None = None

    try:
        attempts += 1
        response = client.complete_json(request)
    except LlmClientError as err:
        kind = client_error_kind(err)
        if kind == "output_truncated":
            # Retrying the same request would overflow identically; surface it so a caller that
            # can shrink the request (clusterer bisection) handles it, and others degrade.
            raise LlmRetryError(
                error_kind="output_truncated",
                message=str(err),
                attempts=attempts,
                retry_attempted=False,
            ) from err
        if kind == "transport_error":
            retry_attempted = True
            try:
                attempts += 1
                response = client.complete_json(request)
            except LlmClientError as retry_err:
                if client_error_kind(retry_err) == "transport_error":
                    raise LlmRetryError(
                        error_kind="transport_error",
                        message=str(retry_err),
                        attempts=attempts,
                        retry_attempted=retry_attempted,
                    ) from retry_err
                return _format_retry(
                    client,
                    request,
                    parse=parse,
                    format_retry_task=format_retry_task,
                    format_retry_system_prompt=format_retry_system_prompt,
                    malformed_payload=None,
                    error_summary=str(retry_err),
                    attempts=attempts,
                )
        else:
            return _format_retry(
                client,
                request,
                parse=parse,
                format_retry_task=format_retry_task,
                format_retry_system_prompt=format_retry_system_prompt,
                malformed_payload=None,
                error_summary=str(err),
                attempts=attempts,
            )

    try:
        value = parse(response)
    except (ValueError, TypeError) as err:
        return _format_retry(
            client,
            request,
            parse=parse,
            format_retry_task=format_retry_task,
            format_retry_system_prompt=format_retry_system_prompt,
            malformed_payload=response,
            error_summary=str(err),
            attempts=attempts,
        )
    return LlmRetryOutcome(value=value, attempts=attempts, retry_attempted=retry_attempted)


def _format_retry(
    client: LlmClient,
    request: dict[str, Any],
    *,
    parse: Callable[[dict[str, Any]], T],
    format_retry_task: str,
    format_retry_system_prompt: str,
    malformed_payload: dict[str, Any] | None,
    error_summary: str,
    attempts: int,
) -> LlmRetryOutcome:
    retry_request = {
        "task": format_retry_task,
        "prompt_version": request.get("prompt_version"),
        "system_prompt": format_retry_system_prompt,
        "input": {
            "original_task": request.get("task"),
            "original_input": request.get("input"),
            "malformed_response": malformed_payload,
            "error_summary": error_summary,
        },
        "response_contract": request.get("response_contract"),
    }
    try:
        retry_response = client.complete_json(retry_request)
    except LlmClientError as err:
        raise LlmRetryError(
            error_kind=client_error_kind(err),
            message=str(err),
            attempts=attempts + 1,
            retry_attempted=True,
        ) from err

    try:
        value = parse(retry_response)
    except (ValueError, TypeError) as err:
        raise LlmRetryError(
            error_kind="schema_error",
            message=str(err),
            attempts=attempts + 1,
            retry_attempted=True,
        ) from err
    return LlmRetryOutcome(value=value, attempts=attempts + 1, retry_attempted=True)


def _timeout_from_env() -> int:
    raw = os.environ.get(LLM_TIMEOUT_ENV, "").strip()
    if not raw:
        return DEFAULT_TIMEOUT_SECONDS
    try:
        timeout = int(raw)
    except ValueError:
        return DEFAULT_TIMEOUT_SECONDS
    return timeout if timeout > 0 else DEFAULT_TIMEOUT_SECONDS


def strict_json_object(raw: str) -> dict[str, Any]:
    """Parse a strict JSON object with no surrounding prose."""
    text = raw.strip()
    if not text:
        raise LlmClientError("LLM adapter returned empty output")
    try:
        payload = json.loads(text)
    except json.JSONDecodeError as err:
        raise LlmClientError(f"LLM adapter returned invalid JSON: {err}") from err
    if not isinstance(payload, dict):
        raise LlmClientError("LLM adapter must return a JSON object")
    return payload

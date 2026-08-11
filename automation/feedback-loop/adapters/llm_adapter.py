#!/usr/bin/env python3
"""Executable LLM adapter for the feedback loop: claude and codex backends, API-first.

Wire format is the core's subprocess contract (feedback_loop/llm.py): one JSON request on stdin,
one strict JSON object on stdout. Configure with:

    export FEEDBACK_LOOP_LLM_COMMAND='<python3> <abs-path>/adapters/llm_adapter.py --provider claude'

Transport per provider: direct API when the key env var is set (ANTHROPIC_API_KEY /
OPENAI_API_KEY) and FEEDBACK_LOOP_ADAPTER_FORCE_CLI != 1. Claude can fall back to the
authenticated `claude -p` CLI; Codex is API-only and fails closed without OPENAI_API_KEY. Exit
codes: 0 ok, 1 provider/transport failure, 2 bad request, 124 timeout — every nonzero exit leaves
stdout empty so the core retries the call as a transport error.
"""

from __future__ import annotations

import argparse
import io
import json
import os
import sys
import time
from typing import Any

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from adapters import anthropic_provider, common, openai_provider  # noqa: E402

PROVIDER_ENV = "FEEDBACK_LOOP_LLM_PROVIDER"
PROVIDERS = {
    "claude": anthropic_provider,
    "codex": openai_provider,
}


def main(
    argv: list[str] | None = None,
    *,
    stdin: io.TextIOBase | None = None,
    stdout: io.TextIOBase | None = None,
    stderr: io.TextIOBase | None = None,
) -> int:
    in_stream = stdin if stdin is not None else sys.stdin
    out_stream = stdout if stdout is not None else sys.stdout
    err_stream = stderr if stderr is not None else sys.stderr

    parser = argparse.ArgumentParser(prog="llm_adapter", description=__doc__)
    parser.add_argument(
        "--provider",
        choices=sorted(PROVIDERS),
        default=os.environ.get(PROVIDER_ENV, "").strip() or "claude",
        help="LLM backend (default: $FEEDBACK_LOOP_LLM_PROVIDER or claude).",
    )
    args = parser.parse_args(argv)
    provider = PROVIDERS[args.provider]

    try:
        request = common.parse_request(in_stream.read())
    except (ValueError, OSError) as err:
        err_stream.write(f"adapter: invalid request JSON: {err}\n")
        return common.EXIT_BAD_REQUEST

    key_present = bool(os.environ.get(provider.KEY_ENV, "").strip())
    forced_cli = common.force_cli()
    api_mode = key_present and not forced_cli
    mode = "api" if api_mode else "cli"
    if not api_mode and not getattr(provider, "SUPPORTS_CLI", True):
        if forced_cli:
            reason = (
                f"{common.FORCE_CLI_ENV}=1 is not supported for --provider {args.provider}; "
                f"{provider.KEY_ENV} API mode is required"
            )
        else:
            reason = f"{provider.KEY_ENV} is required for --provider {args.provider}"
        err_stream.write(f"adapter[{args.provider}/api]: {reason}\n")
        return common.EXIT_TRANSPORT
    started = time.monotonic()
    try:
        response = provider.complete_api(request) if api_mode else provider.complete_cli(request)
    except common.AdapterError as err:
        err_stream.write(f"adapter[{args.provider}/{mode}]: {common.truncate(str(err))}\n")
        return err.exit_code

    common.write_usage_record(
        request,
        provider=args.provider,
        mode=mode,
        response=response,
        duration_ms=int((time.monotonic() - started) * 1000),
        stream=err_stream,
    )
    out_stream.write(json.dumps(response.payload, separators=(",", ":"), sort_keys=True))
    out_stream.write("\n")
    return common.EXIT_OK


if __name__ == "__main__":
    sys.exit(main())

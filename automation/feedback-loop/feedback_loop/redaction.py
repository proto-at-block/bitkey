"""Deterministic redaction for feedback-loop prompts and persisted artifacts."""

from __future__ import annotations

import re
from typing import Any

SECRET_PATTERNS: tuple[tuple[re.Pattern[str], str], ...] = (
    (
        re.compile(
            r"-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----.*?-----END [A-Z0-9 ]*PRIVATE KEY-----",
            re.DOTALL,
        ),
        "[REDACTED_PRIVATE_KEY]",
    ),
    (
        re.compile(r"\b(?:bc1|tb1|bcrt1)[ac-hj-np-z02-9]{11,87}\b", re.IGNORECASE),
        "[REDACTED_BITCOIN_ADDRESS]",
    ),
    (
        re.compile(r"\b(?:[13mn2][1-9A-HJ-NP-Za-km-z]{25,34})\b"),
        "[REDACTED_BITCOIN_ADDRESS]",
    ),
    (
        re.compile(
            r"\b(?:xpub|xprv|tpub|tprv|ypub|yprv|zpub|zprv|upub|uprv|vpub|vprv|"
            r"Ypub|Yprv|Zpub|Zprv|Upub|Uprv|Vpub|Vprv)[1-9A-HJ-NP-Za-km-z]{20,}\b"
        ),
        "[REDACTED_EXTENDED_KEY]",
    ),
    (
        re.compile(
            r"(?i)\b(?:bip-?39\s+)?(?:mnemonic|seed phrase|seed words|recovery phrase)"
            r"\b\s*[:= -]+\s*(?:[a-z]+[\s,]+){11,23}[a-z]+\b"
        ),
        "[REDACTED_BIP39_MNEMONIC]",
    ),
    (
        re.compile(r"\babandon(?:\s+abandon){10}\s+about\b"),
        "[REDACTED_BIP39_MNEMONIC]",
    ),
    (re.compile(r"\bgh[pousr]_[A-Za-z0-9_]{20,}\b"), "[REDACTED_GITHUB_TOKEN]"),
    (re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{20,}\b"), "[REDACTED_SLACK_TOKEN]"),
    (re.compile(r"\bAKIA[0-9A-Z]{16}\b"), "[REDACTED_AWS_ACCESS_KEY]"),
    (re.compile(r"\bsk-ant-[A-Za-z0-9_-]{20,}\b"), "[REDACTED_ANTHROPIC_KEY]"),
    (re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b"), "[REDACTED_API_KEY]"),
    (
        re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"),
        "[REDACTED_JWT]",
    ),
    (
        re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"),
        "[REDACTED_EMAIL]",
    ),
    (
        re.compile(r"(?<![0-9A-Fa-f])[0-9A-Fa-f]{64}(?![0-9A-Fa-f])"),
        "[REDACTED_HEX_SECRET]",
    ),
)

UNTRUSTED_DATA_OPEN = "<untrusted-data>"
UNTRUSTED_DATA_CLOSE = "</untrusted-data>"


def redact_value(value: Any) -> Any:
    """Recursively redact secret-like strings while preserving JSON structure."""
    if isinstance(value, dict):
        return {str(key): redact_value(item) for key, item in value.items()}
    if isinstance(value, list):
        return [redact_value(item) for item in value]
    if isinstance(value, tuple):
        return [redact_value(item) for item in value]
    if isinstance(value, str):
        return redact_text(value)
    return value


def redact_text(text: str) -> str:
    redacted = text
    for pattern, replacement in SECRET_PATTERNS:
        redacted = pattern.sub(replacement, redacted)
    return redacted


def as_untrusted_data(text: str) -> str:
    if text.startswith(UNTRUSTED_DATA_OPEN) and text.endswith(UNTRUSTED_DATA_CLOSE):
        return text
    return f"{UNTRUSTED_DATA_OPEN}\n{text}\n{UNTRUSTED_DATA_CLOSE}"

"""Tests for feedback-loop prompt and artifact redaction."""

from __future__ import annotations

import json
import os
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.artifacts import write_run_bundle  # noqa: E402
from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.redaction import as_untrusted_data, redact_value  # noqa: E402


GITHUB_TOKEN = "ghp_012345678901234567890123456789012345"
BITCOIN_ADDRESS = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kg3g4ty"
XPUB = "xpub" + "A" * 100
XPRV = "xprv" + "B" * 100
MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
HEX_SECRET = "0123456789abcdef" * 4


class TestRedaction(unittest.TestCase):
    def test_redaction_preserves_structure_and_marks_untrusted_prompt_data(self):
        payload = {
            "signal_id": "review:1",
            "path": "automation/feedback-loop/adapters/common.py",
            "body_excerpt": (
                f"token={GITHUB_TOKEN} address={BITCOIN_ADDRESS} xpub={XPUB} "
                f"xprv={XPRV} mnemonic={MNEMONIC} txid={HEX_SECRET}"
            ),
        }

        redacted = redact_value(payload)

        self.assertEqual(redacted["signal_id"], "review:1")
        self.assertEqual(redacted["path"], "automation/feedback-loop/adapters/common.py")
        rendered = json.dumps(redacted)
        for secret in (GITHUB_TOKEN, BITCOIN_ADDRESS, XPUB, XPRV, MNEMONIC, HEX_SECRET):
            self.assertNotIn(secret, rendered)
        self.assertIn("[REDACTED_GITHUB_TOKEN]", redacted["body_excerpt"])
        self.assertIn("[REDACTED_BITCOIN_ADDRESS]", redacted["body_excerpt"])
        self.assertIn("[REDACTED_EXTENDED_KEY]", redacted["body_excerpt"])
        self.assertIn("[REDACTED_BIP39_MNEMONIC]", redacted["body_excerpt"])
        self.assertIn("[REDACTED_HEX_SECRET]", redacted["body_excerpt"])
        self.assertTrue(as_untrusted_data("{}").startswith("<untrusted-data>"))

    def test_run_bundle_redacts_persisted_summary_text(self):
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)
            write_run_bundle(
                RunConfig(dry_run=True, output_dir=str(output_dir), extra={}),
                mode="pr",
                pr_urls=[],
                counts={},
                proposal_eval=None,
                proposal_evals=[],
                llm_learnings=[
                    {
                        "learning_id": "learn-1",
                        "evidence_summary": f"Reviewer pasted {BITCOIN_ADDRESS} {XPRV}",
                    }
                ],
                llm_debug={"errors": [f"adapter saw {MNEMONIC}"]},
                triage_report=triage_report(f"triage saw {HEX_SECRET} {GITHUB_TOKEN}"),
                full_triage_report=triage_report(f"full triage saw {GITHUB_TOKEN}"),
                proposals=[],
                blocked_proposals=[],
                emit_results=[],
            )

            learnings = (output_dir / "llm-learnings.json").read_text()
            debug = (output_dir / "llm-debug.json").read_text()
            triage = (output_dir / "triage-report.md").read_text()

        for secret in (BITCOIN_ADDRESS, XPRV, MNEMONIC, HEX_SECRET, GITHUB_TOKEN):
            self.assertNotIn(secret, learnings + debug + triage)
        self.assertIn("[REDACTED_BITCOIN_ADDRESS]", learnings)
        self.assertIn("[REDACTED_EXTENDED_KEY]", learnings)
        self.assertIn("[REDACTED_BIP39_MNEMONIC]", debug)
        self.assertIn("[REDACTED_HEX_SECRET]", triage)
        self.assertIn("[REDACTED_GITHUB_TOKEN]", triage)


def triage_report(markdown: str) -> SimpleNamespace:
    return SimpleNamespace(markdown=markdown, summary=[], comment_volume_summary={})


if __name__ == "__main__":
    unittest.main()

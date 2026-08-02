"""Tests for the deterministic bot/process-noise prefilter."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.models import NormalizedSignal, RawSignal  # noqa: E402
from feedback_loop.pipeline.noise import prefilter  # noqa: E402


def signal(
    source_id: str,
    body: str,
    *,
    is_bot: bool = False,
    raw: dict | None = None,
) -> NormalizedSignal:
    raw_signal = RawSignal(
        kind="issue_comment",
        source_id=source_id,
        source_url=f"https://github.com/squareup/wallet/pull/1#{source_id}",
        repo="squareup/wallet",
        pr_number=1,
        captured_at="2026-06-09T00:00:00Z",
        body=body,
        is_bot=is_bot,
    )
    return NormalizedSignal(
        raw=raw_signal,
        kind=raw_signal.kind,
        source="issue_comment",
        source_id=source_id,
        source_url=raw_signal.source_url,
        repo=raw_signal.repo,
        pr_number=1,
        captured_at=raw_signal.captured_at,
        harvest_version="test",
        body=body,
        raw_metadata=dict(raw or {}),
        is_bot=is_bot,
    )


class TestNoisePrefilter(unittest.TestCase):
    def test_filters_process_noise_with_auditable_exclusions(self):
        cases = [
            (signal("linear:1", "Created Linear issue BKW-99: https://linear.app/x"), "excluded:linear_linkback"),
            (signal("owl:1", "Owner Owl routed this to bitkey-software."), "excluded:owner_status"),
            (signal("gate:1", "Merge Gatekeeper: waiting for required status checks."), "excluded:merge_gatekeeper"),
            (signal("codex:1", "Codex reviewed this PR. No issues found.", is_bot=True), "excluded:codex_wrapper"),
            (signal("ack:1", "🤖 Addressed in the latest commit.", is_bot=True), "excluded:agent_ack"),
            (signal("ack:2", "Fixed.", raw={"in_reply_to_id": "900"}), "excluded:agent_ack"),
        ]

        kept, noise = prefilter([item for item, _ in cases])

        self.assertEqual(kept, [])
        self.assertEqual(len(noise), len(cases))
        for excluded, (_, expected_tag) in zip(noise, cases):
            self.assertIsNotNone(excluded.exclusion)
            self.assertEqual(excluded.primary_class, "not_actionable")
            self.assertIn(expected_tag, excluded.secondary_tags)

    def test_subjective_judgments_pass_through_to_the_llm(self):
        # nits, preferences, product questions are LLM classifications now, not regexes.
        comments = [
            signal("nit:1", "nit: typo in this comment"),
            signal("pref:1", "I'd rather we rename this to FooService."),
            signal("q:1", "Should we allow empty descriptors here?"),
            signal("miss:1", "This drops the original status word before retrying."),
        ]

        kept, noise = prefilter(comments)

        self.assertEqual(len(kept), 4)
        self.assertEqual(noise, [])

    def test_acknowledgement_with_new_finding_is_kept(self):
        comment = signal(
            "ack:3",
            "Fixed the null check, but the retry path is still broken.",
            raw={"in_reply_to_id": "900"},
        )

        kept, noise = prefilter([comment])

        self.assertEqual(len(kept), 1)
        self.assertEqual(noise, [])

    def test_non_feedback_kinds_are_never_filtered(self):
        commit = signal("commit:1", "Linear issue linkback text in a commit message")
        object.__setattr__(commit.raw, "kind", "commit")
        commit_signal = NormalizedSignal(
            raw=commit.raw,
            kind="commit",
            source="commit",
            source_id="commit:1",
            source_url=commit.source_url,
            repo=commit.repo,
            pr_number=1,
            captured_at=commit.captured_at,
            harvest_version="test",
            body=commit.body,
        )

        kept, noise = prefilter([commit_signal])

        self.assertEqual(len(kept), 1)
        self.assertEqual(noise, [])


if __name__ == "__main__":
    unittest.main()

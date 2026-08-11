"""Tests for the LLM signal-classification stage."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.llm import FakeLlmClient, LlmClientError  # noqa: E402
from feedback_loop.models import NormalizedSignal, PrFacts, RawSignal, SignalFacts  # noqa: E402
from feedback_loop.pipeline.llm_classify import classify_signals  # noqa: E402


def feedback_signal(
    source_id: str,
    *,
    pr_number: int = 1,
    body: str = "Please add validation coverage.",
    facts: SignalFacts | None = None,
) -> NormalizedSignal:
    raw = RawSignal(
        kind="review_comment",
        source_id=source_id,
        source_url=f"https://github.com/squareup/wallet/pull/{pr_number}#{source_id}",
        repo="squareup/wallet",
        pr_number=pr_number,
        captured_at="2026-06-09T00:00:00Z",
        body=body,
    )
    return NormalizedSignal(
        raw=raw,
        kind=raw.kind,
        source="review_comment",
        source_id=source_id,
        source_url=raw.source_url,
        repo=raw.repo,
        pr_number=pr_number,
        captured_at=raw.captured_at,
        harvest_version="test",
        body=body,
        area="app",
        facts=facts
        or SignalFacts(
            later_commit_source_ids=("commit:abc",),
            path_in_diff=True,
            author_trusted=True,
        ),
    )


def pr_facts(pr_number: int = 1) -> dict[int, PrFacts]:
    return {
        pr_number: PrFacts(
            pr_number=pr_number,
            repo="squareup/wallet",
            pr_url=f"https://github.com/squareup/wallet/pull/{pr_number}",
            base_sha="a" * 40,
            head_sha="b" * 40,
            changed_paths=("app/Feature.kt", "app/src/test/FeatureTest.kt"),
        )
    }


def classification(
    signal_id: str,
    *,
    primary_class: str = "miss",
    severity: str = "high",
    confidence: float = 0.9,
    exclusion_reason: str | None = None,
    destination: str | None = "test_or_linter",
    resolution: dict | None = None,
) -> dict:
    return {
        "signal_id": signal_id,
        "primary_class": primary_class,
        "severity": severity,
        "confidence": confidence,
        "exclusion_reason": exclusion_reason,
        "suggested_destination": destination,
        "resolution": resolution,
        "rationale": "Grounded in the supplied facts.",
    }


class TestClassifySignals(unittest.TestCase):
    def test_applies_validated_classifications(self):
        signal = feedback_signal("review_comment:1")
        client = FakeLlmClient(
            [
                {
                    "classifications": [
                        classification(
                            "review_comment:1",
                            resolution={
                                "state": "resolved_with_durable_coverage",
                                "evidence_signal_ids": ["commit:abc"],
                                "coverage_paths": ["app/src/test/FeatureTest.kt"],
                                "rationale": "A later commit added the test.",
                            },
                        )
                    ]
                }
            ]
        )

        result = classify_signals(RunConfig(), client, [signal], pr_facts())

        classified = result.signals[0]
        self.assertEqual(classified.primary_class, "miss")
        self.assertEqual(classified.severity, "high")
        self.assertEqual(classified.suggested_destination, "test_or_linter")
        self.assertFalse(classified.manual_triage)
        assert classified.resolution is not None
        self.assertEqual(classified.resolution.state, "resolved_with_durable_coverage")
        self.assertEqual(classified.resolution.evidence_signal_ids, ("commit:abc",))
        self.assertEqual(result.batch_count, 1)
        self.assertEqual(result.llm_calls, 1)
        request = client.requests[0]
        self.assertEqual(request["task"], "classify_feedback_signals")
        self.assertEqual(
            request["input"]["pr_facts"][0]["changed_paths"],
            ["app/Feature.kt", "app/src/test/FeatureTest.kt"],
        )

    def test_excluded_classes_require_exclusion_reason(self):
        signal = feedback_signal("review_comment:1")
        client = FakeLlmClient(
            [
                # First response invalid (nit without reason) -> format retry -> valid.
                {
                    "classifications": [
                        classification(
                            "review_comment:1",
                            primary_class="nit",
                            destination=None,
                        )
                    ]
                },
                {
                    "classifications": [
                        classification(
                            "review_comment:1",
                            primary_class="nit",
                            exclusion_reason="style_nit",
                            destination=None,
                        )
                    ]
                },
            ]
        )

        result = classify_signals(RunConfig(), client, [signal], pr_facts())

        classified = result.signals[0]
        self.assertEqual(classified.primary_class, "nit")
        self.assertTrue(classified.is_excluded)
        assert classified.exclusion is not None
        self.assertEqual(classified.exclusion.reason, "style_nit")
        self.assertIsNone(classified.suggested_destination)
        self.assertEqual(result.llm_calls, 2)
        self.assertEqual(
            client.requests[1]["task"],
            "normalize_signal_classification_format",
        )

    def test_unknown_signal_ids_trigger_format_retry(self):
        signal = feedback_signal("review_comment:1")
        client = FakeLlmClient(
            [
                {"classifications": [classification("review_comment:unknown")]},
                {"classifications": [classification("review_comment:1")]},
            ]
        )

        result = classify_signals(RunConfig(), client, [signal], pr_facts())

        self.assertEqual(result.signals[0].primary_class, "miss")
        self.assertEqual(result.llm_calls, 2)

    def test_unsupported_durable_coverage_claim_is_downgraded(self):
        signal = feedback_signal("review_comment:1")
        client = FakeLlmClient(
            [
                {
                    "classifications": [
                        classification(
                            "review_comment:1",
                            resolution={
                                "state": "resolved_with_durable_coverage",
                                "evidence_signal_ids": ["commit:not-in-facts"],
                                "coverage_paths": [],
                                "rationale": "Claimed coverage.",
                            },
                        )
                    ]
                }
            ]
        )

        result = classify_signals(RunConfig(), client, [signal], pr_facts())

        resolution = result.signals[0].resolution
        assert resolution is not None
        self.assertEqual(resolution.state, "resolved_without_durable_coverage")
        self.assertEqual(resolution.evidence_signal_ids, ())
        self.assertIn("lacked cited evidence", resolution.rationale)

    def test_invented_coverage_paths_are_discarded_and_downgraded(self):
        signal = feedback_signal("review_comment:1")
        client = FakeLlmClient(
            [
                {
                    "classifications": [
                        classification(
                            "review_comment:1",
                            resolution={
                                "state": "resolved_with_durable_coverage",
                                "evidence_signal_ids": [],
                                "coverage_paths": ["app/src/test/InventedTest.kt"],
                                "rationale": "Claimed a test path.",
                            },
                        )
                    ]
                }
            ]
        )

        result = classify_signals(RunConfig(), client, [signal], pr_facts())

        resolution = result.signals[0].resolution
        assert resolution is not None
        self.assertEqual(resolution.state, "resolved_without_durable_coverage")
        self.assertEqual(resolution.coverage_paths, ())
        self.assertIn("lacked cited evidence", resolution.rationale)

    def test_self_evidence_id_is_ignored_for_durable_coverage(self):
        signal = feedback_signal("review_comment:1")
        client = FakeLlmClient(
            [
                {
                    "classifications": [
                        classification(
                            "review_comment:1",
                            resolution={
                                "state": "resolved_with_durable_coverage",
                                "evidence_signal_ids": ["review_comment:1"],
                                "coverage_paths": [],
                                "rationale": "Claimed self evidence.",
                            },
                        )
                    ]
                }
            ]
        )

        result = classify_signals(RunConfig(), client, [signal], pr_facts())

        resolution = result.signals[0].resolution
        assert resolution is not None
        self.assertEqual(resolution.state, "resolved_without_durable_coverage")
        self.assertEqual(resolution.evidence_signal_ids, ())

    def test_changed_coverage_path_preserves_durable_coverage(self):
        signal = feedback_signal("review_comment:1")
        client = FakeLlmClient(
            [
                {
                    "classifications": [
                        classification(
                            "review_comment:1",
                            resolution={
                                "state": "resolved_with_durable_coverage",
                                "evidence_signal_ids": ["review_comment:1"],
                                "coverage_paths": ["app/src/test/FeatureTest.kt"],
                                "rationale": "A changed test path supports this.",
                            },
                        )
                    ]
                }
            ]
        )

        result = classify_signals(RunConfig(), client, [signal], pr_facts())

        resolution = result.signals[0].resolution
        assert resolution is not None
        self.assertEqual(resolution.state, "resolved_with_durable_coverage")
        self.assertEqual(resolution.evidence_signal_ids, ())
        self.assertEqual(resolution.coverage_paths, ("app/src/test/FeatureTest.kt",))

    def test_later_reply_body_lookup_can_include_noise_filtered_reply(self):
        signal = feedback_signal(
            "review_comment:1",
            facts=SignalFacts(
                later_reply_source_ids=("reply:fixed",),
                path_in_diff=True,
                author_trusted=True,
            ),
        )
        client = FakeLlmClient(
            [{"classifications": [classification("review_comment:1")]}]
        )

        classify_signals(
            RunConfig(),
            client,
            [signal],
            pr_facts(),
            signal_bodies_by_id={
                "review_comment:1": signal.body,
                "reply:fixed": "Fixed by adding FeatureTest coverage.",
            },
        )

        later_replies = client.requests[0]["input"]["signals"][0]["facts"]["later_replies"]
        self.assertEqual(later_replies[0]["signal_id"], "reply:fixed")
        self.assertEqual(
            later_replies[0]["body_excerpt"],
            "Fixed by adding FeatureTest coverage.",
        )

    def test_low_confidence_actionable_goes_to_manual_triage(self):
        signal = feedback_signal("review_comment:1")
        client = FakeLlmClient(
            [{"classifications": [classification("review_comment:1", confidence=0.4)]}]
        )

        result = classify_signals(RunConfig(), client, [signal], pr_facts())

        classified = result.signals[0]
        self.assertTrue(classified.manual_triage)
        self.assertIsNone(classified.suggested_destination)
        self.assertIn("manual_triage", classified.secondary_tags)

    def test_missing_signal_falls_back_to_manual_triage(self):
        signals = [feedback_signal("review_comment:1"), feedback_signal("review_comment:2")]
        client = FakeLlmClient(
            [{"classifications": [classification("review_comment:1")]}]
        )

        result = classify_signals(RunConfig(), client, signals, pr_facts())

        self.assertEqual(result.signals[0].primary_class, "miss")
        self.assertTrue(result.signals[1].manual_triage)
        self.assertEqual(result.unclassified_signal_ids, ("review_comment:2",))

    def test_failed_batch_isolates_and_run_continues(self):
        # Two PRs with 30 signals each cannot share a 40-signal batch.
        pr_one = [
            feedback_signal(f"review_comment:1-{index}", pr_number=1) for index in range(30)
        ]
        pr_two = [
            feedback_signal(f"review_comment:2-{index}", pr_number=2) for index in range(30)
        ]
        client = FakeLlmClient(
            [
                # Batch 1 fails twice (transport), batch 2 succeeds.
                LlmClientError("adapter timed out"),
                LlmClientError("adapter timed out"),
                {
                    "classifications": [
                        classification(item.source_id) for item in pr_two
                    ]
                },
            ]
        )

        result = classify_signals(RunConfig(), client, [*pr_one, *pr_two], pr_facts(1))

        self.assertEqual(result.batch_count, 2)
        self.assertEqual(result.failed_batches, 1)
        self.assertTrue(result.signals[0].manual_triage)
        self.assertIn("classifier batch failed", result.signals[0].rationale)
        self.assertEqual(result.signals[30].primary_class, "miss")

    def test_batches_never_split_a_pr(self):
        signals = [
            feedback_signal(f"review_comment:{pr}:{index}", pr_number=pr)
            for pr in range(1, 4)
            for index in range(20)
        ]
        responses = []
        # 20+20 fits in one 40-signal batch; the third PR overflows into a second batch.
        responses.append(
            {
                "classifications": [
                    classification(item.source_id)
                    for item in signals
                    if item.pr_number in (1, 2)
                ]
            }
        )
        responses.append(
            {
                "classifications": [
                    classification(item.source_id)
                    for item in signals
                    if item.pr_number == 3
                ]
            }
        )
        client = FakeLlmClient(responses)

        result = classify_signals(RunConfig(), client, signals, {})

        self.assertEqual(result.batch_count, 2)
        first_batch_ids = {
            item["signal_id"] for item in client.requests[0]["input"]["signals"]
        }
        self.assertTrue(all(":3:" not in signal_id for signal_id in first_batch_ids))
        second_batch_prs = {
            item["pr_number"] for item in client.requests[1]["input"]["signals"]
        }
        self.assertEqual(second_batch_prs, {3})

    def test_oversized_single_pr_is_split_under_signal_cap(self):
        signals = [
            feedback_signal(f"review_comment:1:{index}", pr_number=1)
            for index in range(45)
        ]
        client = FakeLlmClient(
            [
                {
                    "classifications": [
                        classification(item.source_id) for item in signals[:40]
                    ]
                },
                {
                    "classifications": [
                        classification(item.source_id) for item in signals[40:]
                    ]
                },
            ]
        )

        result = classify_signals(RunConfig(), client, signals, pr_facts(1))

        self.assertEqual(result.batch_count, 2)
        self.assertEqual(len(client.requests[0]["input"]["signals"]), 40)
        self.assertEqual(len(client.requests[1]["input"]["signals"]), 5)
        self.assertTrue(all(signal.primary_class == "miss" for signal in result.signals))


if __name__ == "__main__":
    unittest.main()

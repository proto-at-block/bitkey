"""Tests for LLM clustering with semantic memory matching."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.cluster_memory import (  # noqa: E402
    ClusterMemoryMetadata,
    ClusterMemoryReadResult,
    ClusterMemoryRecord,
    idempotency_key_for_memory,
)
from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.llm import FakeLlmClient, LlmClientError  # noqa: E402
from feedback_loop.models import NormalizedSignal, RawSignal  # noqa: E402
from feedback_loop.pipeline.llm_cluster import cluster_signals  # noqa: E402


def actionable_signal(
    source_id: str,
    *,
    pr_number: int = 1,
    severity: str = "high",
    destination: str = "agents_check",
    manual_triage: bool = False,
) -> NormalizedSignal:
    raw = RawSignal(
        kind="review_comment",
        source_id=source_id,
        source_url=f"https://github.com/squareup/wallet/pull/{pr_number}#{source_id}",
        repo="squareup/wallet",
        pr_number=pr_number,
        captured_at="2026-06-09T00:00:00Z",
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
        body="The retry path drops the original status word.",
        area="firmware",
        primary_class="miss",
        severity=severity,
        confidence=0.9,
        suggested_destination=destination,
        manual_triage=manual_triage,
    )


def memory_record(
    slug: str,
    *,
    destination: str = "agents_check",
    pr_numbers: tuple[int, ...] = (10, 11),
    schema_version: int = 2,
) -> ClusterMemoryRecord:
    return ClusterMemoryRecord(
        issue_identifier="BKW-55",
        issue_url="https://linear.app/squareup/issue/BKW-55/x",
        title=f"Feedback cluster: {slug}",
        metadata=ClusterMemoryMetadata(
            schema_version=schema_version,
            idempotency_key=idempotency_key_for_memory(slug, destination),
            memory_slug=slug,
            destination=destination,
            decision="gather_more_evidence",
            source_urls=tuple(
                f"https://github.com/squareup/wallet/pull/{n}#discussion_r{n}" for n in pr_numbers
            ),
            distinct_pr_numbers=pr_numbers,
            resolution_counts={},
            coverage_paths=(),
            last_seen_at="",
            window={},
        ),
        from_metadata=True,
    )


def cluster_response(
    slug: str,
    member_ids: list[str],
    *,
    matched: str | None = None,
    destination: str = "agents_check",
) -> dict:
    return {
        "clusters": [
            {
                "slug": slug,
                "matched_memory_slug": matched,
                "title": "Preserve status words on retry",
                "summary": "Firmware retries must preserve the original status word.",
                "destination": destination,
                "area": "firmware",
                "member_signal_ids": member_ids,
                "rationale": "Same durable standard violated the same way.",
            }
        ]
    }


def read_result(*records: ClusterMemoryRecord) -> ClusterMemoryReadResult:
    return ClusterMemoryReadResult(status="ok", records=tuple(records))


class TestClusterSignals(unittest.TestCase):
    def test_clusters_actionable_signals_and_computes_decision(self):
        signals = [
            actionable_signal("review:1", pr_number=1),
            actionable_signal("review:2", pr_number=2),
        ]
        client = FakeLlmClient(
            [cluster_response("preserve-status-word", ["review:1", "review:2"])]
        )

        result = cluster_signals(RunConfig(), client, signals, read_result())

        self.assertEqual(len(result.clusters), 1)
        cluster = result.clusters[0]
        self.assertEqual(cluster.slug, "preserve-status-word")
        self.assertEqual(cluster.severity, "high")
        self.assertEqual(cluster.frequency, 2)
        self.assertEqual(cluster.merged_pr_numbers, (1, 2))
        self.assertEqual(cluster.rank, 8.0)
        # high severity at threshold 2 with no manual triage -> promote.
        self.assertEqual(cluster.decision, "promote")
        self.assertEqual(cluster.matched_memory_key, "")

    def test_excluded_and_review_only_signals_never_cluster(self):
        actionable = actionable_signal("review:1")
        excluded = actionable_signal("review:2")
        excluded = type(excluded)(**{**excluded.__dict__, "primary_class": "nit"})
        client = FakeLlmClient([cluster_response("preserve-status-word", ["review:1"])])

        result = cluster_signals(RunConfig(), client, [actionable, excluded], read_result())

        sent_ids = [item["signal_id"] for item in client.requests[0]["input"]["signals"]]
        self.assertEqual(sent_ids, ["review:1"])

    def test_matched_memory_record_forces_slug_and_key_and_merges_history(self):
        record = memory_record("preserve-status-word", pr_numbers=(10, 11))
        signals = [actionable_signal("review:1", pr_number=1)]
        client = FakeLlmClient(
            [
                cluster_response(
                    "some-new-slug-the-model-minted",
                    ["review:1"],
                    matched="preserve-status-word",
                )
            ]
        )

        result = cluster_signals(RunConfig(), client, signals, read_result(record))

        cluster = result.clusters[0]
        self.assertEqual(cluster.slug, "preserve-status-word")
        self.assertEqual(cluster.matched_memory_key, record.idempotency_key)
        self.assertEqual(cluster.matched_issue_identifier, "BKW-55")
        self.assertEqual(cluster.merged_pr_numbers, (1, 10, 11))
        self.assertEqual(cluster.frequency, 3)
        reconciliation = result.reconciliations[0]
        self.assertEqual(reconciliation.frequency_before, 1)
        self.assertEqual(reconciliation.frequency_after, 3)
        self.assertEqual(reconciliation.historical_pr_numbers, (10, 11))

    def test_matched_memory_record_uses_destination_specific_row(self):
        agents_check = memory_record(
            "preserve-status-word",
            destination="agents_check",
            pr_numbers=(10,),
        )
        test_or_linter = memory_record(
            "preserve-status-word",
            destination="test_or_linter",
            pr_numbers=(20,),
        )
        signals = [
            actionable_signal(
                "review:1",
                pr_number=1,
                destination="test_or_linter",
            )
        ]
        client = FakeLlmClient(
            [
                cluster_response(
                    "some-new-slug-the-model-minted",
                    ["review:1"],
                    matched="preserve-status-word",
                    destination="test_or_linter",
                )
            ]
        )

        result = cluster_signals(
            RunConfig(),
            client,
            signals,
            read_result(agents_check, test_or_linter),
        )

        cluster = result.clusters[0]
        self.assertEqual(cluster.slug, "preserve-status-word")
        self.assertEqual(cluster.matched_memory_key, test_or_linter.idempotency_key)
        self.assertEqual(cluster.merged_pr_numbers, (1, 20))
        self.assertEqual(result.reconciliations[0].historical_pr_numbers, (20,))

    def test_legacy_records_are_offered_and_matchable(self):
        legacy = ClusterMemoryRecord(
            issue_identifier="BKW-12",
            issue_url="https://linear.app/squareup/issue/BKW-12/x",
            title="Legacy cluster",
            metadata=ClusterMemoryMetadata(
                schema_version=1,
                idempotency_key="feedback-loop:legacykey12345678",
                memory_slug="miss:firmware:status:agents_check",
                destination="agents_check",
                decision="legacy",
                source_urls=("https://github.com/squareup/wallet/pull/9#discussion_r9",),
                distinct_pr_numbers=(9,),
                resolution_counts={},
                coverage_paths=(),
                last_seen_at="",
                window={},
            ),
            from_metadata=True,
        )
        client = FakeLlmClient(
            [
                cluster_response(
                    "ignored",
                    ["review:1"],
                    matched="miss:firmware:status:agents_check",
                )
            ]
        )

        result = cluster_signals(
            RunConfig(),
            client,
            [actionable_signal("review:1", pr_number=1)],
            read_result(legacy),
        )

        records_sent = client.requests[0]["input"]["memory_records"]
        self.assertTrue(records_sent[0]["legacy"])
        cluster = result.clusters[0]
        # Legacy lexical slugs are kept verbatim so the same issue updates in place.
        self.assertEqual(cluster.slug, "miss:firmware:status:agents_check")
        self.assertEqual(cluster.matched_memory_key, "feedback-loop:legacykey12345678")

    def test_unassigned_signals_become_singleton_clusters_with_warning(self):
        signals = [
            actionable_signal("review:1", pr_number=1),
            actionable_signal("review:2", pr_number=2),
        ]
        client = FakeLlmClient([cluster_response("preserve-status-word", ["review:1"])])

        result = cluster_signals(RunConfig(), client, signals, read_result())

        slugs = sorted(cluster.slug for cluster in result.clusters)
        self.assertEqual(len(slugs), 2)
        self.assertTrue(any(slug.startswith("unclustered-") for slug in slugs))
        self.assertTrue(any("unclustered" in warning for warning in result.warnings))

    def test_unknown_member_triggers_format_retry(self):
        signals = [actionable_signal("review:1")]
        client = FakeLlmClient(
            [
                cluster_response("preserve-status-word", ["review:unknown"]),
                cluster_response("preserve-status-word", ["review:1"]),
            ]
        )

        result = cluster_signals(RunConfig(), client, signals, read_result())

        self.assertEqual(result.clusters[0].slug, "preserve-status-word")
        self.assertEqual(result.llm_calls, 2)
        self.assertEqual(
            client.requests[1]["task"],
            "normalize_signal_clustering_format",
        )

    def test_chunk_failure_degrades_to_singletons(self):
        signals = [actionable_signal("review:1")]
        client = FakeLlmClient(
            [LlmClientError("adapter crashed"), LlmClientError("adapter crashed")]
        )

        result = cluster_signals(RunConfig(), client, signals, read_result())

        self.assertEqual(len(result.clusters), 1)
        self.assertTrue(result.clusters[0].slug.startswith("unclustered-"))
        self.assertTrue(result.errors)

    def test_output_overflow_bisects_into_smaller_chunks(self):
        # A chunk whose output exceeds the model token cap (stop_reason=max_tokens) is split in
        # half and retried, yielding real clusters instead of silent singletons. The chunk (12)
        # is above the bisection floor (8); its halves (6) clear the responder's overflow point.
        signals = [actionable_signal(f"review:{i}", pr_number=i) for i in range(1, 13)]

        def responder(request):
            ids = [s["signal_id"] for s in request["input"]["signals"]]
            if len(ids) > 6:
                return LlmClientError("model stopped with stop_reason=max_tokens")
            return cluster_response("shared-theme", ids)

        client = FakeLlmClient(responder=responder)

        result = cluster_signals(RunConfig(), client, signals, read_result())

        self.assertFalse(
            any(cluster.slug.startswith("unclustered-") for cluster in result.clusters)
        )
        self.assertTrue(any("bisect" in warning for warning in result.warnings))
        # 1 failed (un-retried) attempt on the 12-signal chunk + 2 successful halves.
        self.assertEqual(result.llm_calls, 3)
        self.assertEqual(sum(len(cluster.signals) for cluster in result.clusters), 12)

    def test_output_overflow_at_floor_degrades_to_manual_triage(self):
        # A single signal that still overflows cannot be split further; it must degrade to a
        # manual-triage singleton (gather_more_evidence) instead of silently promoting.
        signals = [actionable_signal("review:1", pr_number=1, severity="critical")]
        client = FakeLlmClient([LlmClientError("model stopped with stop_reason=max_tokens")])

        result = cluster_signals(RunConfig(), client, signals, read_result())

        self.assertEqual(len(result.clusters), 1)
        self.assertTrue(result.clusters[0].slug.startswith("unclustered-"))
        # critical + frequency 1 would normally promote; manual_triage degradation blocks that.
        self.assertEqual(result.clusters[0].decision, "gather_more_evidence")
        self.assertEqual(result.llm_calls, 1)
        self.assertTrue(result.errors)

    def test_manual_triage_member_forces_gather_more_evidence(self):
        signals = [
            actionable_signal("review:1", pr_number=1, manual_triage=True),
            actionable_signal("review:2", pr_number=2),
        ]
        client = FakeLlmClient(
            [cluster_response("preserve-status-word", ["review:1", "review:2"])]
        )

        result = cluster_signals(RunConfig(), client, signals, read_result())

        self.assertEqual(result.clusters[0].decision, "gather_more_evidence")

    def test_below_threshold_frequency_gathers_more_evidence(self):
        signals = [actionable_signal("review:1", pr_number=1, severity="medium")]
        client = FakeLlmClient([cluster_response("preserve-status-word", ["review:1"])])

        result = cluster_signals(RunConfig(), client, signals, read_result())

        # medium needs 3 distinct PRs; one PR -> gather_more_evidence.
        self.assertEqual(result.clusters[0].decision, "gather_more_evidence")

    def test_test_or_linter_destination_converts_to_mechanical_check(self):
        signals = [
            actionable_signal(
                "review:1", pr_number=1, severity="high", destination="test_or_linter"
            ),
            actionable_signal(
                "review:2", pr_number=2, severity="high", destination="test_or_linter"
            ),
        ]
        client = FakeLlmClient(
            [
                cluster_response(
                    "preserve-status-word",
                    ["review:1", "review:2"],
                    destination="test_or_linter",
                )
            ]
        )

        result = cluster_signals(RunConfig(), client, signals, read_result())

        self.assertEqual(result.clusters[0].decision, "convert_to_mechanical_check")


if __name__ == "__main__":
    unittest.main()

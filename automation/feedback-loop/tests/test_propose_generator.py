"""Tests for proposal generation from assessed clusters."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.models import Cluster, Exclusion, NormalizedSignal, RawSignal  # noqa: E402
from feedback_loop.pipeline.propose import REQUIRED_SECTION_KEYS, propose  # noqa: E402


def signal(
    source_id: str,
    *,
    pr_number: int = 123,
    body: str = "Raw reviewer comment text",
    primary_class: str = "miss",
    severity: str = "high",
    confidence: float = 0.8,
    manual_triage: bool = False,
    excluded: bool = False,
    destination: str | None = "agents_check",
) -> NormalizedSignal:
    raw = RawSignal(
        kind="review_comment",
        source_id=source_id,
        source_url=f"https://github.com/squareup/wallet/pull/{pr_number}#{source_id}",
        repo="squareup/wallet",
        pr_number=pr_number,
        captured_at="2026-05-04T00:00:00Z",
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
        primary_class=primary_class,
        severity=severity,
        confidence=confidence,
        rationale="classifier rationale",
        suggested_destination=None if manual_triage or excluded else destination,
        evidence_ids=[source_id],
        manual_triage=manual_triage,
        exclusion=(
            Exclusion(reason="not_actionable", summary="not actionable")
            if excluded
            else None
        ),
    )


def cluster(
    *,
    severity: str = "high",
    frequency: int = 2,
    destination: str | None = "agents_check",
    signals: list[NormalizedSignal] | None = None,
) -> Cluster:
    items = signals or [
        signal("review_comment:1", pr_number=101),
        signal("review_comment:2", pr_number=202),
    ]
    return Cluster(
        theme=f"miss:automation:validation:{destination or 'manual_triage'}",
        signals=items,
        area="automation",
        severity=severity,
        frequency=frequency,
        rank=4.0 * frequency,
        suggested_destination=destination,
        summary="Repeated validation misses in automation.",
        representative_examples=[
            "review_comment:1: Raw reviewer comment text",
            "review_comment:2: Raw reviewer comment text",
        ],
        source_urls=[item.source_url for item in items],
    )


class TestProposeGenerator(unittest.TestCase):
    def test_generates_one_filled_proposal_per_eligible_cluster(self):
        proposals = propose(RunConfig(dry_run=True), [cluster()])

        self.assertEqual(len(proposals), 1)
        proposal = proposals[0]
        self.assertEqual(proposal.destination, "agents_check")
        self.assertEqual(proposal.confidence, 0.8)
        self.assertEqual(proposal.template_title, ".agents/checks guardrail")
        self.assertEqual(set(proposal.sections), REQUIRED_SECTION_KEYS)
        self.assertEqual(len(proposal.evidence_urls), 2)
        self.assertEqual(len(proposal.replay_cases), 2)
        self.assertTrue(proposal.validation_commands)
        self.assertFalse(proposal.eval_passed)
        self.assertTrue(proposal.dry_run_only)

    def test_skips_manual_triage_low_confidence_excluded_and_research_only_clusters(self):
        manual = cluster(signals=[signal("manual:1", manual_triage=True)], frequency=2)
        low_confidence = cluster(signals=[signal("low:1", confidence=0.49)], frequency=2)
        excluded = cluster(signals=[signal("excluded:1", excluded=True)], frequency=2)
        research_only = cluster(severity="medium", frequency=1)

        proposals = propose(
            RunConfig(),
            [manual, low_confidence, excluded, research_only],
        )

        self.assertEqual(proposals, [])

    def test_low_severity_only_promotes_mechanical_guardrails(self):
        docs_cluster = cluster(severity="low", frequency=5, destination="docs")
        mechanical_cluster = cluster(
            severity="low",
            frequency=5,
            destination="test_or_linter",
        )

        proposals = propose(RunConfig(), [docs_cluster, mechanical_cluster])

        self.assertEqual(len(proposals), 1)
        self.assertEqual(proposals[0].destination, "test_or_linter")

    def test_skips_world_model_clusters_as_research_only(self):
        world_model = cluster(
            severity="critical",
            frequency=3,
            destination="world_model",
            signals=[
                signal(
                    "review_comment:1",
                    severity="critical",
                    destination="world_model",
                )
            ],
        )

        proposals = propose(RunConfig(), [world_model])

        self.assertEqual(proposals, [])

    def test_skips_review_only_clusters(self):
        review_only_signal = signal(
            "review:1",
            body="General concern.",
            primary_class="not_actionable",
            severity="low",
            destination="agents_check",
        )
        review_only = Cluster(
            theme="not_actionable:repo-wide:general-concern:review_only",
            signals=[review_only_signal],
            area="repo-wide",
            severity="low",
            frequency=5,
            rank=0.0,
            suggested_destination="agents_check",
            summary="Review-only weak signal.",
            representative_examples=["review:1: General concern."],
            source_urls=[review_only_signal.source_url],
        )

        proposals = propose(RunConfig(), [review_only])

        self.assertEqual(proposals, [])

    def test_proposal_does_not_copy_raw_comment_examples(self):
        raw_body = "Reviewer said exactly this raw sentence."
        item = cluster(signals=[signal("raw:1", body=raw_body), signal("raw:2", body=raw_body)])

        proposals = propose(RunConfig(), [item])

        proposal_text = "\n".join([proposals[0].summary, *proposals[0].sections.values()])
        self.assertNotIn(raw_body, proposal_text)
        self.assertIn("Source URLs", proposal_text)

    def test_evidence_section_caps_visible_urls_but_preserves_proposal_evidence(self):
        signals = [
            signal(f"review_comment:{index}", pr_number=100 + index)
            for index in range(1, 8)
        ]
        item = cluster(signals=signals, frequency=7)

        proposals = propose(RunConfig(), [item])

        self.assertEqual(len(proposals), 1)
        proposal = proposals[0]
        evidence_text = proposal.sections["evidence"]
        self.assertEqual(proposal.evidence_urls, item.source_urls)
        self.assertIn(item.source_urls[4], evidence_text)
        self.assertNotIn(item.source_urls[5], evidence_text)
        self.assertIn("(+2 more in proposal.evidence_urls)", evidence_text)


if __name__ == "__main__":
    unittest.main()

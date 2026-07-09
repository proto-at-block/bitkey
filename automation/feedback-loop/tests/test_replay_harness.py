"""Tests for current-vs-proposed replay harness."""

from __future__ import annotations

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.models import (  # noqa: E402
    Destination,
    ReplayCase,
    ReplayCommitRange,
    ReplayFinding,
)
from feedback_loop.replay import (  # noqa: E402
    replay_report_artifact,
    run_replay_harness,
    write_replay_report,
)


class TestReplayHarness(unittest.TestCase):
    def test_compares_current_and_proposed_guidance_on_same_cases(self):
        case = replay_case("case-1", expected_destination="agents_check")

        report = run_replay_harness(
            current_runner=lambda item: [],
            proposed_runner=lambda item: [
                ReplayFinding(
                    case_id=item.case_id,
                    summary=item.expected_finding,
                    destination="agents_check",
                    source_url=item.source_comment_url,
                )
            ],
            cases=[case],
        )

        self.assertEqual(report.current_summary.missed_misses, 1)
        self.assertEqual(report.proposed_summary.caught_misses, 1)
        self.assertEqual(report.proposed_summary.blocking_failures, 0)
        self.assertTrue(report.proposal_publishable)

        artifact = replay_report_artifact(report)
        self.assertTrue(artifact["proposal_publishable"])
        self.assertFalse(artifact["cases"][0]["current"]["caught_miss"])
        self.assertTrue(artifact["cases"][0]["proposed"]["caught_miss"])

    def test_records_extra_findings_separately_from_expected_miss(self):
        case = replay_case("case-1")

        report = run_replay_harness(
            current_runner=lambda item: [],
            proposed_runner=lambda item: [
                ReplayFinding(
                    case_id=item.case_id,
                    summary=item.expected_finding,
                    source_url=item.source_comment_url,
                ),
                ReplayFinding(
                    case_id="other-case",
                    summary="Noisy unrelated finding.",
                ),
            ],
            cases=[case],
        )

        self.assertEqual(report.proposed_summary.caught_misses, 1)
        self.assertEqual(report.proposed_summary.extra_findings, 1)
        self.assertTrue(report.proposal_publishable)

        proposed = replay_report_artifact(report)["cases"][0]["proposed"]
        self.assertEqual(proposed["extra_findings"][0]["case_id"], "other-case")

    def test_expected_finding_can_match_a_paraphrased_summary(self):
        case = replay_case("case-1", expected_destination="agents_check")

        report = run_replay_harness(
            current_runner=lambda item: [],
            proposed_runner=lambda item: [
                ReplayFinding(
                    case_id=item.case_id,
                    summary="Catch this historical replay miss.",
                    destination="agents_check",
                    source_url=item.source_comment_url,
                )
            ],
            cases=[case],
        )

        self.assertEqual(report.proposed_summary.caught_misses, 1)
        self.assertEqual(report.proposed_summary.extra_findings, 0)
        self.assertTrue(report.proposal_publishable)

    def test_same_case_and_destination_requires_expected_content(self):
        case = replay_case("case-1", expected_destination="agents_check")

        report = run_replay_harness(
            current_runner=lambda item: [],
            proposed_runner=lambda item: [
                ReplayFinding(
                    case_id=item.case_id,
                    summary="Generic finding for this case.",
                    destination="agents_check",
                    source_url=item.source_comment_url,
                )
            ],
            cases=[case],
        )

        self.assertEqual(report.proposed_summary.caught_misses, 0)
        self.assertEqual(report.proposed_summary.missed_misses, 1)
        self.assertEqual(report.proposed_summary.extra_findings, 1)
        self.assertFalse(report.proposal_publishable)

    def test_single_overlapping_word_does_not_match_expected_finding(self):
        case = replay_case("case-1", expected_destination="agents_check")

        report = run_replay_harness(
            current_runner=lambda item: [],
            proposed_runner=lambda item: [
                ReplayFinding(
                    case_id=item.case_id,
                    summary="Miss.",
                    destination="agents_check",
                    source_url=item.source_comment_url,
                )
            ],
            cases=[case],
        )

        self.assertEqual(report.proposed_summary.caught_misses, 0)
        self.assertEqual(report.proposed_summary.extra_findings, 1)
        self.assertFalse(report.proposal_publishable)

    def test_missing_source_url_does_not_match_expected_finding(self):
        case = replay_case("case-1", expected_destination="agents_check")

        report = run_replay_harness(
            current_runner=lambda item: [],
            proposed_runner=lambda item: [
                ReplayFinding(
                    case_id=item.case_id,
                    summary=item.expected_finding,
                    destination="agents_check",
                )
            ],
            cases=[case],
        )

        self.assertEqual(report.proposed_summary.caught_misses, 0)
        self.assertEqual(report.proposed_summary.extra_findings, 1)
        self.assertFalse(report.proposal_publishable)

    def test_wrong_source_url_does_not_match_expected_finding(self):
        case = replay_case("case-1", expected_destination="agents_check")

        report = run_replay_harness(
            current_runner=lambda item: [],
            proposed_runner=lambda item: [
                ReplayFinding(
                    case_id=item.case_id,
                    summary=item.expected_finding,
                    destination="agents_check",
                    source_url="https://github.com/squareup/wallet/pull/123#discussion_r2",
                )
            ],
            cases=[case],
        )

        self.assertEqual(report.proposed_summary.caught_misses, 0)
        self.assertEqual(report.proposed_summary.extra_findings, 1)
        self.assertFalse(report.proposal_publishable)

    def test_runtime_failures_are_reported_and_block_publication(self):
        case = replay_case("case-1")

        def broken_runner(item):
            raise RuntimeError(f"could not replay {item.case_id}")

        report = run_replay_harness(
            current_runner=lambda item: [],
            proposed_runner=broken_runner,
            cases=[case],
        )

        self.assertEqual(report.proposed_summary.runtime_failures, 1)
        self.assertEqual(report.proposed_summary.blocking_failures, 1)
        self.assertFalse(report.proposal_publishable)

        proposed = replay_report_artifact(report)["cases"][0]["proposed"]
        self.assertTrue(proposed["blocking_failure"])
        self.assertEqual(proposed["runtime_failure"]["exception_type"], "RuntimeError")
        self.assertIn("case-1", proposed["runtime_failure"]["message"])

    def test_write_replay_report_is_json_and_deterministic(self):
        case = replay_case("case-1")
        report = run_replay_harness(
            current_runner=lambda item: [],
            proposed_runner=lambda item: [
                ReplayFinding(
                    case_id=item.case_id,
                    summary=item.expected_finding,
                    source_url=item.source_comment_url,
                )
            ],
            cases=[case],
        )
        path = self.temp_path()

        write_replay_report(report, path)

        data = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(data, replay_report_artifact(report))

    def test_invalid_runner_output_is_reported_as_runtime_failure(self):
        case = replay_case("case-1")

        report = run_replay_harness(
            current_runner=lambda item: [],
            proposed_runner=lambda item: ["not a finding"],
            cases=[case],
        )

        self.assertEqual(report.proposed_summary.runtime_failures, 1)
        self.assertFalse(report.proposal_publishable)

    def temp_path(self) -> Path:
        handle = tempfile.NamedTemporaryFile(delete=False)
        handle.close()
        self.addCleanup(lambda: os.path.exists(handle.name) and os.unlink(handle.name))
        return Path(handle.name)


def replay_case(
    case_id: str,
    *,
    expected_destination: Destination | None = None,
) -> ReplayCase:
    return ReplayCase(
        case_id=case_id,
        repo="squareup/wallet",
        pr_number=123,
        pr_url="https://github.com/squareup/wallet/pull/123",
        commit_range=ReplayCommitRange(base="base", head="head", merge_commit="merge"),
        changed_files=("automation/example.py",),
        miss_class="miss",
        source_comment_url="https://github.com/squareup/wallet/pull/123#discussion_r1",
        expected_destination=expected_destination,
        expected_finding="Flag the historical miss.",
        summary="Historical miss summary.",
    )


if __name__ == "__main__":
    unittest.main()

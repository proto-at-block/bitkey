"""Tests for outcome reconciliation (Linear states from draft-PR results)."""

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
    parse_memory_metadata_block,
    render_memory_metadata_block,
)
from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.github import GitHubError  # noqa: E402
from feedback_loop.linear_control import BUILDERBOT_APPROVAL_LABEL  # noqa: E402
from feedback_loop.outcomes import outcome_artifact, reconcile_outcomes  # noqa: E402


class FakeReader:
    def __init__(self, *records: ClusterMemoryRecord):
        self.records = records

    def read_cluster_memory(self) -> ClusterMemoryReadResult:
        return ClusterMemoryReadResult(status="ok", records=tuple(self.records))


class FakeWriter:
    def __init__(self):
        self.calls: list[dict] = []

    def update_issue_outcome(self, issue_identifier, *, state, description, remove_labels=()):
        self.calls.append(
            {
                "id": issue_identifier,
                "state": state,
                "description": description,
                "remove_labels": tuple(remove_labels),
            }
        )
        return "https://linear.app/squareup/issue/updated"


class FakeGitHub:
    def __init__(self, *, hits: list[dict] | None = None, status: dict | None = None):
        self.hits = hits if hits is not None else []
        self.status = status or {}
        self.search_queries: list[str] = []

    def search_prs_by_change_set(self, repo, change_set_id, *, max_items=5):
        self.search_queries.append(change_set_id)
        return list(self.hits)

    def pull_request_status(self, ref):
        return {
            "state": self.status.get("state", "open"),
            "merged": self.status.get("merged", False),
            "merged_at": self.status.get("merged_at", ""),
            "draft": self.status.get("draft", False),
            "html_url": f"https://github.com/squareup/wallet/pull/{ref.number}",
            "number": ref.number,
        }


def record(
    *,
    change_set_id: str = "change-set:abc123def4567890",
    issue_status: str = "eval_passed",
    slug: str = "validation-coverage",
    identifier: str = "BKW-90",
) -> ClusterMemoryRecord:
    metadata = ClusterMemoryMetadata(
        schema_version=2,
        idempotency_key=idempotency_key_for_memory(slug, "agents_check"),
        memory_slug=slug,
        destination="agents_check",
        decision="promote",
        source_urls=("https://github.com/squareup/wallet/pull/12#discussion_r1",),
        distinct_pr_numbers=(12,),
        resolution_counts={},
        coverage_paths=(),
        last_seen_at="2026-06-01T00:00:00Z",
        window={},
        eval_state="pr_ready",
        issue_status=issue_status,
        change_set_id=change_set_id,
    )
    description = f"## Summary\nTheme body.\n\n{render_memory_metadata_block(metadata)}\n"
    return ClusterMemoryRecord(
        issue_identifier=identifier,
        issue_url=f"https://linear.app/squareup/issue/{identifier}/x",
        title=f"Feedback cluster: {slug}",
        metadata=metadata,
        from_metadata=True,
        description=description,
    )


def pr_hit(number: int = 456) -> dict:
    return {
        "number": number,
        "html_url": f"https://github.com/squareup/wallet/pull/{number}",
    }


class TestReconcileOutcomes(unittest.TestCase):
    def test_merged_pr_adopts_issue_and_removes_trigger_label(self):
        writer = FakeWriter()
        github = FakeGitHub(hits=[pr_hit()], status={"merged": True, "state": "closed"})

        result = reconcile_outcomes(
            RunConfig(dry_run=False, limit=50),
            reader=FakeReader(record()),
            writer=writer,
            github=github,
        )

        action = result.actions[0]
        self.assertEqual(action.pr_state, "merged")
        self.assertEqual(action.new_status, "adopted")
        self.assertEqual(action.new_linear_state, "Done")
        self.assertTrue(action.applied)
        self.assertEqual(len(writer.calls), 1)
        call = writer.calls[0]
        self.assertEqual(call["id"], "BKW-90")
        self.assertEqual(call["state"], "Done")
        self.assertEqual(call["remove_labels"], (BUILDERBOT_APPROVAL_LABEL,))
        updated_metadata, _ = parse_memory_metadata_block(call["description"])
        assert updated_metadata is not None
        self.assertEqual(updated_metadata.issue_status, "adopted")
        self.assertEqual(
            updated_metadata.outcome_pr_url,
            "https://github.com/squareup/wallet/pull/456",
        )
        self.assertTrue(updated_metadata.outcome_checked_at)

    def test_closed_unmerged_pr_rejects_issue(self):
        writer = FakeWriter()
        github = FakeGitHub(hits=[pr_hit()], status={"merged": False, "state": "closed"})

        result = reconcile_outcomes(
            RunConfig(dry_run=False, limit=50),
            reader=FakeReader(record()),
            writer=writer,
            github=github,
        )

        action = result.actions[0]
        self.assertEqual(action.pr_state, "closed")
        self.assertEqual(action.new_status, "rejected")
        self.assertEqual(action.new_linear_state, "Canceled")
        self.assertEqual(writer.calls[0]["remove_labels"], (BUILDERBOT_APPROVAL_LABEL,))

    def test_open_pr_moves_to_in_review_without_label_removal(self):
        writer = FakeWriter()
        github = FakeGitHub(hits=[pr_hit()], status={"merged": False, "state": "open"})

        result = reconcile_outcomes(
            RunConfig(dry_run=False, limit=50),
            reader=FakeReader(record(issue_status="eval_passed")),
            writer=writer,
            github=github,
        )

        action = result.actions[0]
        self.assertEqual(action.pr_state, "open")
        self.assertEqual(action.new_status, "pr_open")
        self.assertEqual(action.new_linear_state, "In Review")
        self.assertFalse(action.remove_approval_label)
        self.assertEqual(writer.calls[0]["remove_labels"], ())

    def test_already_reconciled_records_are_skipped(self):
        writer = FakeWriter()
        github = FakeGitHub(hits=[pr_hit()], status={"merged": False, "state": "open"})

        result = reconcile_outcomes(
            RunConfig(dry_run=False, limit=50),
            reader=FakeReader(record(issue_status="pr_open")),
            writer=writer,
            github=github,
        )

        action = result.actions[0]
        self.assertEqual(action.skipped_reason, "already_reconciled")
        self.assertFalse(action.applied)
        self.assertEqual(writer.calls, [])

    def test_missing_pr_is_reported_not_written(self):
        writer = FakeWriter()
        github = FakeGitHub(hits=[])

        result = reconcile_outcomes(
            RunConfig(dry_run=False, limit=50),
            reader=FakeReader(record()),
            writer=writer,
            github=github,
        )

        action = result.actions[0]
        self.assertEqual(action.pr_state, "not_found")
        self.assertEqual(action.skipped_reason, "pr_not_found")
        self.assertEqual(writer.calls, [])

    def test_dry_run_plans_without_writing(self):
        writer = FakeWriter()
        github = FakeGitHub(hits=[pr_hit()], status={"merged": True, "state": "closed"})

        result = reconcile_outcomes(
            RunConfig(dry_run=True, limit=50),
            reader=FakeReader(record()),
            writer=writer,
            github=github,
        )

        action = result.actions[0]
        self.assertEqual(action.new_status, "adopted")
        self.assertFalse(action.applied)
        self.assertEqual(writer.calls, [])
        self.assertTrue(result.dry_run)

    def test_only_in_flight_records_with_change_sets_are_considered(self):
        github = FakeGitHub(hits=[pr_hit()], status={"merged": True, "state": "closed"})
        records = [
            record(identifier="BKW-1"),
            record(identifier="BKW-2", change_set_id=""),
            record(identifier="BKW-3", issue_status="adopted"),
            record(identifier="BKW-4", issue_status="needs_triage"),
        ]

        result = reconcile_outcomes(
            RunConfig(dry_run=True, limit=50),
            reader=FakeReader(*records),
            writer=None,
            github=github,
        )

        self.assertEqual(result.counts["candidates"], 1)
        self.assertEqual(result.actions[0].issue_identifier, "BKW-1")

    def test_limit_bounds_candidates(self):
        github = FakeGitHub(hits=[pr_hit()], status={"merged": True, "state": "closed"})
        records = [record(identifier=f"BKW-{index}", slug=f"slug-{index}") for index in range(5)]

        result = reconcile_outcomes(
            RunConfig(dry_run=True, limit=2),
            reader=FakeReader(*records),
            writer=None,
            github=github,
        )

        self.assertEqual(result.counts["candidates"], 2)

    def test_search_errors_are_recorded_per_record(self):
        class ErrorGitHub(FakeGitHub):
            def search_prs_by_change_set(self, repo, change_set_id, *, max_items=5):
                raise GitHubError("rate limited")

        result = reconcile_outcomes(
            RunConfig(dry_run=True, limit=50),
            reader=FakeReader(record()),
            writer=None,
            github=ErrorGitHub(),
        )

        action = result.actions[0]
        self.assertEqual(action.pr_state, "error")
        self.assertIn("rate limited", action.error)
        self.assertEqual(result.counts["errors"], 1)

    def test_artifact_shape(self):
        github = FakeGitHub(hits=[pr_hit()], status={"merged": True, "state": "closed"})

        result = reconcile_outcomes(
            RunConfig(dry_run=True, limit=50),
            reader=FakeReader(record()),
            writer=None,
            github=github,
        )
        artifact = outcome_artifact(result)

        self.assertTrue(artifact["dry_run"])
        self.assertEqual(artifact["counts"]["candidates"], 1)
        self.assertEqual(artifact["actions"][0]["new_status"], "adopted")
        self.assertEqual(artifact["actions"][0]["pr_state"], "merged")


if __name__ == "__main__":
    unittest.main()

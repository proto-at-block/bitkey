"""Tests for schema-v2 Linear-backed cluster memory."""

from __future__ import annotations

import json
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.cluster_memory import (  # noqa: E402
    MAX_MEMORY_UPSERTS_PER_RUN,
    MEMORY_SCHEMA_VERSION,
    ClusterMemoryMetadata,
    ClusterMemoryRecord,
    LinearIssueSnapshot,
    SqAgentToolsLinearClient,
    build_cluster_memory_issue_plan,
    idempotency_key_for_memory,
    memory_key_for_cluster,
    parse_linear_issue_memory,
    parse_memory_metadata_block,
    plan_cluster_memory_upserts,
    render_memory_metadata_block,
    replace_memory_metadata_block,
    should_write_cluster_memory,
)
from feedback_loop.linear_control import BUILDERBOT_APPROVAL_LABEL  # noqa: E402
from feedback_loop.models import (  # noqa: E402
    Cluster,
    NormalizedSignal,
    Proposal,
    ProposalEvalArtifact,
    ProposalFileChange,
    RawSignal,
)


def signal(source_id: str, *, pr_number: int = 1) -> NormalizedSignal:
    raw = RawSignal(
        kind="review_comment",
        source_id=source_id,
        source_url=f"https://github.com/squareup/wallet/pull/{pr_number}#discussion_{source_id}",
        repo="squareup/wallet",
        pr_number=pr_number,
        captured_at="2026-06-09T00:00:00Z",
    )
    return NormalizedSignal(
        raw=raw,
        kind=raw.kind,
        source="review_comment",
        source_id=raw.source_id,
        source_url=raw.source_url,
        repo=raw.repo,
        pr_number=raw.pr_number,
        captured_at=raw.captured_at,
        harvest_version="test",
        body="Please add validation coverage.",
        primary_class="miss",
        severity="medium",
        confidence=0.9,
        suggested_destination="agents_check",
    )


def cluster(
    slug: str = "validation-coverage",
    *,
    decision: str = "promote",
    destination: str = "agents_check",
    pr_numbers: tuple[int, ...] = (1, 2, 3),
    matched_memory_key: str = "",
    rank: float = 8.0,
) -> Cluster:
    signals = [signal(f"review:{number}", pr_number=number) for number in pr_numbers]
    return Cluster(
        slug=slug,
        signals=signals,
        title="Validation coverage is required",
        area="automation",
        severity="medium",
        frequency=len(pr_numbers),
        current_pr_numbers=pr_numbers,
        merged_pr_numbers=pr_numbers,
        rank=rank,
        suggested_destination=destination,
        decision=decision,
        matched_memory_key=matched_memory_key,
        summary="Reviewers repeatedly required validation coverage.",
        source_urls=[item.source_url for item in signals],
    )


def _gather_more_cluster(
    *,
    slug: str,
    severity: str,
    pr_numbers: tuple[int, ...],
    rank: float,
) -> Cluster:
    """A gather_more_evidence cluster with explicit severity/frequency for cap-ordering tests."""
    signals = [signal(f"{slug}:{number}", pr_number=number) for number in pr_numbers]
    return Cluster(
        slug=slug,
        signals=signals,
        title=slug,
        area="automation",
        severity=severity,
        frequency=len(pr_numbers),
        current_pr_numbers=pr_numbers,
        merged_pr_numbers=pr_numbers,
        rank=rank,
        suggested_destination="agents_check",
        decision="gather_more_evidence",
        matched_memory_key="",
        summary="Sub-threshold recurring theme.",
        source_urls=[item.source_url for item in signals],
    )


def memory_record(
    slug: str,
    *,
    destination: str = "agents_check",
    pr_numbers: tuple[int, ...] = (1, 2),
    source_urls: tuple[str, ...] | None = None,
    idempotency_key: str = "",
    schema_version: int = MEMORY_SCHEMA_VERSION,
    from_metadata: bool = True,
) -> ClusterMemoryRecord:
    key = idempotency_key or idempotency_key_for_memory(slug, destination)
    return ClusterMemoryRecord(
        issue_identifier="BKW-77",
        issue_url="https://linear.app/squareup/issue/BKW-77/memory",
        title=f"Feedback cluster: {slug}",
        metadata=ClusterMemoryMetadata(
            schema_version=schema_version,
            idempotency_key=key,
            memory_slug=slug,
            destination=destination,
            decision="gather_more_evidence",
            source_urls=source_urls
            if source_urls is not None
            else tuple(
                f"https://github.com/squareup/wallet/pull/{number}#discussion_r{number}"
                for number in pr_numbers
            ),
            distinct_pr_numbers=pr_numbers,
            resolution_counts={
                "unresolved": len(pr_numbers),
                "resolved_without_durable_coverage": 0,
                "resolved_with_durable_coverage": 0,
            },
            coverage_paths=(),
            last_seen_at="2026-06-01T00:00:00Z",
            window={},
        ),
        from_metadata=from_metadata,
    )


def pr_ready_proposal(item: Cluster, *, destination: str = "agents_check") -> Proposal:
    return Proposal(
        cluster=item,
        destination=destination,
        summary=f"Add a {destination} guardrail for validation coverage.",
        evidence_urls=list(item.source_urls),
        confidence=0.9,
        file_changes=[ProposalFileChange(path=".agents/checks/validation.md", content="check\n")],
        learning_id="learn-memory",
        route_id=f"llm:learn-memory:{destination}",
        eval_state="pr_ready",
        eval_passed=True,
        eval_artifact=ProposalEvalArtifact(
            state="pr_ready",
            cluster_slug=item.slug,
            rubric_markdown="Status: PASS",
        ),
    )


class TestMemoryKeys(unittest.TestCase):
    def test_key_derives_from_slug_and_destination(self):
        key = idempotency_key_for_memory("validation-coverage", "agents_check")

        self.assertTrue(key.startswith("feedback-loop:"))
        self.assertEqual(key, idempotency_key_for_memory("validation-coverage", "agents_check"))
        self.assertNotEqual(key, idempotency_key_for_memory("validation-coverage", "docs"))
        self.assertNotEqual(key, idempotency_key_for_memory("other-slug", "agents_check"))

    def test_matched_memory_key_wins_verbatim(self):
        item = cluster(matched_memory_key="feedback-loop:legacy0123456789")

        self.assertEqual(memory_key_for_cluster(item), "feedback-loop:legacy0123456789")


class TestMetadataRoundTrip(unittest.TestCase):
    def test_v2_metadata_round_trips(self):
        plan = build_cluster_memory_issue_plan(cluster())

        parsed, warnings = parse_memory_metadata_block(plan.description)

        self.assertEqual(warnings, ())
        self.assertIsNotNone(parsed)
        assert parsed is not None
        self.assertEqual(parsed.schema_version, MEMORY_SCHEMA_VERSION)
        self.assertEqual(parsed.memory_slug, "validation-coverage")
        self.assertEqual(parsed.destination, "agents_check")
        self.assertEqual(parsed.decision, "promote")
        self.assertEqual(parsed.distinct_pr_numbers, (1, 2, 3))

    def test_v1_metadata_payload_still_parses(self):
        v1_payload = {
            "schema_version": 1,
            "idempotency_key": "feedback-loop:v1key",
            "cluster_theme": "miss:app:validation:test_or_linter",
            "route_key": "test_or_linter",
            "destination": "test_or_linter",
            "decision": "promote",
            "source_urls": ["https://github.com/squareup/wallet/pull/1#discussion"],
            "distinct_pr_numbers": [1],
            "resolution_counts": {"unresolved": 1},
            "coverage_paths": [],
            "last_seen_at": "",
            "window": {},
        }
        description = (
            "<!-- feedback-loop:cluster-memory:v1\n" + json.dumps(v1_payload) + "\n-->"
        )

        parsed, warnings = parse_memory_metadata_block(description)

        self.assertEqual(warnings, ())
        assert parsed is not None
        self.assertEqual(parsed.schema_version, 1)
        self.assertEqual(parsed.memory_slug, "miss:app:validation:test_or_linter")
        self.assertEqual(parsed.idempotency_key, "feedback-loop:v1key")

    def test_legacy_description_falls_back_to_source_links(self):
        issue = LinearIssueSnapshot(
            identifier="BKW-123",
            url="https://linear.app/squareup/issue/BKW-123/example",
            title="Legacy feedback cluster",
            description=(
                "## Routing\n"
                "- Destination: `docs`\n"
                "- Cluster: `miss:docs:guidance:docs`\n"
                "## Links\n"
                "- https://github.com/squareup/wallet/pull/12#discussion_r1\n"
                "- https://github.com/squareup/wallet/pull/13\n"
            ),
        )

        record = parse_linear_issue_memory(issue)

        self.assertIsNotNone(record)
        assert record is not None
        self.assertFalse(record.from_metadata)
        self.assertEqual(record.memory_slug, "miss:docs:guidance:docs")
        self.assertEqual(record.distinct_pr_numbers, (12, 13))

    def test_malformed_metadata_is_ignored_with_warning(self):
        parsed, warnings = parse_memory_metadata_block(
            "<!-- feedback-loop:cluster-memory:v1\nnot-json\n-->"
        )

        self.assertIsNone(parsed)
        self.assertEqual(len(warnings), 1)
        self.assertIn("malformed", warnings[0])

    def test_replace_memory_metadata_block_substitutes_in_place(self):
        plan = build_cluster_memory_issue_plan(cluster())
        parsed, _ = parse_memory_metadata_block(plan.description)
        assert parsed is not None
        updated_metadata = ClusterMemoryMetadata(
            **{
                **parsed.__dict__,
                "issue_status": "adopted",
                "outcome_pr_url": "https://github.com/squareup/wallet/pull/99",
            }
        )

        updated = replace_memory_metadata_block(plan.description, updated_metadata)
        reparsed, _ = parse_memory_metadata_block(updated)

        assert reparsed is not None
        self.assertEqual(reparsed.issue_status, "adopted")
        self.assertEqual(reparsed.outcome_pr_url, "https://github.com/squareup/wallet/pull/99")
        self.assertEqual(updated.count("feedback-loop:cluster-memory:v1"), 1)


class TestDecisionGatedWrites(unittest.TestCase):
    def test_only_durable_decisions_are_written(self):
        self.assertTrue(should_write_cluster_memory(cluster(decision="promote")))
        self.assertTrue(
            should_write_cluster_memory(cluster(decision="convert_to_mechanical_check"))
        )
        self.assertTrue(should_write_cluster_memory(cluster(decision="gather_more_evidence")))
        self.assertFalse(should_write_cluster_memory(cluster(decision="already_covered")))
        self.assertFalse(should_write_cluster_memory(cluster(decision="review_only")))
        self.assertFalse(should_write_cluster_memory(cluster(decision="ignore")))
        self.assertFalse(should_write_cluster_memory(cluster(decision="")))


class TestPlanUpserts(unittest.TestCase):
    def test_proposal_attaches_to_its_clusters_single_row(self):
        item = cluster()
        proposal = pr_ready_proposal(item)

        plan = plan_cluster_memory_upserts(
            [item],
            proposals=[proposal],
            emit_results=[],
            existing_records=(),
            dry_run=True,
        )

        self.assertEqual(len(plan.upserts), 1)
        upsert = plan.upserts[0]
        self.assertEqual(upsert.action, "create")
        self.assertEqual(upsert.cluster_slug, "validation-coverage")
        self.assertEqual(upsert.proposal_eval_state, "pr_ready")
        self.assertIn("learn-memory", upsert.plan.description)

    def test_route_specific_pr_ready_proposals_get_separate_rows(self):
        item = cluster()
        primary = pr_ready_proposal(item, destination="agents_check")
        supporting = pr_ready_proposal(item, destination="docs")

        plan = plan_cluster_memory_upserts(
            [item],
            proposals=[primary, supporting],
            emit_results=[],
            existing_records=(),
            dry_run=True,
        )

        self.assertEqual(len(plan.upserts), 2)
        keys = {upsert.plan.idempotency_key for upsert in plan.upserts}
        destinations = {
            parse_memory_metadata_block(upsert.plan.description)[0].destination
            for upsert in plan.upserts
        }
        self.assertEqual(len(keys), 2)
        self.assertEqual(destinations, {"agents_check", "docs"})

    def test_existing_record_makes_update(self):
        item = cluster()
        existing = memory_record("validation-coverage")

        plan = plan_cluster_memory_upserts(
            [item],
            proposals=[],
            emit_results=[],
            existing_records=(existing,),
            dry_run=True,
        )

        self.assertEqual(plan.upserts[0].action, "update")
        self.assertEqual(plan.upserts[0].existing_issue_identifier, "BKW-77")

    def test_synthetic_proposal_cluster_still_gets_a_row(self):
        # A learning that matched no run cluster must not silently vanish in execute mode.
        synthetic = cluster(slug="synthetic-abc12345", decision="")
        proposal = pr_ready_proposal(synthetic)

        plan = plan_cluster_memory_upserts(
            [],
            proposals=[proposal],
            emit_results=[],
            existing_records=(),
            dry_run=True,
        )

        self.assertEqual(len(plan.upserts), 1)
        self.assertEqual(plan.upserts[0].cluster_slug, "synthetic-abc12345")
        self.assertEqual(plan.upserts[0].decision, "promote")

    def test_same_pr_different_evidence_urls_do_not_fold(self):
        item = cluster(slug="new-slug-for-same-theme", pr_numbers=(1, 2, 4))
        existing = memory_record("validation-coverage", pr_numbers=(1, 2))

        plan = plan_cluster_memory_upserts(
            [item],
            proposals=[],
            emit_results=[],
            existing_records=(existing,),
            dry_run=True,
        )

        self.assertEqual(plan.upserts[0].action, "create")
        self.assertNotEqual(plan.upserts[0].plan.idempotency_key, existing.idempotency_key)
        self.assertFalse(any("folded into existing issue" in warning for warning in plan.warnings))

    def test_exact_slug_match_folds_destination_drift(self):
        drifted = cluster(
            slug="validation-coverage",
            destination="test_or_linter",
            pr_numbers=(8, 9),
            matched_memory_key="",
        )
        existing = memory_record(
            "validation-coverage", destination="agents_check", pr_numbers=(1, 2)
        )

        plan = plan_cluster_memory_upserts(
            [drifted],
            proposals=[],
            emit_results=[],
            existing_records=(existing,),
            dry_run=True,
        )

        self.assertEqual(plan.upserts[0].action, "update")
        self.assertEqual(plan.upserts[0].plan.idempotency_key, existing.idempotency_key)
        self.assertTrue(any("folded into existing issue" in warning for warning in plan.warnings))

    def test_upsert_cap_drops_lowest_priority_and_logs(self):
        clusters = [
            cluster(slug=f"theme-{index:02d}", decision="gather_more_evidence", rank=float(index))
            for index in range(MAX_MEMORY_UPSERTS_PER_RUN + 5)
        ]
        clusters[0] = cluster(slug="promoted-theme", decision="promote", rank=99.0)

        plan = plan_cluster_memory_upserts(
            clusters,
            proposals=[],
            emit_results=[],
            existing_records=(),
            dry_run=True,
        )

        self.assertEqual(len(plan.upserts), MAX_MEMORY_UPSERTS_PER_RUN)
        self.assertEqual(len(plan.dropped_upserts), 5)
        self.assertEqual(plan.upserts[0].cluster_slug, "promoted-theme")
        self.assertTrue(all("memory upsert cap dropped" in warning for warning in plan.warnings))
        dropped_slugs = {item["cluster_slug"] for item in plan.dropped_upserts}
        self.assertEqual(len(dropped_slugs), 5)

    def test_upsert_cap_keeps_near_threshold_over_higher_rank(self):
        # Over the cap, a theme one PR short of promoting must survive even when a far-from-
        # threshold theme has a higher rank — otherwise the recurrence we want to accumulate is
        # silently dropped.
        os.environ["FEEDBACK_LOOP_MEMORY_UPSERT_CAP"] = "1"
        self.addCleanup(os.environ.pop, "FEEDBACK_LOOP_MEMORY_UPSERT_CAP", None)
        near = _gather_more_cluster(slug="near-threshold", severity="high", pr_numbers=(1,), rank=1.0)
        far = _gather_more_cluster(slug="far-threshold", severity="low", pr_numbers=(2,), rank=99.0)

        plan = plan_cluster_memory_upserts(
            [far, near],
            proposals=[],
            emit_results=[],
            existing_records=(),
            dry_run=True,
        )

        self.assertEqual(len(plan.upserts), 1)
        self.assertEqual(plan.upserts[0].cluster_slug, "near-threshold")
        dropped_slugs = {item["cluster_slug"] for item in plan.dropped_upserts}
        self.assertEqual(dropped_slugs, {"far-threshold"})

    def test_exact_evidence_url_overlap_folds_destination_drift(self):
        drifted = cluster(
            slug="new-slug-same-theme",
            destination="test_or_linter",
            pr_numbers=(8, 9),
            matched_memory_key="",
        )
        existing = memory_record(
            "validation-coverage", destination="agents_check", pr_numbers=(1, 2)
        )
        drifted.source_urls = [existing.source_urls[0]]

        plan = plan_cluster_memory_upserts(
            [drifted],
            proposals=[],
            emit_results=[],
            existing_records=(existing,),
            dry_run=True,
        )

        self.assertEqual(plan.upserts[0].action, "update")
        self.assertEqual(plan.upserts[0].plan.idempotency_key, existing.idempotency_key)
        self.assertTrue(any("folded into existing issue" in warning for warning in plan.warnings))


class FakeSqClient(SqAgentToolsLinearClient):
    """Overrides the subprocess boundary with canned payloads."""

    def __init__(self, pages: list[dict], **kwargs):
        super().__init__(**kwargs)
        self.pages = list(pages)
        self.calls: list[tuple[str, ...]] = []

    def _run(self, *args: str) -> dict:
        self.calls.append(args)
        if not self.pages:
            return {"issues": []}
        return self.pages.pop(0)


def issue_payload(identifier: str, slug: str) -> dict:
    plan = build_cluster_memory_issue_plan(cluster(slug=slug))
    return {
        "identifier": identifier,
        "url": f"https://linear.app/squareup/issue/{identifier}/x",
        "title": plan.title,
        "description": plan.description,
    }


class TestPaginatedReader(unittest.TestCase):
    def test_reader_follows_cursors_across_pages(self):
        client = FakeSqClient(
            [
                {"issues": [issue_payload("BKW-1", "theme-one")], "next_cursor": "abc"},
                {"issues": [issue_payload("BKW-2", "theme-two")]},
                {"issues": []},
            ],
            limit=1,
        )

        result = client.read_cluster_memory()

        self.assertEqual(result.status, "ok")
        self.assertEqual(
            [record.issue_identifier for record in result.records],
            ["BKW-1", "BKW-2"],
        )
        self.assertIn("abc", client.calls[1])

    def test_reader_warns_when_full_page_has_no_cursor(self):
        client = FakeSqClient(
            [
                {"issues": [issue_payload("BKW-1", "theme-one")]},
                {"issues": []},
            ],
            limit=1,
        )

        result = client.read_cluster_memory()

        self.assertTrue(
            any("may be truncated" in warning for warning in result.warnings),
            result.warnings,
        )

    def test_reader_includes_canceled_feedback_loop_issues(self):
        client = FakeSqClient(
            [
                {"issues": []},
                {"issues": []},
                {"issues": []},
            ]
        )

        client.read_cluster_memory()

        state_groups = [
            call[call.index("--state-types") + 1 :]
            for call in client.calls
            if call[0] == "search-issues"
        ]
        self.assertIn(("canceled",), state_groups)

    def test_writer_removes_trigger_label_when_not_planned(self):
        client = FakeSqClient([{"issue": {"url": "https://linear.app/x"}}])
        plan = build_cluster_memory_issue_plan(cluster())
        existing = memory_record("validation-coverage")

        url = client.upsert_cluster_memory(plan, existing=existing)

        self.assertEqual(url, "https://linear.app/x")
        payload = json.loads(client.calls[0][-1])
        self.assertEqual(payload["id"], "BKW-77")
        self.assertEqual(payload["remove_labels"], [BUILDERBOT_APPROVAL_LABEL])


if __name__ == "__main__":
    unittest.main()

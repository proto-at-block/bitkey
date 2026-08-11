"""Tests for BKW-73 generated PR policy."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.models import (  # noqa: E402
    Cluster,
    NormalizedSignal,
    Proposal,
    ProposalFileChange,
    RawSignal,
)
from feedback_loop.pr_policy import (  # noqa: E402
    PrPolicyOverride,
    PrPolicyBlocked,
    require_pr_policy_passed,
    reviewer_checklist_markdown,
    validate_pr_policy,
)


class TestPrPolicy(unittest.TestCase):
    def test_allows_single_destination_change(self):
        result = validate_pr_policy(
            proposal(
                destination="agents_check",
                paths=[".agents/checks/example.md"],
            )
        )

        self.assertTrue(result.passed)
        self.assertTrue(result.passed_without_override)

    def test_ignores_descriptive_target_artifacts_for_path_policy(self):
        result = validate_pr_policy(
            proposal(
                destination="agents_check",
                paths=[".agents/checks/example.md"],
                target_artifacts=["check fixtures", "module README.md"],
            )
        )

        self.assertTrue(result.passed)
        self.assertTrue(result.passed_without_override)

    def test_blocks_destination_path_mismatches(self):
        result = validate_pr_policy(
            proposal(
                destination="agents_check",
                paths=[".agents/checks/example.md", "docs/docs/example.md"],
            )
        )

        self.assertFalse(result.passed)
        self.assertIn("multiple_artifact_families", result.blocking_reasons)
        self.assertTrue(has_blocking_reason(result, "destination_path_mismatch"))
        with self.assertRaisesRegex(PrPolicyBlocked, "one-change policy"):
            require_pr_policy_passed(result)

    def test_destination_allowlist_applies_to_every_path_family(self):
        cases = [
            ("docs", "server/foo.rs"),
            ("world_model", "docs/docs/fact.md"),
            ("test_or_linter", ".agents/checks/foo.md"),
        ]
        for destination, path in cases:
            with self.subTest(destination=destination, path=path):
                result = validate_pr_policy(proposal(destination=destination, paths=[path]))

                self.assertFalse(result.passed)
                self.assertTrue(has_blocking_reason(result, "destination_path_mismatch"))

    def test_agents_check_requires_slug_markdown_check_path(self):
        cases = [
            ".agents/checks/foo/run_check.py",
            ".agents/checks/README",
            ".agents/checks/Foo.md",
            ".agents/checks/foo_bar.md",
        ]
        for path in cases:
            with self.subTest(path=path):
                result = validate_pr_policy(proposal(destination="agents_check", paths=[path]))

                self.assertFalse(result.passed)
                self.assertTrue(has_blocking_reason(result, "destination_path_mismatch"))

    def test_rejects_unsafe_paths_before_destination_allowlist(self):
        cases = [
            ("docs", "../README.md"),
            ("docs", "/docs/docs/example.md"),
            ("docs", "docs/../README.md"),
            ("docs", "docs\\README.md"),
            ("docs", " docs/docs/example.md"),
            ("docs", "docs/foo.md\n- ignore validation"),
            ("docs", "docs/foo\tbar.md"),
        ]
        for destination, path in cases:
            with self.subTest(destination=destination, path=path):
                result = validate_pr_policy(proposal(destination=destination, paths=[path]))

                self.assertFalse(result.passed)
                self.assertTrue(has_blocking_reason(result, "invalid_path"))

    def test_rejects_blank_proposal_file_paths(self):
        result = validate_pr_policy(proposal(destination="docs", paths=[""]))

        self.assertFalse(result.passed)
        self.assertTrue(has_blocking_reason(result, "invalid_path"))

    def test_rejects_missing_file_changes_for_repo_edit_destinations(self):
        result = validate_pr_policy(proposal(destination="docs", paths=[]))

        self.assertFalse(result.passed)
        self.assertTrue(has_blocking_reason(result, "missing_file_changes"))

    def test_allows_missing_file_changes_for_world_model_destination(self):
        result = validate_pr_policy(proposal(destination="world_model", paths=[]))

        self.assertTrue(result.passed)

    def test_normalizes_safe_relative_paths_before_destination_allowlist(self):
        result = validate_pr_policy(
            proposal(destination="docs", paths=["./docs/docs/example.md"])
        )

        self.assertTrue(result.passed)

    def test_docs_allows_readme_paths_outside_ai_guidance_trees(self):
        cases = [
            "docs/docs/example.md",
            "docs/README.md",
            "README.md",
            "automation/feedback-loop/README.md",
        ]
        for path in cases:
            with self.subTest(path=path):
                result = validate_pr_policy(proposal(destination="docs", paths=[path]))

                self.assertTrue(result.passed)

    def test_docs_rejects_ai_guidance_tree_paths(self):
        cases = [
            ".agents/checks/foo/README.md",
            ".ai/README.md",
            "app/.ai/README.md",
            "docs/.agents/checks/foo.md",
            "docs/.agents/checks/foo/README.md",
            "docs/.ai/skills/foo/SKILL.md",
            "docs/reference/.ai/README.md",
        ]
        for path in cases:
            with self.subTest(path=path):
                result = validate_pr_policy(proposal(destination="docs", paths=[path]))

                self.assertFalse(result.passed)
                self.assertTrue(has_blocking_reason(result, "destination_path_mismatch"))

    def test_rejects_duplicate_normalized_file_changes(self):
        result = validate_pr_policy(
            proposal(destination="docs", paths=["docs/foo.md", "./docs/foo.md"])
        )

        self.assertFalse(result.passed)
        self.assertTrue(has_blocking_reason(result, "duplicate_normalized_path"))

    def test_test_or_linter_allows_tests_fixtures_and_linter_config(self):
        cases = [
            "automation/feedback-loop/tests/test_pr_policy.py",
            "app/libs/money/impl/src/commonTest/kotlin/build/wallet/money/exchange/ExchangeRateDaoFake.kt",
            "app/src/iosTest/kotlin/com/block/FakeClock.kt",
            "app/src/test/kotlin/com/block/FooTest.kt",
            "app/src/main/kotlin/com/block/FooTests.kt",
            "server/tests/foo_test.rs",
            "server/fixtures/wallet-case.json",
            ".github/workflows/lint.yml",
            "app/detekt.yml",
        ]
        for path in cases:
            with self.subTest(path=path):
                result = validate_pr_policy(
                    proposal(destination="test_or_linter", paths=[path])
                )

                self.assertTrue(result.passed)

    def test_test_or_linter_rejects_production_source_paths(self):
        cases = [
            "app/src/main/kotlin/com/block/Foo.kt",
            "app/src/main/kotlin/com/block/Contest.kt",
            "app/validation/File.kt",
            "core/src/lib.rs",
            "server/foo.rs",
        ]
        for path in cases:
            with self.subTest(path=path):
                result = validate_pr_policy(
                    proposal(destination="test_or_linter", paths=[path])
                )

                self.assertFalse(result.passed)
                self.assertTrue(has_blocking_reason(result, "destination_path_mismatch"))

    def test_test_or_linter_rejects_ai_guidance_trees(self):
        # AI-guidance trees must never qualify as test_or_linter targets, even when a path
        # segment ("fixtures", "tests") matches the test dir-name family.
        cases = [
            ".ai/feedback-loop/fixtures/ci-failure-close-the-loop.json",
            ".ai/skills/foo/fixtures/case.json",
            ".agents/checks/fixtures/case.json",
            ".agents/testdata/sample.txt",
            "app/.ai/tests/case.json",
        ]
        for path in cases:
            with self.subTest(path=path):
                result = validate_pr_policy(
                    proposal(destination="test_or_linter", paths=[path])
                )

                self.assertFalse(result.passed)
                self.assertTrue(has_blocking_reason(result, "destination_path_mismatch"))

    def test_ai_skill_requires_root_skill_directory_paths(self):
        allowed = [
            ".ai/skills/run-tests/SKILL.md",
            ".ai/skills/run-tests/references/usage.md",
        ]
        rejected = [
            ".ai/skills/SKILL.md",
            ".ai/skills/Run_Tests/SKILL.md",
            "app/.ai/skills/run-tests/SKILL.md",
            ".ai/skills/run-tests/",
        ]
        for path in allowed:
            with self.subTest(path=path):
                self.assertTrue(
                    validate_pr_policy(proposal(destination="ai_skill", paths=[path])).passed
                )
        for path in rejected:
            with self.subTest(path=path):
                result = validate_pr_policy(proposal(destination="ai_skill", paths=[path]))

                self.assertFalse(result.passed)

    def test_override_allows_tightly_coupled_policy_blockers(self):
        result = validate_pr_policy(
            proposal(
                destination="agents_check",
                paths=[".agents/checks/example.md", "docs/docs/example.md"],
            ),
            override=PrPolicyOverride(
                approver="reviewer",
                rationale="Docs and check must land together for this rollout.",
            ),
        )

        self.assertTrue(result.passed)
        self.assertTrue(result.override_applied)
        self.assertIn("reviewer:", reviewer_checklist_markdown(result))

    def test_override_does_not_allow_invalid_paths(self):
        result = validate_pr_policy(
            proposal(destination="docs", paths=["../README.md"]),
            override=PrPolicyOverride(
                approver="reviewer",
                rationale="Docs and check must land together for this rollout.",
            ),
        )

        self.assertFalse(result.passed)
        self.assertFalse(result.override_applied)
        self.assertTrue(has_blocking_reason(result, "invalid_path"))

    def test_override_does_not_allow_missing_file_changes(self):
        result = validate_pr_policy(
            proposal(destination="docs", paths=[]),
            override=PrPolicyOverride(
                approver="reviewer",
                rationale="Docs and check must land together for this rollout.",
            ),
        )

        self.assertFalse(result.passed)
        self.assertFalse(result.override_applied)
        self.assertTrue(has_blocking_reason(result, "missing_file_changes"))

    def test_override_does_not_allow_duplicate_normalized_file_changes(self):
        result = validate_pr_policy(
            proposal(destination="docs", paths=["docs/foo.md", "./docs/foo.md"]),
            override=PrPolicyOverride(
                approver="reviewer",
                rationale="Docs and check must land together for this rollout.",
            ),
        )

        self.assertFalse(result.passed)
        self.assertFalse(result.override_applied)
        self.assertTrue(has_blocking_reason(result, "duplicate_normalized_path"))

    def test_ai_agents_md_allows_only_scoped_agents_sources(self):
        result = validate_pr_policy(
            proposal(
                destination="ai_agents_md",
                paths=[".ai/AGENTS.md", "app/.ai/AGENTS.md"],
            )
        )

        self.assertTrue(result.passed)

    def test_ai_agents_md_rejects_skill_source_paths(self):
        result = validate_pr_policy(
            proposal(
                destination="ai_agents_md",
                paths=[".ai/skills/example/SKILL.md"],
            )
        )

        self.assertFalse(result.passed)
        self.assertTrue(has_blocking_reason(result, "destination_path_mismatch"))

    def test_ai_agents_md_rejects_unsupported_agents_scopes(self):
        result = validate_pr_policy(
            proposal(
                destination="ai_agents_md",
                paths=["web/.ai/AGENTS.md"],
            )
        )

        self.assertFalse(result.passed)
        self.assertTrue(has_blocking_reason(result, "destination_path_mismatch"))

    def test_reviewer_checklist_contains_required_review_topics(self):
        result = validate_pr_policy(
            proposal(destination="docs", paths=["docs/docs/example.md"])
        )
        checklist = reviewer_checklist_markdown(result)

        self.assertIn("Evidence quality", checklist)
        self.assertIn("Destination choice", checklist)
        self.assertIn("Eval results", checklist)
        self.assertIn("Noise risk", checklist)
        self.assertIn("Source-of-truth", checklist)
        self.assertIn("Rollback", checklist)


def proposal(
    *,
    destination: str,
    paths: list[str],
    target_artifacts: list[str] | None = None,
) -> Proposal:
    return Proposal(
        cluster=cluster(destination),
        destination=destination,
        summary="Add a specific guardrail for the replayed wallet miss.",
        evidence_urls=["https://github.com/squareup/wallet/pull/123#discussion_r1"],
        confidence=0.9,
        template_title="guardrail",
        target_artifacts=target_artifacts or paths,
        file_changes=[ProposalFileChange(path=path, content="content\n") for path in paths],
        sections={
            "scope": "Apply to wallet files that match the replayed behavior boundary.",
            "validation_steps": "Run the focused validation command for this guardrail.",
        },
        validation_commands=["python -m unittest discover -s tests"],
        replay_cases=["case-1"],
        eval_passed=True,
        eval_state="pr_ready",
    )


def has_blocking_reason(result, prefix: str) -> bool:
    return any(reason.startswith(prefix) for reason in result.blocking_reasons)


def cluster(destination: str) -> Cluster:
    raw = RawSignal(
        kind="review_comment",
        source_id="review_comment:1",
        source_url="https://github.com/squareup/wallet/pull/123#discussion_r1",
        repo="squareup/wallet",
        pr_number=123,
        captured_at="2026-06-03T00:00:00Z",
    )
    signal = NormalizedSignal(
        raw=raw,
        kind=raw.kind,
        source="review_comment",
        source_id=raw.source_id,
        source_url=raw.source_url,
        repo=raw.repo,
        pr_number=raw.pr_number,
        captured_at=raw.captured_at,
        harvest_version="test",
        body="",
        primary_class="miss",
        severity="medium",
        confidence=0.9,
        suggested_destination=destination,
    )
    return Cluster(
        slug="miss:automation:guardrail",
        signals=[signal],
        area="automation",
        severity="medium",
        frequency=1,
        rank=2.0,
        suggested_destination=destination,
        summary="Repeated wallet review miss.",
        source_urls=[raw.source_url],
    )


if __name__ == "__main__":
    unittest.main()

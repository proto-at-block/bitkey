"""Tests for plan reality checks (paths, commands, invented infrastructure, guidance dedup)."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from helpers_reality import FakeRepoReality  # noqa: E402
from feedback_loop.models import ProposalFileChange  # noqa: E402
from feedback_loop.pipeline.reality_preflight import (  # noqa: E402
    guidance_context,
    infrastructure_reality_reasons,
    path_reality_reasons,
    validation_command_assessment,
    validation_command_reasons,
)
from feedback_loop.repo_reality import CheckSummary  # noqa: E402


def repo() -> FakeRepoReality:
    return FakeRepoReality(
        files={
            "app/src/test/kotlin/FooTest.kt": "class FooTest\n",
            "automation/feedback-loop/tests/test_scaffold.py": "",
            "docs/docs/automation/feedback-loop.md": "# Docs\n",
            ".ai/AGENTS.md": "# Root agent rules that apply everywhere.\n",
            "app/.ai/AGENTS.md": "# App-scoped agent rules.\n",
            "tools/ai-context/ai-context-generate.sh": "#!/bin/sh\n",
            "tools/ai-context/ai-context-check.sh": "#!/bin/sh\n",
        },
        dirs={".agents/checks", ".ai/skills/run-tests", "server/tests"},
        references={"server/fixtures/": 1, "known-fixture.json": 1},
        checks=(
            CheckSummary(
                name="smoke",
                description="Verifies the check scaffold works.",
                path=".agents/checks/smoke.md",
            ),
        ),
        skills=("run-tests",),
    )


def changes(*paths: str, mode: str = "create_or_update") -> list[ProposalFileChange]:
    return [ProposalFileChange(path=path, content="content\n", mode=mode) for path in paths]


class TestPathReality(unittest.TestCase):
    def test_existing_files_and_existing_parents_pass(self):
        self.assertEqual(
            path_reality_reasons(
                changes("app/src/test/kotlin/FooTest.kt", "app/src/test/kotlin/NewTest.kt"),
                "test_or_linter",
                repo(),
            ),
            [],
        )

    def test_new_directories_are_rejected(self):
        # The canonical gamed plan shape: a brand-new directory tree
        # (`.ai/feedback-loop/fixtures/...`) for a runner that does not exist.
        reasons = path_reality_reasons(
            changes(".ai/feedback-loop/fixtures/ci-failure-close-the-loop.json"),
            "test_or_linter",
            repo(),
        )

        self.assertEqual(reasons, ["nonexistent_parent_directory"])

    def test_allowed_new_path_families(self):
        self.assertEqual(
            path_reality_reasons(changes(".agents/checks/new-check.md"), "agents_check", repo()),
            [],
        )
        self.assertEqual(
            path_reality_reasons(changes(".ai/skills/new-skill/SKILL.md"), "ai_skill", repo()),
            [],
        )

    def test_agents_check_family_requires_checks_dir(self):
        bare = FakeRepoReality(files={}, dirs=set())

        self.assertEqual(
            path_reality_reasons(changes(".agents/checks/new-check.md"), "agents_check", bare),
            ["nonexistent_parent_directory"],
        )

    def test_unified_diff_requires_existing_target(self):
        reasons = path_reality_reasons(
            changes("docs/docs/automation/missing.md", mode="unified_diff"),
            "docs",
            repo(),
        )

        self.assertEqual(reasons, ["unified_diff_target_missing"])

    def test_ai_skill_requires_skill_md(self):
        # A file inside an existing skill dir is path-valid, but an ai_skill plan that never
        # touches a SKILL.md is not a skill change.
        reasons = path_reality_reasons(
            changes(".ai/skills/run-tests/usage.md"),
            "ai_skill",
            repo(),
        )

        self.assertEqual(reasons, ["missing_skill_md"])


class TestValidationCommandReality(unittest.TestCase):
    def test_vacuous_inspection_only_commands_are_rejected(self):
        # A plan must not "validate" itself with jq syntax checking and rg self-grepping for
        # its own marker strings — inspection proves nothing about whether the check works.
        reasons = validation_command_reasons(
            "test_or_linter",
            [
                "jq empty .ai/feedback-loop/fixtures/ci-failure-close-the-loop.json",
                "rg -n 'expected_assertion' .ai/feedback-loop/fixtures/ci-failure-close-the-loop.json",
            ],
            [".ai/feedback-loop/fixtures/ci-failure-close-the-loop.json"],
            repo(),
        )

        self.assertIn("vacuous_validation_commands", reasons)
        self.assertIn("missing_area_test_runner", reasons)

    def test_area_runner_satisfies_test_or_linter(self):
        cases = [
            ("app/src/test/kotlin/NewTest.kt", "bin/ai-gradle :app:testDebugUnitTest"),
            ("app/src/test/kotlin/NewTest.kt", "gradle --console=plain :app:testDebugUnitTest"),
            ("server/tests/new_test.rs", "cargo test -p wallet-api new_test"),
            ("automation/feedback-loop/tests/test_new.py", "python -m unittest tests.test_new"),
            ("firmware/test/new_test.c", "inv test"),
        ]
        for path, command in cases:
            with self.subTest(path=path):
                self.assertEqual(
                    validation_command_reasons("test_or_linter", [command], [path], repo()),
                    [],
                )

    def test_wrong_area_runner_is_rejected(self):
        reasons = validation_command_reasons(
            "test_or_linter",
            ["cargo test new_test"],
            ["app/src/test/kotlin/NewTest.kt"],
            repo(),
        )

        self.assertEqual(reasons, ["missing_area_test_runner"])

    def test_command_path_tokens_must_exist_or_be_created(self):
        reasons = validation_command_reasons(
            "agents_check",
            ['sq agents review "main...HEAD" && cat .agents/checks/missing-file.md'],
            [".agents/checks/new-check.md"],
            repo(),
        )

        self.assertEqual(reasons, ["nonexistent_validation_path"])

        self.assertEqual(
            validation_command_reasons(
                "agents_check",
                ['sq agents review "main...HEAD" && cat .agents/checks/new-check.md'],
                [".agents/checks/new-check.md"],
                repo(),
            ),
            [],
        )

    def test_invented_runner_heads_are_unknown(self):
        reasons = validation_command_reasons(
            "agents_check",
            ["feedback-loop-eval --route agents_check"],
            [".agents/checks/new-check.md"],
            repo(),
        )

        self.assertIn("unknown_validation_command", reasons)

    def test_cd_prefixed_runner_segments_match(self):
        self.assertEqual(
            validation_command_reasons(
                "test_or_linter",
                ["cd app && bin/ai-gradle :app:testDebugUnitTest"],
                ["app/src/test/kotlin/NewTest.kt"],
                repo(),
            ),
            [],
        )

    def test_cd_prefix_must_target_existing_repo_directory(self):
        cases = [
            "cd missing-app && bin/ai-gradle :app:testDebugUnitTest",
            "cd /tmp && bin/ai-gradle :app:testDebugUnitTest",
            "cd ../app && bin/ai-gradle :app:testDebugUnitTest",
        ]
        for command in cases:
            with self.subTest(command=command):
                reasons = validation_command_reasons(
                    "test_or_linter",
                    [command],
                    ["app/src/test/kotlin/NewTest.kt"],
                    repo(),
                )

                self.assertIn("invalid_cd_validation_directory", reasons)

    def test_gradle_wrapper_is_forbidden_for_app_validation(self):
        reasons = validation_command_reasons(
            "test_or_linter",
            ["./gradlew :app:testDebugUnitTest"],
            ["app/src/test/kotlin/NewTest.kt"],
            repo(),
        )

        self.assertIn("forbidden_gradle_wrapper_command", reasons)
        self.assertIn("missing_area_test_runner", reasons)

    def test_ai_skill_requires_ai_context_commands(self):
        reasons = validation_command_reasons(
            "ai_skill",
            ["./tools/ai-context/ai-context-generate.sh"],
            [".ai/skills/new-skill/SKILL.md"],
            repo(),
        )

        self.assertEqual(reasons, ["missing_ai_context_commands"])

    def test_assessment_classifies_runner_vs_inspection(self):
        assessment = validation_command_assessment(
            "test_or_linter",
            [
                "python -m unittest tests.test_new",
                "rg -n 'pattern' automation/feedback-loop/tests/test_scaffold.py",
            ],
            ["automation/feedback-loop/tests/test_new.py"],
            repo(),
        )

        self.assertEqual(
            [item["kind"] for item in assessment],
            ["runner", "inspection"],
        )


class TestInfrastructureReality(unittest.TestCase):
    def test_unreferenced_new_fixture_is_rejected(self):
        reasons = infrastructure_reality_reasons(
            "test_or_linter",
            changes("server/tests/fixtures/new-case.json"),
            repo(),
        )

        self.assertIn("unreferenced_new_fixture", reasons)

    def test_referenced_fixture_directory_passes(self):
        reasons = infrastructure_reality_reasons(
            "test_or_linter",
            changes(
                "server/fixtures/new-case.json",
                "server/tests/reader_test.rs",
            ),
            repo(),
        )

        self.assertEqual(reasons, [])

    def test_data_only_test_plan_is_rejected(self):
        reasons = infrastructure_reality_reasons(
            "test_or_linter",
            changes("server/fixtures/new-case.json"),
            repo(),
        )

        self.assertEqual(reasons, ["data_only_test_plan"])

    def test_docs_destination_is_exempt(self):
        self.assertEqual(
            infrastructure_reality_reasons(
                "docs",
                changes("docs/docs/automation/new-data.json"),
                repo(),
            ),
            [],
        )


class TestGuidanceContext(unittest.TestCase):
    def test_app_area_includes_scoped_and_root_guidance(self):
        context = guidance_context("app", repo())

        self.assertEqual(context["scoped_agents_md"]["path"], "app/.ai/AGENTS.md")
        self.assertTrue(context["scoped_agents_md"]["exists"])
        self.assertIn("App-scoped", context["scoped_agents_md"]["excerpt"])
        self.assertEqual(context["root_agents_md"]["path"], ".ai/AGENTS.md")
        self.assertEqual(context["existing_agents_checks"][0]["name"], "smoke")
        self.assertEqual(context["existing_ai_skills"], ["run-tests"])

    def test_repo_wide_area_uses_root_guidance_only(self):
        context = guidance_context("repo-wide", repo())

        self.assertEqual(context["scoped_agents_md"]["path"], ".ai/AGENTS.md")
        self.assertNotIn("root_agents_md", context)


if __name__ == "__main__":
    unittest.main()

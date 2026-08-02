"""Tests for promotion templates."""

from __future__ import annotations

import os
import sys
import unittest
from typing import get_args

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.models import Destination  # noqa: E402
from feedback_loop.pipeline.templates import (  # noqa: E402
    REQUIRED_SECTION_KEYS,
    VALIDATION_COMMANDS_BY_DESTINATION,
    promotion_templates,
    template_for_destination,
)


DESTINATIONS = set(get_args(Destination))


class TestPromotionTemplates(unittest.TestCase):
    def test_catalog_covers_every_destination_once(self):
        templates = promotion_templates()

        self.assertEqual({template.destination for template in templates}, DESTINATIONS)
        self.assertEqual(len(templates), len(DESTINATIONS))

    def test_validation_commands_cover_every_template_destination(self):
        self.assertEqual(set(VALIDATION_COMMANDS_BY_DESTINATION), DESTINATIONS)

    def test_each_template_has_required_review_sections(self):
        for template in promotion_templates():
            with self.subTest(destination=template.destination):
                self.assertEqual(template.section_keys(), REQUIRED_SECTION_KEYS)
                self.assertTrue(template.target_artifacts)
                self.assertTrue(template.applies_when)

    def test_evidence_section_prefers_summary_and_links(self):
        for template in promotion_templates():
            evidence = next(
                section for section in template.sections if section.key == "evidence"
            )
            with self.subTest(destination=template.destination):
                self.assertIn("Summarize", evidence.instructions)
                self.assertIn("source URLs", evidence.instructions)
                self.assertIn("Do not paste raw PR comments verbatim", evidence.instructions)

    def test_world_model_template_has_cross_repo_decision_rules(self):
        template = template_for_destination("world_model")

        self.assertTrue(any("durable facts" in rule for rule in template.decision_rules))
        self.assertTrue(any("repo boundaries" in rule for rule in template.decision_rules))
        self.assertTrue(any("research issue" in rule for rule in template.decision_rules))

    def test_template_lookup_returns_catalog_entry(self):
        template = template_for_destination("agents_check")

        self.assertEqual(template.destination, "agents_check")
        self.assertIn(".agents/checks/<check-name>", template.target_artifacts)


if __name__ == "__main__":
    unittest.main()

import unittest
from unittest import mock

from bitkey.git import Git


class TestGitIdentity(unittest.TestCase):
    def test_uses_stable_abbreviation_length(self):
        git = Git.__new__(Git)

        with mock.patch.object(
            git, "_run_git_cmd", return_value="fw-1.2.13-1-g81251adc34"
        ) as run_git:
            self.assertEqual(git._get_identity(), "fw-1.2.13-1-g81251adc34")

        run_git.assert_called_once_with(
            ["describe", "--tags", "--dirty", "--long", "--abbrev=10"]
        )


class TestGitSemverTag(unittest.TestCase):
    def test_skips_unrepresentable_historical_tags(self):
        git = Git.__new__(Git)

        with mock.patch.object(
            git,
            "_run_git_cmd",
            side_effect=["", "fw-100.0.0\nfw-3.0.0-rc.1\nfw-2.0.0"],
        ):
            self.assertEqual(str(git._get_semver_tag()), "2.0.0")


class TestGitHeadTimestamp(unittest.TestCase):
    HEAD_REV = "81251adc34b7fdcff2acc753d1583b77674045b8"

    def git_with_head(self, head_rev=HEAD_REV):
        git = Git.__new__(Git)
        git._info = {"head_rev": head_rev}
        return git

    def test_uses_exact_head_revision(self):
        git = self.git_with_head()

        with mock.patch.object(
            git, "_run_git_cmd", return_value="1787760230"
        ) as run_git:
            self.assertEqual(git.head_timestamp, 1787760230)

        run_git.assert_called_once_with(
            ["show", "-s", "--format=%ct", self.HEAD_REV]
        )

    def test_missing_head_revision_fails(self):
        git = self.git_with_head("")

        with self.assertRaisesRegex(RuntimeError, "HEAD revision is unavailable"):
            _ = git.head_timestamp

    def test_missing_timestamp_fails(self):
        git = self.git_with_head()

        with mock.patch.object(git, "_run_git_cmd", return_value=""):
            with self.assertRaisesRegex(RuntimeError, "Cannot determine HEAD commit timestamp"):
                _ = git.head_timestamp

    def test_invalid_timestamp_fails(self):
        git = self.git_with_head()

        with mock.patch.object(git, "_run_git_cmd", return_value="not-a-timestamp"):
            with self.assertRaisesRegex(RuntimeError, "Invalid HEAD commit timestamp"):
                _ = git.head_timestamp


if __name__ == "__main__":
    unittest.main()

"""Hermetic tests for single-PR structural harvest."""

from __future__ import annotations

import json
import os
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.github import GitHubClient, GitHubError, parse_pull_request_url  # noqa: E402
from feedback_loop.models import RawSignal  # noqa: E402
from feedback_loop.pipeline.harvest import (  # noqa: E402
    HarvestError,
    harvest_pr,
    list_merged_prs,
    normalize_signals,
    parse_unified_diff_hunks,
)


class FakeGitHubClient:
    def __init__(
        self,
        *,
        pull: dict | None = None,
        commits: list[dict] | None = None,
        files: list[dict] | None = None,
        diff: str = "",
        issue_comments: list[dict] | None = None,
        review_comments: list[dict] | None = None,
        reviews: list[dict] | None = None,
        commit_statuses: list[dict] | None = None,
        check_runs: list[dict] | None = None,
        workflow_runs: list[dict] | None = None,
        closed_pulls: list[dict] | None = None,
        closed_pull_pages: list[list[dict]] | None = None,
    ):
        self.pull = pull if pull is not None else merged_pull()
        self.commits = commits if commits is not None else []
        self.files = files if files is not None else []
        self.diff = diff
        self.issue_comment_payloads = issue_comments if issue_comments is not None else []
        self.review_comment_payloads = review_comments if review_comments is not None else []
        self.review_payloads = reviews if reviews is not None else []
        self.commit_status_payloads = commit_statuses if commit_statuses is not None else []
        self.check_run_payloads = check_runs if check_runs is not None else []
        self.workflow_run_payloads = workflow_runs if workflow_runs is not None else []
        self.closed_pull_payloads = closed_pulls if closed_pulls is not None else []
        self.closed_pull_pages = closed_pull_pages
        self.calls: list[str] = []
        self.feedback_max_items: list[tuple[str, int | None]] = []

    def closed_pull_requests(self, repo, *, max_items=None):
        self.calls.append("closed_pull_requests")
        if max_items is None:
            return self.closed_pull_payloads
        return self.closed_pull_payloads[:max_items]

    def closed_pull_requests_page(self, repo, *, page, per_page=100):
        self.calls.append(f"closed_pull_requests_page:{page}")
        if self.closed_pull_pages is not None:
            if page <= 0 or page > len(self.closed_pull_pages):
                return []
            return self.closed_pull_pages[page - 1]
        start = (page - 1) * per_page
        end = start + per_page
        return self.closed_pull_payloads[start:end]

    def pull_metadata(self, ref):
        self.calls.append("pull_metadata")
        return self.pull

    def pull_commits(self, ref):
        self.calls.append("pull_commits")
        return self.commits

    def pull_files(self, ref):
        self.calls.append("pull_files")
        return self.files

    def pull_diff(self, ref):
        self.calls.append("pull_diff")
        return self.diff

    def issue_comments(self, ref, *, max_items=None):
        self.calls.append("issue_comments")
        self.feedback_max_items.append(("issue_comments", max_items))
        return self._feedback_payloads(
            self.issue_comment_payloads,
            max_items=max_items,
        )

    def pull_review_comments(self, ref, *, max_items=None):
        self.calls.append("pull_review_comments")
        self.feedback_max_items.append(("pull_review_comments", max_items))
        return self._feedback_payloads(
            self.review_comment_payloads,
            max_items=max_items,
        )

    def pull_reviews(self, ref, *, max_items=None):
        self.calls.append("pull_reviews")
        self.feedback_max_items.append(("pull_reviews", max_items))
        return self._feedback_payloads(
            self.review_payloads,
            max_items=max_items,
        )

    def _feedback_payloads(self, payloads, *, max_items=None):
        if max_items is None:
            return payloads
        if max_items <= 0:
            return []
        return payloads[-max_items:]

    def commit_statuses(self, ref, sha, *, max_items=None):
        self.calls.append("commit_statuses")
        if max_items is None:
            return self.commit_status_payloads
        return self.commit_status_payloads[:max_items]

    def check_runs(self, ref, sha, *, max_items=None):
        self.calls.append("check_runs")
        if max_items is None:
            return self.check_run_payloads
        return self.check_run_payloads[:max_items]

    def workflow_runs_for_sha(self, ref, sha, *, max_items=None):
        self.calls.append("workflow_runs_for_sha")
        if max_items is None:
            return self.workflow_run_payloads
        return self.workflow_run_payloads[:max_items]


class RecordingGitHubClient(GitHubClient):
    def __init__(self, responses: list[str]):
        self.responses = responses
        self.calls: list[list[str]] = []

    def _run_gh(self, args: list[str]) -> str:
        self.calls.append(args)
        return self.responses.pop(0)


def gh_include_response(body: list[dict], *, last_page: int | None = None) -> str:
    headers = ["HTTP/2 200"]
    if last_page is not None:
        next_link = (
            "<https://api.github.com/repos/squareup/wallet/issues/123/comments"
            '?per_page=100&page=2>; rel="next"'
        )
        last_link = (
            "<https://api.github.com/repos/squareup/wallet/issues/123/comments"
            f'?per_page=100&page={last_page}>; rel="last"'
        )
        headers.append(f"link: {next_link}, {last_link}")
    return "\r\n".join(headers) + "\r\n\r\n" + json.dumps(body)


def merged_pull(**updates) -> dict:
    pull = {
        "number": 123,
        "html_url": "https://github.com/squareup/wallet/pull/123",
        "title": "Tighten feedback-loop harvest",
        "body": "PR body text",
        "user": {"login": "octocat"},
        "author_association": "MEMBER",
        "labels": [{"name": "automation"}, {"name": "wallet"}],
        "requested_reviewers": [{"login": "reviewer"}],
        "requested_teams": [{"slug": "mobile"}],
        "head": {
            "ref": "feature/bkw-78",
            "sha": "headsha",
            "repo": {"full_name": "squareup/wallet"},
        },
        "base": {
            "ref": "main",
            "sha": "basesha",
            "repo": {"full_name": "squareup/wallet"},
        },
        "merge_commit_sha": "mergesha",
        "created_at": "2026-05-01T01:02:03Z",
        "updated_at": "2026-05-02T01:02:03Z",
        "closed_at": "2026-05-03T01:02:03Z",
        "merged_at": "2026-05-03T01:02:03Z",
        "merged_by": {"login": "merger"},
        "state": "closed",
    }
    pull.update(updates)
    return pull


def issue_comment_payload(**updates) -> dict:
    comment_id = updates.get("id", 987)
    comment = {
        "id": comment_id,
        "html_url": f"https://github.com/squareup/wallet/pull/123#issuecomment-{comment_id}",
        "user": {"login": "reviewer"},
        "author_association": "MEMBER",
        "created_at": "2026-05-04T01:02:03Z",
        "body": "Please add a regression test.",
    }
    comment.update(updates)
    return comment


def review_comment_payload(**updates) -> dict:
    comment_id = updates.get("id", 111)
    comment = {
        "id": comment_id,
        "html_url": f"https://github.com/squareup/wallet/pull/123#discussion_r{comment_id}",
        "user": {"login": "reviewer"},
        "author_association": "COLLABORATOR",
        "created_at": "2026-05-04T02:00:00Z",
        "body": "This should use the helper.",
        "path": "automation/feedback-loop/feedback_loop/pipeline/harvest.py",
        "line": 42,
        "original_line": 40,
        "pull_request_review_id": 222,
    }
    comment.update(updates)
    return comment


def submitted_review_payload(**updates) -> dict:
    review_id = updates.get("id", 333)
    review = {
        "id": review_id,
        "html_url": f"https://github.com/squareup/wallet/pull/123#pullrequestreview-{review_id}",
        "user": {"login": "reviewer"},
        "author_association": "MEMBER",
        "created_at": "2026-05-04T03:00:00Z",
        "submitted_at": "2026-05-04T03:05:00Z",
        "body": "Requesting changes for the missing validation.",
        "state": "CHANGES_REQUESTED",
    }
    review.update(updates)
    return review


def codex_security_review_comment_payload(**updates) -> dict:
    comment_id = updates.pop("id", 444)
    body = updates.pop(
        "body",
        """<!-- codex-security-review -->
## Codex Security Review

> **Scope summary**
> - Reviewed pull request diff only (`basesha...headsha`, exact PR three-dot diff)
> - Model: gpt-5.4

### Findings

#### [HIGH] Missing bound
- **Location**: [`automation/feedback-loop/feedback_loop/pipeline/harvest.py:12`](https://github.com/squareup/wallet/blob/headsha/automation/feedback-loop/feedback_loop/pipeline/harvest.py#L12)
- **Recommendation**: Add a bound.

<sub>Generated by [Codex Security Review](https://github.com/openai/codex-action) |
[Review workflow run](https://github.com/squareup/wallet/actions/runs/123456)</sub>""",
    )
    return issue_comment_payload(
        id=comment_id,
        user={"login": "github-actions[bot]"},
        body=body,
        **updates,
    )


def commit_status_payload(**updates) -> dict:
    status_id = updates.get("id", 1001)
    status = {
        "id": status_id,
        "context": "buildkite/wallet/pr",
        "state": "failure",
        "target_url": "https://buildkite.com/runway/wallet/builds/1",
        "description": "Build failed",
        "created_at": "2026-05-04T05:00:00Z",
        "updated_at": "2026-05-04T05:10:00Z",
        "creator": {"login": "buildkite[bot]"},
    }
    status.update(updates)
    return status


def check_run_payload(**updates) -> dict:
    run_id = updates.get("id", 2001)
    run = {
        "id": run_id,
        "name": "feedback-loop / test",
        "status": "completed",
        "conclusion": "failure",
        "html_url": "https://github.com/squareup/wallet/actions/runs/2001/job/1",
        "details_url": "https://github.com/squareup/wallet/actions/runs/2001/job/1",
        "started_at": "2026-05-04T06:00:00Z",
        "completed_at": "2026-05-04T06:10:00Z",
        "app": {"slug": "github-actions", "name": "GitHub Actions"},
    }
    run.update(updates)
    return run


def workflow_run_payload(**updates) -> dict:
    run_id = updates.get("id", 3001)
    run = {
        "id": run_id,
        "name": "Codex Security Review",
        "status": "completed",
        "conclusion": "failure",
        "html_url": "https://github.com/squareup/wallet/actions/runs/3001",
        "event": "pull_request",
        "created_at": "2026-05-04T07:00:00Z",
        "run_started_at": "2026-05-04T07:02:00Z",
        "updated_at": "2026-05-04T07:15:00Z",
        "actor": {"login": "github-actions[bot]"},
    }
    run.update(updates)
    return run


def closed_pull_payload(**updates) -> dict:
    number = updates.get("number", 123)
    pull = {
        "number": number,
        "html_url": f"https://github.com/squareup/wallet/pull/{number}",
        "merged_at": "2026-06-02T12:00:00Z",
    }
    pull.update(updates)
    return pull


class TestPullUrlParsing(unittest.TestCase):
    def test_parse_full_github_pull_url(self):
        ref = parse_pull_request_url("https://github.com/squareup/wallet/pull/123")
        self.assertEqual(ref.owner, "squareup")
        self.assertEqual(ref.name, "wallet")
        self.assertEqual(ref.hostname, "github.com")
        self.assertEqual(ref.repo, "squareup/wallet")
        self.assertEqual(ref.number, 123)

    def test_rejects_non_pull_url(self):
        with self.assertRaises(GitHubError):
            parse_pull_request_url("https://github.com/squareup/wallet/issues/123")

    def test_rejects_repo_mismatch(self):
        with self.assertRaises(HarvestError) as ctx:
            harvest_pr(
                RunConfig(repo="squareup/wallet"),
                "https://github.com/squareup/not-wallet/pull/123",
                client=FakeGitHubClient(),
            )
        self.assertIn("does not match configured repo", str(ctx.exception))


class TestGitHubClient(unittest.TestCase):
    def test_pins_gh_api_calls_to_parsed_hostname(self):
        ref = parse_pull_request_url("https://github.com/squareup/wallet/pull/123")
        client = RecordingGitHubClient([
            "{}",
            "[]",
            "[]",
            "diff --git a/file.txt b/file.txt\n",
        ])

        with patch.dict(os.environ, {"GH_HOST": "github.example.invalid"}):
            client.pull_metadata(ref)
            client.pull_commits(ref)
            client.pull_files(ref)
            client.pull_diff(ref)

        self.assertEqual(len(client.calls), 4)
        for args in client.calls:
            self.assertEqual(args[0:3], ["api", "--hostname", "github.com"])

    def test_comment_and_review_fetchers_paginate_with_pinned_hostname(self):
        ref = parse_pull_request_url("https://github.com/squareup/wallet/pull/123")
        full_page = [{"id": idx} for idx in range(100)]
        tail_page = [{"id": 100}]
        client = RecordingGitHubClient([
            json.dumps(full_page),
            json.dumps(tail_page),
            json.dumps(full_page),
            json.dumps(tail_page),
            json.dumps(full_page),
            json.dumps(tail_page),
        ])

        issue_comments = client.issue_comments(ref)
        review_comments = client.pull_review_comments(ref)
        reviews = client.pull_reviews(ref)

        self.assertEqual(len(issue_comments), 101)
        self.assertEqual(len(review_comments), 101)
        self.assertEqual(len(reviews), 101)
        self.assertEqual(
            [call[3] for call in client.calls],
            [
                "repos/squareup/wallet/issues/123/comments",
                "repos/squareup/wallet/issues/123/comments",
                "repos/squareup/wallet/pulls/123/comments",
                "repos/squareup/wallet/pulls/123/comments",
                "repos/squareup/wallet/pulls/123/reviews",
                "repos/squareup/wallet/pulls/123/reviews",
            ],
        )
        for idx, args in enumerate(client.calls):
            self.assertEqual(args[0:3], ["api", "--hostname", "github.com"])
            self.assertIn("per_page=100", args)
            self.assertIn(f"page={(idx % 2) + 1}", args)

    def test_comment_fetcher_stops_at_max_items(self):
        ref = parse_pull_request_url("https://github.com/squareup/wallet/pull/123")
        client = RecordingGitHubClient([json.dumps([{"id": idx} for idx in range(100)])])

        issue_comments = client.issue_comments(ref, max_items=50)

        self.assertEqual(len(issue_comments), 50)
        self.assertEqual(len(client.calls), 1)
        self.assertIn("page=1", client.calls[0])

    def test_unbounded_comment_fetcher_returns_raw_payloads(self):
        ref = parse_pull_request_url("https://github.com/squareup/wallet/pull/123")
        client = RecordingGitHubClient([
            json.dumps([
                {"id": 1, "body": "first"},
                {"id": 1, "body": "duplicate"},
                {"id": True, "body": "bool id one"},
                {"id": True, "body": "bool id two"},
            ]),
        ])

        issue_comments = client.issue_comments(ref)

        self.assertEqual(
            [comment["body"] for comment in issue_comments],
            ["first", "duplicate", "bool id one", "bool id two"],
        )
        self.assertEqual(len(client.calls), 1)

    def test_comment_fetcher_counts_raw_items_before_stopping(self):
        ref = parse_pull_request_url("https://github.com/squareup/wallet/pull/123")
        client = RecordingGitHubClient([
            gh_include_response([{"id": 1} for _ in range(99)] + [{"id": 2}], last_page=2),
            json.dumps([{"id": 3}]),
        ])

        issue_comments = client.issue_comments(ref, max_items=3)

        self.assertEqual([comment["id"] for comment in issue_comments], [1, 2, 3])
        self.assertEqual(len(client.calls), 2)
        self.assertIn("page=1", client.calls[0])
        self.assertIn("page=2", client.calls[1])

    def test_comment_fetcher_with_max_items_reads_newest_tail_pages(self):
        ref = parse_pull_request_url("https://github.com/squareup/wallet/pull/123")
        client = RecordingGitHubClient([
            gh_include_response([{"id": idx} for idx in range(100)], last_page=5),
            json.dumps([{"id": 400}, {"id": 401}]),
            json.dumps([{"id": idx} for idx in range(300, 400)]),
        ])

        issue_comments = client.issue_comments(ref, max_items=3)

        self.assertEqual([comment["id"] for comment in issue_comments], [399, 400, 401])
        self.assertEqual(len(client.calls), 3)
        self.assertIn("page=1", client.calls[0])
        self.assertIn("--include", client.calls[0])
        self.assertIn("page=5", client.calls[1])
        self.assertIn("page=4", client.calls[2])

    def test_comment_fetcher_counts_malformed_ids_toward_max_items(self):
        ref = parse_pull_request_url("https://github.com/squareup/wallet/pull/123")
        client = RecordingGitHubClient([
            gh_include_response([{"id": idx} for idx in range(100)], last_page=5),
            json.dumps([
                {"id": True, "body": "bool id"},
                {"body": "missing id"},
                {"id": 401, "body": "valid id"},
            ]),
        ])

        issue_comments = client.issue_comments(ref, max_items=2)

        self.assertEqual([comment.get("body") for comment in issue_comments], [
            "missing id",
            "valid id",
        ])
        self.assertEqual(len(client.calls), 2)
        self.assertIn("page=1", client.calls[0])
        self.assertIn("page=5", client.calls[1])

    def test_comment_fetcher_has_raw_page_ceiling(self):
        ref = parse_pull_request_url("https://github.com/squareup/wallet/pull/123")
        client = RecordingGitHubClient([
            gh_include_response([{"id": idx} for idx in range(100)], last_page=5),
            json.dumps([{"id": idx} for idx in range(400, 500)]),
            json.dumps([{"id": idx} for idx in range(300, 400)]),
            json.dumps([{"id": idx} for idx in range(200, 300)]),
        ])

        issue_comments = client.issue_comments(ref, max_items=201)

        self.assertEqual(issue_comments[0]["id"], 299)
        self.assertEqual(issue_comments[-1]["id"], 499)
        self.assertEqual(len(issue_comments), 201)
        self.assertEqual(len(client.calls), 4)
        self.assertIn("page=1", client.calls[0])
        self.assertIn("page=5", client.calls[1])
        self.assertIn("page=4", client.calls[2])
        self.assertIn("page=3", client.calls[3])

    def test_comment_fetcher_returns_raw_duplicates(self):
        ref = parse_pull_request_url("https://github.com/squareup/wallet/pull/123")
        client = RecordingGitHubClient([
            json.dumps([
                {"id": 1, "body": "first"},
                {"id": 1, "body": "duplicate"},
            ]),
        ])

        issue_comments = client.issue_comments(ref, max_items=2)

        self.assertEqual([comment["body"] for comment in issue_comments], [
            "first",
            "duplicate",
        ])

    def test_comment_fetcher_counts_bool_ids_as_raw_items(self):
        ref = parse_pull_request_url("https://github.com/squareup/wallet/pull/123")
        client = RecordingGitHubClient([
            gh_include_response([{"id": 1}], last_page=2),
            json.dumps([{"id": True}, {"id": 2}]),
        ])

        issue_comments = client.issue_comments(ref, max_items=2)

        self.assertEqual([comment["id"] for comment in issue_comments], [True, 2])
        self.assertEqual(len(client.calls), 2)

    def test_check_fetchers_use_head_sha_and_paginated_apis(self):
        ref = parse_pull_request_url("https://github.com/squareup/wallet/pull/123")
        client = RecordingGitHubClient([
            json.dumps({"statuses": [{"id": 1}]}),
            json.dumps({"check_runs": [{"id": 2}]}),
            json.dumps({"workflow_runs": [{"id": 3}]}),
        ])

        statuses = client.commit_statuses(ref, "headsha")
        check_runs = client.check_runs(ref, "headsha")
        workflow_runs = client.workflow_runs_for_sha(ref, "headsha")

        self.assertEqual(statuses, [{"id": 1}])
        self.assertEqual(check_runs, [{"id": 2}])
        self.assertEqual(workflow_runs, [{"id": 3}])
        self.assertEqual(client.calls[0][0:4], [
            "api",
            "--hostname",
            "github.com",
            "repos/squareup/wallet/commits/headsha/status",
        ])
        self.assertEqual(client.calls[1][3], "repos/squareup/wallet/commits/headsha/check-runs")
        self.assertEqual(client.calls[2][3], "repos/squareup/wallet/actions/runs")
        self.assertIn("head_sha=headsha", client.calls[2])

    def test_commit_statuses_paginates_before_applying_limit(self):
        ref = parse_pull_request_url("https://github.com/squareup/wallet/pull/123")
        client = RecordingGitHubClient([
            json.dumps({"statuses": [{"id": status_id} for status_id in range(100)]}),
            json.dumps({"statuses": [{"id": 200, "state": "failure"}]}),
        ])

        statuses = client.commit_statuses(ref, "headsha", max_items=101)

        self.assertEqual(len(statuses), 101)
        self.assertEqual(statuses[-1], {"id": 200, "state": "failure"})
        self.assertEqual(len(client.calls), 2)
        first_call = client.calls[0]
        second_call = client.calls[1]
        self.assertIn("per_page=100", first_call)
        self.assertIn("page=1", first_call)
        self.assertIn("per_page=100", second_call)
        self.assertIn("page=2", second_call)

    def test_commit_statuses_dedupes_by_id_across_pages_before_limit(self):
        ref = parse_pull_request_url("https://github.com/squareup/wallet/pull/123")
        client = RecordingGitHubClient([
            json.dumps({"statuses": [{"id": status_id} for status_id in range(100)]}),
            json.dumps({"statuses": [{"id": 99}, {"id": 200, "state": "failure"}]}),
        ])

        statuses = client.commit_statuses(ref, "headsha", max_items=101)

        self.assertEqual(len(statuses), 101)
        self.assertEqual(statuses[-1], {"id": 200, "state": "failure"})

    def test_closed_pull_requests_uses_closed_updated_pagination(self):
        client = RecordingGitHubClient([json.dumps([{"number": 123}])])

        pulls = client.closed_pull_requests("squareup/wallet", max_items=1)

        self.assertEqual(pulls, [{"number": 123}])
        self.assertEqual(len(client.calls), 1)
        call = client.calls[0]
        self.assertEqual(call[0:4], [
            "api",
            "--hostname",
            "github.com",
            "repos/squareup/wallet/pulls",
        ])
        self.assertIn("state=closed", call)
        self.assertIn("sort=updated", call)
        self.assertIn("direction=desc", call)

    def test_closed_pull_requests_page_uses_closed_updated_pagination(self):
        client = RecordingGitHubClient([json.dumps([{"number": 123}])])

        pulls = client.closed_pull_requests_page("squareup/wallet", page=3, per_page=50)

        self.assertEqual(pulls, [{"number": 123}])
        self.assertEqual(len(client.calls), 1)
        call = client.calls[0]
        self.assertEqual(call[0:4], [
            "api",
            "--hostname",
            "github.com",
            "repos/squareup/wallet/pulls",
        ])
        self.assertIn("per_page=50", call)
        self.assertIn("page=3", call)
        self.assertIn("state=closed", call)
        self.assertIn("sort=updated", call)
        self.assertIn("direction=desc", call)


class TestListMergedPrs(unittest.TestCase):
    def test_filters_backfill_window_and_limit(self):
        client = FakeGitHubClient(
            closed_pulls=[
                closed_pull_payload(number=101, merged_at="2026-06-02T12:00:00Z"),
                closed_pull_payload(number=102, merged_at=None),
                closed_pull_payload(number=103, merged_at="2026-06-01T23:59:59Z"),
                closed_pull_payload(number=104, merged_at="2026-06-03T00:00:00Z"),
                closed_pull_payload(number=105, merged_at="2026-06-02T09:00:00Z"),
            ]
        )

        pr_urls = list_merged_prs(
            RunConfig(
                repo="squareup/wallet",
                since="2026-06-02",
                until="2026-06-02",
                limit=2,
            ),
            client=client,
        )

        self.assertEqual(pr_urls, [
            "https://github.com/squareup/wallet/pull/101",
            "https://github.com/squareup/wallet/pull/105",
        ])
        self.assertEqual(client.calls, ["closed_pull_requests_page:1"])

    def test_backfill_uses_fallback_url_when_html_url_missing(self):
        client = FakeGitHubClient(
            closed_pulls=[
                closed_pull_payload(number=106, html_url=""),
            ]
        )

        pr_urls = list_merged_prs(RunConfig(repo="squareup/wallet", limit=1), client=client)

        self.assertEqual(pr_urls, ["https://github.com/squareup/wallet/pull/106"])

    def test_backfill_pages_past_first_thousand_closed_prs(self):
        closed_pull_pages = [
            [
                closed_pull_payload(
                    number=10_000 + page_index * 100 + item_index,
                    merged_at=None,
                    updated_at="2026-06-04T00:00:00Z",
                )
                for item_index in range(100)
            ]
            for page_index in range(11)
        ]
        closed_pull_pages.append([
            closed_pull_payload(
                number=999,
                merged_at="2026-06-02T12:00:00Z",
                updated_at="2026-06-02T12:00:00Z",
            )
        ])
        client = FakeGitHubClient(closed_pull_pages=closed_pull_pages)

        pr_urls = list_merged_prs(
            RunConfig(
                repo="squareup/wallet",
                since="2026-06-02",
                until="2026-06-02",
                limit=1,
            ),
            client=client,
        )

        self.assertEqual(pr_urls, ["https://github.com/squareup/wallet/pull/999"])
        self.assertIn("closed_pull_requests_page:12", client.calls)

    def test_backfill_stops_when_updated_stream_is_older_than_since(self):
        old_page = [
            closed_pull_payload(
                number=20_000 + item_index,
                merged_at="2026-05-31T12:00:00Z",
                updated_at="2026-05-31T12:00:00Z",
            )
            for item_index in range(100)
        ]
        client = FakeGitHubClient(
            closed_pull_pages=[
                old_page,
                [
                    closed_pull_payload(
                        number=201,
                        merged_at="2026-06-02T12:00:00Z",
                        updated_at="2026-06-02T12:00:00Z",
                    )
                ],
            ]
        )

        pr_urls = list_merged_prs(
            RunConfig(repo="squareup/wallet", since="2026-06-02", limit=1),
            client=client,
        )

        self.assertEqual(pr_urls, [])
        self.assertEqual(client.calls, ["closed_pull_requests_page:1"])

    def test_rejects_invalid_backfill_window(self):
        with self.assertRaisesRegex(HarvestError, "--since must be before --until"):
            list_merged_prs(
                RunConfig(repo="squareup/wallet", since="2026-06-03", until="2026-06-02"),
                client=FakeGitHubClient(),
            )


class TestHarvestPr(unittest.TestCase):
    def test_rejects_non_merged_pr_before_fetching_children(self):
        client = FakeGitHubClient(pull=merged_pull(merged_at=None))

        with self.assertRaises(HarvestError) as ctx:
            harvest_pr(
                RunConfig(repo="squareup/wallet"),
                "https://github.com/squareup/wallet/pull/123",
                client=client,
            )

        self.assertIn("is not merged", str(ctx.exception))
        self.assertEqual(client.calls, ["pull_metadata"])

    def test_maps_pr_metadata_signal(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(),
        )

        self.assertEqual(len(signals), 1)
        signal = signals[0]
        self.assertEqual(signal.kind, "pr_metadata")
        self.assertEqual(signal.source_id, "pr:squareup/wallet#123")
        self.assertEqual(signal.source_url, "https://github.com/squareup/wallet/pull/123")
        self.assertEqual(signal.author, "octocat")
        self.assertEqual(signal.created_at, "2026-05-01T01:02:03Z")
        self.assertEqual(signal.body, "PR body text")
        self.assertTrue(signal.captured_at.endswith("Z"))
        self.assertEqual(signal.raw["title"], "Tighten feedback-loop harvest")
        self.assertEqual(signal.raw["labels"], ["automation", "wallet"])
        self.assertEqual(signal.raw["requested_reviewers"], ["reviewer"])
        self.assertEqual(signal.raw["requested_teams"], ["mobile"])
        self.assertEqual(signal.raw["branches"]["head"]["sha"], "headsha")
        self.assertEqual(signal.raw["shas"]["merge_commit"], "mergesha")
        self.assertEqual(signal.raw["timestamps"]["merged_at"], "2026-05-03T01:02:03Z")
        self.assertEqual(signal.raw["areas"], [])
        self.assertEqual(signal.raw["commits"], {
            "reported": 0,
            "fetched": 0,
            "truncated": False,
        })
        self.assertEqual(signal.raw["changed_files"], {
            "reported": 0,
            "fetched": 0,
            "truncated": False,
        })
        self.assertEqual(signal.raw["issue_comments"]["fetched"], 0)
        self.assertEqual(signal.raw["review_comments"]["fetched"], 0)
        self.assertEqual(signal.raw["reviews"]["fetched"], 0)
        self.assertFalse(signal.raw["feedback_body_bytes"]["truncated"])

    def test_boolean_count_fields_are_not_treated_as_ints(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(pull=merged_pull(commits=True, changed_files=True)),
        )

        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        self.assertEqual(metadata.raw["commits"], {
            "reported": 0,
            "fetched": 0,
            "truncated": False,
        })
        self.assertEqual(metadata.raw["changed_files"], {
            "reported": 0,
            "fetched": 0,
            "truncated": False,
        })

    def test_maps_issue_comment_signals(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(issue_comments=[issue_comment_payload()]),
        )

        comments = [signal for signal in signals if signal.kind == "issue_comment"]
        self.assertEqual(len(comments), 1)
        signal = comments[0]
        self.assertEqual(signal.source_id, "issue_comment:squareup/wallet#123:987")
        self.assertEqual(
            signal.source_url,
            "https://github.com/squareup/wallet/pull/123#issuecomment-987",
        )
        self.assertEqual(signal.author, "reviewer")
        self.assertEqual(signal.author_association, "MEMBER")
        self.assertEqual(signal.created_at, "2026-05-04T01:02:03Z")
        self.assertEqual(signal.body, "Please add a regression test.")
        self.assertIsNone(signal.path)
        self.assertIsNone(signal.line)
        self.assertFalse(signal.is_bot)
        self.assertEqual(signal.raw["kind"], "issue_comment")
        self.assertEqual(signal.raw["id"], "987")
        self.assertEqual(
            signal.raw["url"],
            "https://github.com/squareup/wallet/pull/123#issuecomment-987",
        )
        self.assertIsNone(signal.raw["path"])
        self.assertIsNone(signal.raw["line"])
        self.assertEqual(len({item.captured_at for item in signals}), 1)

    def test_maps_review_comment_signals_with_original_line_fallback(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                review_comments=[
                    review_comment_payload(),
                    review_comment_payload(
                        id=112,
                        created_at="2026-05-04T02:05:00Z",
                        body="This was on the original diff line.",
                        path="automation/feedback-loop/README.md",
                        line=None,
                        original_line=7,
                        pull_request_review_id=None,
                    ),
                ]
            ),
        )

        comments = [signal for signal in signals if signal.kind == "review_comment"]
        self.assertEqual(len(comments), 2)
        self.assertEqual(comments[0].source_id, "review_comment:squareup/wallet#123:111")
        self.assertEqual(
            comments[0].path,
            "automation/feedback-loop/feedback_loop/pipeline/harvest.py",
        )
        self.assertEqual(comments[0].line, 42)
        self.assertEqual(comments[0].raw["kind"], "review_comment")
        self.assertEqual(comments[0].raw["line"], 42)
        self.assertEqual(comments[0].raw["github_line"], 42)
        self.assertEqual(comments[0].raw["original_line"], 40)
        self.assertEqual(comments[0].raw["in_reply_to_id"], "")
        self.assertEqual(comments[0].raw["pull_request_review_id"], "222")
        self.assertEqual(comments[1].source_id, "review_comment:squareup/wallet#123:112")
        self.assertEqual(comments[1].path, "automation/feedback-loop/README.md")
        self.assertEqual(comments[1].line, 7)
        self.assertEqual(comments[1].raw["line"], 7)
        self.assertIsNone(comments[1].raw["github_line"])

    def test_maps_review_signals_with_state_and_submitted_timestamp(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                reviews=[
                    submitted_review_payload(),
                    submitted_review_payload(
                        id=334,
                        created_at="2026-05-04T04:00:00Z",
                        submitted_at=None,
                        body="",
                        state="APPROVED",
                    ),
                ]
            ),
        )

        reviews = [signal for signal in signals if signal.kind == "review"]
        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        self.assertEqual(len(reviews), 1)
        self.assertEqual(reviews[0].source_id, "review:squareup/wallet#123:333")
        self.assertEqual(
            reviews[0].source_url,
            "https://github.com/squareup/wallet/pull/123#pullrequestreview-333",
        )
        self.assertEqual(reviews[0].created_at, "2026-05-04T03:05:00Z")
        self.assertEqual(reviews[0].body, "Requesting changes for the missing validation.")
        self.assertEqual(reviews[0].raw["kind"], "review")
        self.assertEqual(
            reviews[0].raw["url"],
            "https://github.com/squareup/wallet/pull/123#pullrequestreview-333",
        )
        self.assertEqual(reviews[0].raw["state"], "CHANGES_REQUESTED")
        self.assertIsNone(reviews[0].raw["path"])
        self.assertIsNone(reviews[0].raw["line"])
        self.assertTrue(reviews[0].raw["is_trusted_author"])
        self.assertTrue(reviews[0].raw["is_processable"])
        self.assertEqual(reviews[0].raw["drop_reasons"], [])
        self.assertEqual(metadata.raw["reviews"]["reported"], 2)
        self.assertEqual(metadata.raw["reviews"]["processable"], 1)
        self.assertEqual(metadata.raw["reviews"]["dropped"], 1)

    def test_drops_unprocessable_feedback_from_signal_stream(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                issue_comments=[
                    issue_comment_payload(
                        id=444,
                        user={"login": "drive-by"},
                        author_association="CONTRIBUTOR",
                        body="Untrusted raw evidence.",
                    ),
                    issue_comment_payload(
                        id=445,
                        user={"login": "trusted-reviewer"},
                        author_association="MEMBER",
                        body="Trusted signal.",
                    ),
                    issue_comment_payload(
                        id=446,
                        user={"login": "github-actions[bot]"},
                        author_association="NONE",
                        body="Trusted bot signal.",
                    ),
                    issue_comment_payload(
                        id=447,
                        user={"login": "spoof-bot"},
                        author_association="NONE",
                        body="Bot suffix should not grant trust.",
                    ),
                    issue_comment_payload(
                        id=448,
                        user={"login": "Copilot"},
                        author_association="NONE",
                        body="Explicitly allowed bot signal.",
                    ),
                ],
            ),
        )

        comments = [signal for signal in signals if signal.kind == "issue_comment"]
        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        comments_by_author = {comment.author: comment for comment in comments}
        self.assertEqual(metadata.raw["issue_comments"]["reported"], 5)
        self.assertEqual(metadata.raw["issue_comments"]["processable"], 3)
        self.assertEqual(metadata.raw["issue_comments"]["dropped"], 2)
        self.assertFalse(metadata.raw["issue_comments"]["truncated"])
        self.assertNotIn("drive-by", comments_by_author)
        self.assertNotIn("spoof-bot", comments_by_author)
        self.assertTrue(comments_by_author["trusted-reviewer"].raw["is_trusted_author"])
        self.assertTrue(comments_by_author["github-actions[bot]"].raw["is_trusted_author"])
        self.assertTrue(comments_by_author["Copilot"].raw["is_trusted_author"])
        self.assertTrue(comments_by_author["Copilot"].is_bot)

    def test_drops_non_processable_reviews_from_signal_stream(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                reviews=[
                    submitted_review_payload(id=333, body="", state="APPROVED"),
                    submitted_review_payload(
                        id=334,
                        body="Superseded request.",
                        state="DISMISSED",
                    ),
                    submitted_review_payload(
                        id=335,
                        body="Current requested change.",
                        state="CHANGES_REQUESTED",
                    ),
                ],
            ),
        )

        reviews = [signal for signal in signals if signal.kind == "review"]
        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        self.assertEqual([review.body for review in reviews], ["Current requested change."])
        self.assertEqual(metadata.raw["reviews"]["reported"], 3)
        self.assertEqual(metadata.raw["reviews"]["processable"], 1)
        self.assertEqual(metadata.raw["reviews"]["dropped"], 2)
        self.assertFalse(metadata.raw["reviews"]["truncated"])
        self.assertEqual(reviews[0].raw["drop_reasons"], [])
        self.assertTrue(reviews[0].raw["is_processable"])

    def test_drops_review_comments_from_dismissed_reviews(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                review_comments=[
                    review_comment_payload(
                        id=111,
                        body="Stale inline feedback.",
                        pull_request_review_id=333,
                    ),
                    review_comment_payload(
                        id=112,
                        body="Current inline feedback.",
                        pull_request_review_id=334,
                    ),
                ],
                reviews=[
                    submitted_review_payload(
                        id=333,
                        body="Stale request.",
                        state="DISMISSED",
                    ),
                    submitted_review_payload(
                        id=334,
                        body="Current request.",
                        state="CHANGES_REQUESTED",
                    ),
                ],
            ),
        )

        review_comments = [signal for signal in signals if signal.kind == "review_comment"]
        reviews = [signal for signal in signals if signal.kind == "review"]
        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        self.assertEqual([comment.body for comment in review_comments], [
            "Current inline feedback."
        ])
        self.assertEqual([review.body for review in reviews], ["Current request."])
        self.assertEqual(metadata.raw["review_comments"]["reported"], 2)
        self.assertEqual(metadata.raw["review_comments"]["processable"], 1)
        self.assertEqual(metadata.raw["review_comments"]["dropped"], 1)
        self.assertEqual(metadata.raw["reviews"]["reported"], 2)
        self.assertEqual(metadata.raw["reviews"]["processable"], 1)
        self.assertEqual(metadata.raw["reviews"]["dropped"], 1)

    def test_selects_processable_review_comments_after_dismissal_filter(self):
        with patch("feedback_loop.pipeline.harvest.MAX_FEEDBACK_ITEMS_PER_KIND", 1):
            signals = harvest_pr(
                RunConfig(repo="squareup/wallet"),
                "https://github.com/squareup/wallet/pull/123",
                client=FakeGitHubClient(
                    review_comments=[
                        review_comment_payload(
                            id=111,
                            body="Current inline feedback.",
                            pull_request_review_id=334,
                        ),
                        review_comment_payload(
                            id=112,
                            body="Newer dismissed inline feedback.",
                            pull_request_review_id=333,
                        ),
                        review_comment_payload(
                            id=113,
                            body="Newest dismissed inline feedback.",
                            pull_request_review_id=333,
                        ),
                    ],
                    reviews=[
                        submitted_review_payload(
                            id=333,
                            body="Dismissed request.",
                            state="DISMISSED",
                        ),
                        submitted_review_payload(
                            id=334,
                            body="Current request.",
                            state="CHANGES_REQUESTED",
                        ),
                    ],
                ),
            )

        review_comments = [signal for signal in signals if signal.kind == "review_comment"]
        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        self.assertEqual([comment.body for comment in review_comments], [
            "Current inline feedback."
        ])
        self.assertEqual(metadata.raw["review_comments"]["processable"], 1)
        self.assertEqual(metadata.raw["review_comments"]["dropped"], 2)

    def test_drops_review_comments_with_unfetched_parent_when_review_window_is_truncated(self):
        with (
            patch("feedback_loop.pipeline.harvest.MAX_FEEDBACK_ITEMS_PER_KIND", 1),
            patch("feedback_loop.pipeline.harvest.MAX_FEEDBACK_RAW_ITEMS_PER_KIND", 2),
        ):
            signals = harvest_pr(
                RunConfig(repo="squareup/wallet"),
                "https://github.com/squareup/wallet/pull/123",
                client=FakeGitHubClient(
                    review_comments=[
                        review_comment_payload(
                            id=111,
                            body="Stale inline feedback with unknown parent state.",
                            pull_request_review_id=333,
                        ),
                        review_comment_payload(
                            id=112,
                            body="Current inline feedback.",
                            pull_request_review_id=335,
                        ),
                    ],
                    reviews=[
                        submitted_review_payload(
                            id=333,
                            body="Dismissed request outside fetched tail.",
                            state="DISMISSED",
                        ),
                        submitted_review_payload(
                            id=334,
                            body="Newer current request.",
                            state="CHANGES_REQUESTED",
                        ),
                        submitted_review_payload(
                            id=335,
                            body="Newest current request.",
                            state="CHANGES_REQUESTED",
                        ),
                    ],
                ),
            )

        review_comments = [signal for signal in signals if signal.kind == "review_comment"]
        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        self.assertEqual([comment.body for comment in review_comments], [
            "Current inline feedback."
        ])
        self.assertEqual(metadata.raw["reviews"]["reported"], 2)
        self.assertEqual(metadata.raw["reviews"]["processable"], 2)
        self.assertTrue(metadata.raw["reviews"]["truncated"])
        self.assertTrue(metadata.raw["reviews"]["raw_truncated"])
        self.assertEqual(metadata.raw["review_comments"]["reported"], 2)
        self.assertEqual(metadata.raw["review_comments"]["processable"], 1)
        self.assertEqual(metadata.raw["review_comments"]["dropped"], 1)

    def test_selects_processable_items_from_raw_window(self):
        with patch("feedback_loop.pipeline.harvest.MAX_FEEDBACK_ITEMS_PER_KIND", 1):
            signals = harvest_pr(
                RunConfig(repo="squareup/wallet"),
                "https://github.com/squareup/wallet/pull/123",
                client=FakeGitHubClient(
                    issue_comments=[
                        issue_comment_payload(id=1, body="Trusted signal."),
                        issue_comment_payload(
                            id=2,
                            user={"login": "drive-by"},
                            author_association="CONTRIBUTOR",
                            body="Newer dropped feedback.",
                        ),
                        issue_comment_payload(
                            id=3,
                            user={"login": "another-drive-by"},
                            author_association="CONTRIBUTOR",
                            body="Newest dropped feedback.",
                        ),
                    ],
                ),
            )

        comments = [signal for signal in signals if signal.kind == "issue_comment"]
        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        self.assertEqual([comment.body for comment in comments], ["Trusted signal."])
        self.assertEqual(metadata.raw["issue_comments"]["processable"], 1)
        self.assertEqual(metadata.raw["issue_comments"]["dropped"], 2)

    def test_raw_window_stops_at_raw_cap_before_processability_filter(self):
        client = FakeGitHubClient(
            issue_comments=[
                issue_comment_payload(id=1, body="Trusted signal outside raw window."),
                issue_comment_payload(
                    id=2,
                    user={"login": "drive-by"},
                    author_association="CONTRIBUTOR",
                    body="Dropped feedback.",
                ),
                issue_comment_payload(
                    id=3,
                    user={"login": "another-drive-by"},
                    author_association="CONTRIBUTOR",
                    body="Newer dropped feedback.",
                ),
                issue_comment_payload(
                    id=4,
                    user={"login": "latest-drive-by"},
                    author_association="CONTRIBUTOR",
                    body="Newest dropped feedback.",
                ),
            ],
        )
        with (
            patch("feedback_loop.pipeline.harvest.MAX_FEEDBACK_ITEMS_PER_KIND", 1),
            patch("feedback_loop.pipeline.harvest.MAX_FEEDBACK_RAW_ITEMS_PER_KIND", 2),
        ):
            signals = harvest_pr(
                RunConfig(repo="squareup/wallet"),
                "https://github.com/squareup/wallet/pull/123",
                client=client,
            )

        comments = [signal for signal in signals if signal.kind == "issue_comment"]
        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        self.assertEqual(comments, [])
        self.assertEqual(client.feedback_max_items, [
            ("pull_reviews", 3),
            ("pull_review_comments", 3),
            ("issue_comments", 3),
        ])
        self.assertEqual(metadata.raw["issue_comments"]["reported"], 2)
        self.assertEqual(metadata.raw["issue_comments"]["processable"], 0)
        self.assertEqual(metadata.raw["issue_comments"]["dropped"], 2)
        self.assertTrue(metadata.raw["issue_comments"]["raw_truncated"])

    def test_spends_body_budget_after_processability_filter(self):
        with patch("feedback_loop.pipeline.harvest.MAX_FEEDBACK_BODY_BYTES_PER_KIND", 5):
            signals = harvest_pr(
                RunConfig(repo="squareup/wallet"),
                "https://github.com/squareup/wallet/pull/123",
                client=FakeGitHubClient(
                    issue_comments=[
                        issue_comment_payload(id=1, body="ok"),
                        issue_comment_payload(
                            id=2,
                            user={"login": "drive-by"},
                            author_association="CONTRIBUTOR",
                            body="newer dropped feedback",
                        ),
                    ],
                ),
            )

        comments = [signal for signal in signals if signal.kind == "issue_comment"]
        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        self.assertEqual([comment.body for comment in comments], ["ok"])
        self.assertEqual(metadata.raw["issue_comments"]["body_bytes"], 2)
        self.assertEqual(metadata.raw["issue_comments"]["dropped"], 1)

    def test_marks_bot_and_human_comment_authors(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                issue_comments=[
                    issue_comment_payload(
                        id=444,
                        user={"login": "github-actions[bot]"},
                        body="Automated note.",
                    ),
                    issue_comment_payload(
                        id=445,
                        user={"login": "human-reviewer"},
                        body="Human note.",
                    ),
                ]
            ),
        )

        comments = {signal.author: signal for signal in signals if signal.kind == "issue_comment"}
        self.assertTrue(comments["github-actions[bot]"].is_bot)
        self.assertFalse(comments["human-reviewer"].is_bot)

    def test_maps_codex_security_review_comment_as_bot_review(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(issue_comments=[codex_security_review_comment_payload()]),
        )

        issue_comments = [signal for signal in signals if signal.kind == "issue_comment"]
        bot_reviews = [signal for signal in signals if signal.kind == "bot_review"]
        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")

        self.assertEqual(len(issue_comments), 1)
        self.assertEqual(len(bot_reviews), 1)
        signal = bot_reviews[0]
        self.assertEqual(signal.source_id, "bot_review:squareup/wallet#123:444")
        self.assertEqual(signal.author, "github-actions[bot]")
        self.assertTrue(signal.is_bot)
        self.assertEqual(signal.raw["provider"], "codex_security_review")
        self.assertEqual(signal.raw["match_reason"], "codex-security-review-marker")
        self.assertEqual(signal.raw["reviewed_commit_range"], "basesha...headsha")
        self.assertEqual(signal.raw["reviewed_base_sha"], "basesha")
        self.assertEqual(signal.raw["reviewed_head_sha"], "headsha")
        self.assertEqual(
            signal.raw["workflow_run_url"],
            "https://github.com/squareup/wallet/actions/runs/123456",
        )
        bot_reviews_summary = metadata.raw["bot_reviews"]
        self.assertEqual(bot_reviews_summary["reported"], 1)
        self.assertEqual(bot_reviews_summary["fetched"], 1)
        self.assertFalse(bot_reviews_summary["truncated"])
        self.assertFalse(bot_reviews_summary["raw_truncated"])
        self.assertFalse(bot_reviews_summary["item_truncated"])
        self.assertEqual(bot_reviews_summary["item_limit"], 1000)
        self.assertEqual(bot_reviews_summary["body_byte_limit"], 1048576)
        self.assertGreater(bot_reviews_summary["body_bytes"], 0)
        self.assertEqual(bot_reviews_summary["dropped"], 0)
        self.assertEqual(bot_reviews_summary["body_dropped"], 0)
        self.assertEqual(bot_reviews_summary["codex_security_review"], 1)
        self.assertEqual(bot_reviews_summary["builderbot"], 0)

    def test_preserves_bot_review_when_issue_comments_are_truncated(self):
        with patch("feedback_loop.pipeline.harvest.MAX_FEEDBACK_ITEMS_PER_KIND", 1):
            signals = harvest_pr(
                RunConfig(repo="squareup/wallet"),
                "https://github.com/squareup/wallet/pull/123",
                client=FakeGitHubClient(
                    issue_comments=[
                        codex_security_review_comment_payload(id=444),
                        issue_comment_payload(id=445, body="newest comment"),
                    ],
                ),
            )

        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        bot_reviews = [signal for signal in signals if signal.kind == "bot_review"]
        issue_comments = [signal for signal in signals if signal.kind == "issue_comment"]

        self.assertEqual([review.source_id for review in bot_reviews], [
            "bot_review:squareup/wallet#123:444"
        ])
        self.assertEqual([comment.source_id for comment in issue_comments], [
            "issue_comment:squareup/wallet#123:445"
        ])
        self.assertTrue(metadata.raw["issue_comments"]["truncated"])
        self.assertEqual(metadata.raw["bot_reviews"]["reported"], 1)
        self.assertEqual(metadata.raw["bot_reviews"]["fetched"], 1)
        self.assertFalse(metadata.raw["bot_reviews"]["truncated"])
        self.assertEqual(metadata.raw["bot_reviews"]["codex_security_review"], 1)
        self.assertEqual(metadata.raw["bot_reviews"]["builderbot"], 0)

    def test_marks_bot_review_summary_truncated_by_own_item_count(self):
        with patch("feedback_loop.pipeline.harvest.MAX_BOT_REVIEW_ITEMS", 1):
            signals = harvest_pr(
                RunConfig(repo="squareup/wallet"),
                "https://github.com/squareup/wallet/pull/123",
                client=FakeGitHubClient(
                    issue_comments=[
                        codex_security_review_comment_payload(id=444),
                        codex_security_review_comment_payload(id=445),
                    ],
                ),
            )

        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        bot_reviews = [signal for signal in signals if signal.kind == "bot_review"]

        self.assertEqual([review.source_id for review in bot_reviews], [
            "bot_review:squareup/wallet#123:445"
        ])
        self.assertEqual(metadata.raw["bot_reviews"]["reported"], 2)
        self.assertEqual(metadata.raw["bot_reviews"]["fetched"], 1)
        self.assertTrue(metadata.raw["bot_reviews"]["truncated"])
        self.assertFalse(metadata.raw["bot_reviews"]["raw_truncated"])
        self.assertTrue(metadata.raw["bot_reviews"]["item_truncated"])
        self.assertEqual(metadata.raw["bot_reviews"]["item_limit"], 1)
        self.assertEqual(metadata.raw["bot_reviews"]["dropped"], 1)

    def test_maps_builderbot_bot_author_as_bot_review(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                issue_comments=[
                    issue_comment_payload(
                        id=445,
                        user={"login": "builderbot[bot]"},
                        body="I opened a draft PR for the requested fuzz harness.",
                    ),
                    issue_comment_payload(
                        id=446,
                        user={"login": "human-reviewer"},
                        body="@builderbot please implement this fuzz harness.",
                    ),
                ]
            ),
        )

        bot_reviews = [signal for signal in signals if signal.kind == "bot_review"]
        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        self.assertEqual(len(bot_reviews), 1)
        self.assertEqual(bot_reviews[0].source_id, "bot_review:squareup/wallet#123:445")
        self.assertEqual(bot_reviews[0].raw["provider"], "builderbot")
        self.assertEqual(bot_reviews[0].raw["match_reason"], "builderbot-author")
        self.assertEqual(metadata.raw["bot_reviews"]["builderbot"], 1)

    def test_does_not_promote_human_codex_marker_to_bot_review(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                issue_comments=[
                    issue_comment_payload(
                        id=447,
                        user={"login": "trusted-human"},
                        author_association="MEMBER",
                        body="<!-- codex-security-review -->\nSpoofed Codex Security Review.",
                    )
                ]
            ),
        )

        bot_reviews = [signal for signal in signals if signal.kind == "bot_review"]
        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")

        self.assertEqual(bot_reviews, [])
        self.assertEqual(metadata.raw["bot_reviews"]["reported"], 0)
        self.assertEqual(metadata.raw["bot_reviews"]["fetched"], 0)
        self.assertFalse(metadata.raw["bot_reviews"]["truncated"])
        self.assertEqual(metadata.raw["bot_reviews"]["dropped"], 0)
        self.assertEqual(metadata.raw["bot_reviews"]["codex_security_review"], 0)
        self.assertEqual(metadata.raw["bot_reviews"]["builderbot"], 0)

    def test_does_not_promote_builderbot_mentions_or_human_lookalikes_to_bot_review(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                issue_comments=[
                    issue_comment_payload(
                        id=448,
                        user={"login": "github-actions[bot]"},
                        body="@builderbot please implement this fuzz harness.",
                    ),
                    issue_comment_payload(
                        id=449,
                        user={"login": "builderbot-human"},
                        author_association="MEMBER",
                        body="I am not Builderbot output.",
                    ),
                ]
            ),
        )

        bot_reviews = [signal for signal in signals if signal.kind == "bot_review"]
        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")

        self.assertEqual(bot_reviews, [])
        self.assertEqual(metadata.raw["bot_reviews"]["builderbot"], 0)

    def test_maps_failed_ci_status_check_run_and_workflow_run_signals(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                commits=[
                    {
                        "sha": "headsha",
                        "html_url": "https://github.com/squareup/wallet/commit/headsha",
                        "commit": {
                            "message": "Push after review",
                            "author": {"date": "2026-05-04T04:00:00Z"},
                            "committer": {"date": "2026-05-04T04:05:00Z"},
                        },
                    }
                ],
                issue_comments=[
                    issue_comment_payload(id=447, created_at="2026-05-04T03:00:00Z")
                ],
                commit_statuses=[
                    commit_status_payload(id=1001, state="failure"),
                    commit_status_payload(id=1002, state="success"),
                ],
                check_runs=[
                    check_run_payload(id=2001, conclusion="failure"),
                    check_run_payload(id=2002, conclusion="success"),
                    # Cancelled runs are superseded-head noise, not quality signals.
                    check_run_payload(id=2003, conclusion="cancelled"),
                ],
                workflow_runs=[
                    workflow_run_payload(id=3001, conclusion="timed_out"),
                    workflow_run_payload(id=3002, conclusion="success"),
                    workflow_run_payload(id=3003, conclusion="cancelled"),
                ],
            ),
        )

        checks = [signal for signal in signals if signal.kind == "check"]
        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")

        self.assertEqual(
            [check.source_id for check in checks],
            [
                "check:commit_status:squareup/wallet#123:1001",
                "check:check_run:squareup/wallet#123:2001",
                "check:workflow_run:squareup/wallet#123:3001",
            ],
        )
        self.assertEqual({check.raw["source"] for check in checks}, {
            "commit_status",
            "check_run",
            "workflow_run",
        })
        for check in checks:
            self.assertEqual(check.raw["sha"], "headsha")
            self.assertTrue(check.raw["timing"]["event_after_latest_feedback"])
            self.assertTrue(check.raw["timing"]["event_after_latest_commit"])
            self.assertTrue(check.is_bot)
            self.assertIn(check.raw["name"], check.body)
            self.assertIn(check.raw["url"], check.body)

        self.assertEqual(metadata.raw["commit_statuses"]["reported"], 2)
        self.assertEqual(metadata.raw["check_runs"]["reported"], 3)
        self.assertEqual(metadata.raw["workflow_runs"]["reported"], 3)
        self.assertEqual(metadata.raw["check_failures"], {
            "reported": 3,
            "fetched": 3,
            "truncated": False,
        })

    def test_dedupes_duplicate_comment_and_review_source_ids(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                issue_comments=[
                    issue_comment_payload(id=555, body="First issue comment wins."),
                    issue_comment_payload(id=555, body="Duplicate issue comment loses."),
                ],
                review_comments=[
                    review_comment_payload(id=555, body="Same numeric ID, different kind.")
                ],
                reviews=[
                    submitted_review_payload(id=555, body="Same numeric ID, review kind.")
                ],
            ),
        )

        harvested = [
            signal
            for signal in signals
            if signal.kind in {"issue_comment", "review_comment", "review"}
        ]
        self.assertEqual(
            [signal.source_id for signal in harvested],
            [
                "issue_comment:squareup/wallet#123:555",
                "review_comment:squareup/wallet#123:555",
                "review:squareup/wallet#123:555",
            ],
        )
        issue_comment = next(signal for signal in harvested if signal.kind == "issue_comment")
        self.assertEqual(issue_comment.body, "First issue comment wins.")

    def test_rejects_comment_and_review_payloads_missing_required_id(self):
        cases = [
            (
                "issue_comment payload",
                {
                    "issue_comments": [
                        {
                            "user": {"login": "reviewer"},
                            "author_association": "MEMBER",
                            "body": "missing id",
                        }
                    ]
                },
            ),
            (
                "review_comment payload",
                {
                    "review_comments": [
                        {
                            "user": {"login": "reviewer"},
                            "author_association": "MEMBER",
                            "body": "missing id",
                        }
                    ]
                },
            ),
            (
                "review payload",
                {
                    "reviews": [
                        {
                            "user": {"login": "reviewer"},
                            "author_association": "MEMBER",
                            "body": "missing id",
                        }
                    ]
                },
            ),
        ]
        for payload_name, client_kwargs in cases:
            with self.subTest(payload_name=payload_name):
                with self.assertRaises(HarvestError) as ctx:
                    harvest_pr(
                        RunConfig(repo="squareup/wallet"),
                        "https://github.com/squareup/wallet/pull/123",
                        client=FakeGitHubClient(**client_kwargs),
                    )

                self.assertIn(
                    f"{payload_name} for squareup/wallet#123 is missing id",
                    str(ctx.exception),
                )

    def test_marks_feedback_truncated_by_item_count_in_metadata(self):
        with patch("feedback_loop.pipeline.harvest.MAX_FEEDBACK_ITEMS_PER_KIND", 1):
            signals = harvest_pr(
                RunConfig(repo="squareup/wallet"),
                "https://github.com/squareup/wallet/pull/123",
                client=FakeGitHubClient(
                    issue_comments=[
                        issue_comment_payload(id=1, body="first"),
                        issue_comment_payload(id=2, body="second"),
                        issue_comment_payload(id=3, body="third"),
                    ],
                ),
            )

        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        comments = [signal for signal in signals if signal.kind == "issue_comment"]
        self.assertEqual([comment.body for comment in comments], ["third"])
        self.assertEqual(metadata.raw["issue_comments"], {
            "reported": 3,
            "processable": 3,
            "fetched": 1,
            "truncated": True,
            "raw_truncated": False,
            "item_truncated": True,
            "raw_item_limit": 2000,
            "item_limit": 1,
            "body_byte_limit": 1048576,
            "body_bytes": 5,
            "duplicates": 0,
            "dropped": 0,
            "body_dropped": 0,
        })

    def test_dedupes_feedback_before_applying_item_count_limit(self):
        with patch("feedback_loop.pipeline.harvest.MAX_FEEDBACK_ITEMS_PER_KIND", 2):
            signals = harvest_pr(
                RunConfig(repo="squareup/wallet"),
                "https://github.com/squareup/wallet/pull/123",
                client=FakeGitHubClient(
                    issue_comments=[
                        issue_comment_payload(id=1, body="first"),
                        issue_comment_payload(id=1, body="duplicate"),
                        issue_comment_payload(id=2, body="second"),
                    ],
                ),
            )

        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        comments = [signal for signal in signals if signal.kind == "issue_comment"]
        self.assertEqual([comment.body for comment in comments], ["first", "second"])
        self.assertEqual(metadata.raw["issue_comments"]["reported"], 2)
        self.assertEqual(metadata.raw["issue_comments"]["fetched"], 2)
        self.assertFalse(metadata.raw["issue_comments"]["truncated"])
        self.assertEqual(metadata.raw["issue_comments"]["duplicates"], 1)

    def test_body_byte_limit_is_per_feedback_kind(self):
        with patch("feedback_loop.pipeline.harvest.MAX_FEEDBACK_BODY_BYTES_PER_KIND", 5):
            signals = harvest_pr(
                RunConfig(repo="squareup/wallet"),
                "https://github.com/squareup/wallet/pull/123",
                client=FakeGitHubClient(
                    issue_comments=[
                        issue_comment_payload(id=1, body="12345"),
                        issue_comment_payload(id=2, body="6"),
                    ],
                    review_comments=[review_comment_payload(id=3, body="7")],
                ),
            )

        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        comments = [signal for signal in signals if signal.kind == "issue_comment"]
        review_comments = [signal for signal in signals if signal.kind == "review_comment"]
        self.assertEqual([comment.body for comment in comments], ["6"])
        self.assertEqual([comment.body for comment in review_comments], ["7"])
        self.assertEqual(metadata.raw["issue_comments"]["fetched"], 1)
        self.assertTrue(metadata.raw["issue_comments"]["truncated"])
        self.assertEqual(metadata.raw["issue_comments"]["dropped"], 0)
        self.assertEqual(metadata.raw["issue_comments"]["body_dropped"], 1)
        self.assertEqual(metadata.raw["review_comments"]["fetched"], 1)
        self.assertFalse(metadata.raw["review_comments"]["truncated"])
        self.assertEqual(metadata.raw["review_comments"]["body_dropped"], 0)
        self.assertEqual(metadata.raw["feedback_body_bytes"], {
            "fetched": 2,
            "limit": 15,
            "truncated": True,
        })

    def test_body_byte_limit_skips_oversized_newest_feedback(self):
        with patch("feedback_loop.pipeline.harvest.MAX_FEEDBACK_BODY_BYTES_PER_KIND", 5):
            signals = harvest_pr(
                RunConfig(repo="squareup/wallet"),
                "https://github.com/squareup/wallet/pull/123",
                client=FakeGitHubClient(
                    issue_comments=[
                        issue_comment_payload(id=1, body="older"),
                        issue_comment_payload(id=2, body="newest-too-large"),
                    ],
                ),
            )

        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        comments = [signal for signal in signals if signal.kind == "issue_comment"]
        self.assertEqual([comment.body for comment in comments], ["older"])
        self.assertEqual(metadata.raw["issue_comments"]["fetched"], 1)
        self.assertTrue(metadata.raw["issue_comments"]["truncated"])
        self.assertEqual(metadata.raw["issue_comments"]["body_bytes"], 5)
        self.assertEqual(metadata.raw["issue_comments"]["body_dropped"], 1)

    def test_maps_commit_signals(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                commits=[
                    {
                        "sha": "abc123",
                        "html_url": "https://github.com/squareup/wallet/commit/abc123",
                        "author": {"login": "author-login"},
                        "committer": {"login": "committer-login"},
                        "commit": {
                            "message": "Add structural harvest",
                            "author": {
                                "name": "Author Name",
                                "email": "author@example.com",
                                "date": "2026-05-01T02:00:00Z",
                            },
                            "committer": {
                                "name": "Committer Name",
                                "email": "committer@example.com",
                                "date": "2026-05-01T03:00:00Z",
                            },
                        },
                        "parents": [{"sha": "parent1"}],
                    }
                ]
            ),
        )

        commits = [signal for signal in signals if signal.kind == "commit"]
        self.assertEqual(len(commits), 1)
        signal = commits[0]
        self.assertEqual(signal.source_id, "commit:abc123")
        self.assertEqual(signal.source_url, "https://github.com/squareup/wallet/commit/abc123")
        self.assertEqual(signal.author, "author-login")
        self.assertEqual(signal.created_at, "2026-05-01T02:00:00Z")
        self.assertEqual(signal.body, "Add structural harvest")
        self.assertEqual(signal.raw["parents"], ["parent1"])
        self.assertEqual(signal.raw["committer"]["login"], "committer-login")

    def test_marks_truncated_github_commit_list_in_metadata(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                pull=merged_pull(commits=3),
                commits=[
                    {
                        "sha": "abc123",
                        "html_url": "https://github.com/squareup/wallet/commit/abc123",
                        "commit": {"message": "First fetched commit"},
                    },
                    {
                        "sha": "def456",
                        "html_url": "https://github.com/squareup/wallet/commit/def456",
                        "commit": {"message": "Second fetched commit"},
                    },
                ],
            ),
        )

        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        commits = [signal for signal in signals if signal.kind == "commit"]
        self.assertEqual(len(commits), 2)
        self.assertEqual(metadata.raw["commits"], {
            "reported": 3,
            "fetched": 2,
            "truncated": True,
        })

    def test_maps_changed_files_with_area_tags(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                files=[
                    {
                        "filename": "app/src/Main.kt",
                        "status": "modified",
                        "additions": 3,
                        "deletions": 1,
                        "changes": 4,
                        "sha": "file-sha",
                        "blob_url": "https://github.com/squareup/wallet/blob/head/app/src/Main.kt",
                        "raw_url": (
                            "https://raw.githubusercontent.com/squareup/wallet/head/app/src/Main.kt"
                        ),
                        "contents_url": (
                            "https://api.github.com/repos/squareup/wallet/contents/app/src/Main.kt"
                        ),
                        "patch": "@@ -1 +1 @@",
                    },
                    {
                        "filename": "server/src/main.rs",
                        "status": "added",
                        "additions": 5,
                        "deletions": 0,
                        "changes": 5,
                    },
                    {
                        "filename": "README.md",
                        "status": "modified",
                        "additions": 1,
                        "deletions": 0,
                        "changes": 1,
                    },
                ]
            ),
        )

        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        files = [signal for signal in signals if signal.kind == "changed_file"]
        self.assertEqual(len(files), 3)
        self.assertEqual({file.path: file.raw["area"] for file in files}, {
            "app/src/Main.kt": "app",
            "server/src/main.rs": "server",
            "README.md": "repo",
        })
        self.assertEqual(set(metadata.raw["areas"]), {"app", "server", "repo"})
        self.assertEqual(files[0].source_id, "file:squareup/wallet#123:app/src/Main.kt")
        self.assertEqual(files[0].raw["status"], "modified")
        self.assertTrue(files[0].raw["patch_present"])
        self.assertFalse(files[1].raw["patch_present"])
        self.assertEqual(files[1].source_url, "https://github.com/squareup/wallet/pull/123")

    def test_marks_truncated_github_file_list_in_metadata(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                pull=merged_pull(changed_files=3),
                files=[
                    {"filename": "app/src/Main.kt", "status": "modified"},
                    {"filename": "server/src/main.rs", "status": "added"},
                ],
            ),
        )

        metadata = next(signal for signal in signals if signal.kind == "pr_metadata")
        files = [signal for signal in signals if signal.kind == "changed_file"]
        self.assertEqual(len(files), 2)
        self.assertEqual(metadata.raw["changed_files"], {
            "reported": 3,
            "fetched": 2,
            "truncated": True,
        })
        self.assertEqual(set(metadata.raw["areas"]), {"app", "server"})

    def test_maps_diff_hunk_signals(self):
        diff = (
            "diff --git a/app/src/Main.kt b/app/src/Main.kt\n"
            "--- a/app/src/Main.kt\n"
            "+++ b/app/src/Main.kt\n"
            "@@ -1,2 +1,3 @@ fun main()\n"
            " line one\n"
            "+line two\n"
            " line three\n"
        )
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                files=[
                    {
                        "filename": "app/src/Main.kt",
                        "status": "modified",
                        "blob_url": "https://github.com/squareup/wallet/blob/head/app/src/Main.kt",
                    }
                ],
                diff=diff,
            ),
        )

        hunks = [signal for signal in signals if signal.kind == "diff_hunk"]
        self.assertEqual(len(hunks), 1)
        signal = hunks[0]
        self.assertEqual(
            signal.source_id,
            "hunk:squareup/wallet#123:app/src/Main.kt:1,2->1,3",
        )
        self.assertEqual(
            signal.source_url,
            "https://github.com/squareup/wallet/blob/head/app/src/Main.kt#L1-L3",
        )
        self.assertEqual(signal.path, "app/src/Main.kt")
        self.assertEqual(signal.line, 1)
        self.assertEqual(
            signal.body,
            "@@ -1,2 +1,3 @@ fun main()\n line one\n+line two\n line three\n",
        )
        self.assertNotIn("hunk_text", signal.raw)
        self.assertEqual(signal.raw["area"], "app")

    def test_maps_hunk_paths_with_quoted_names_and_tab_metadata(self):
        diff = (
            'diff --git "a/app/App Icons/Icon File.txt" "b/app/App Icons/Icon File.txt"\n'
            '--- "a/app/App Icons/Icon File.txt"\t2026-06-02 12:00:00\n'
            '+++ "b/app/App Icons/Icon File.txt"\t2026-06-02 12:00:00\n'
            "@@ -1 +1 @@\n"
            "-old\n"
            "+new\n"
        )
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                files=[
                    {
                        "filename": "app/App Icons/Icon File.txt",
                        "status": "modified",
                        "blob_url": (
                            "https://github.com/squareup/wallet/blob/head/"
                            "app/App%20Icons/Icon%20File.txt"
                        ),
                    }
                ],
                diff=diff,
            ),
        )

        hunk = next(signal for signal in signals if signal.kind == "diff_hunk")
        self.assertEqual(hunk.path, "app/App Icons/Icon File.txt")
        self.assertEqual(hunk.raw["old_path"], "app/App Icons/Icon File.txt")
        self.assertEqual(hunk.raw["new_path"], "app/App Icons/Icon File.txt")
        self.assertEqual(hunk.raw["area"], "app")
        self.assertEqual(
            hunk.source_url,
            "https://github.com/squareup/wallet/blob/head/app/App%20Icons/Icon%20File.txt#L1",
        )

    def test_maps_hunk_paths_with_git_quoted_utf8_octal_escapes(self):
        filename = "app/Snapshots/en dash \u2013 name.txt"
        quoted_filename = "app/Snapshots/en dash \\342\\200\\223 name.txt"
        diff = (
            f'diff --git "a/{quoted_filename}" "b/{quoted_filename}"\n'
            f'--- "a/{quoted_filename}"\n'
            f'+++ "b/{quoted_filename}"\n'
            "@@ -1 +1 @@\n"
            "-old\n"
            "+new\n"
        )
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                files=[
                    {
                        "filename": filename,
                        "status": "modified",
                        "blob_url": (
                            "https://github.com/squareup/wallet/blob/head/"
                            "app/Snapshots/en%20dash%20%E2%80%93%20name.txt"
                        ),
                    }
                ],
                diff=diff,
            ),
        )

        hunk = next(signal for signal in signals if signal.kind == "diff_hunk")
        self.assertEqual(hunk.path, filename)
        self.assertEqual(hunk.raw["old_path"], filename)
        self.assertEqual(hunk.raw["new_path"], filename)
        self.assertEqual(
            hunk.source_url,
            (
                "https://github.com/squareup/wallet/blob/head/"
                "app/Snapshots/en%20dash%20%E2%80%93%20name.txt#L1"
            ),
        )

    def test_rejects_oversized_pr_diff_before_parsing(self):
        with patch("feedback_loop.pipeline.harvest.MAX_PR_DIFF_BYTES", 20):
            client = FakeGitHubClient(diff="x" * 21)
            with self.assertRaises(HarvestError) as ctx:
                harvest_pr(
                    RunConfig(repo="squareup/wallet"),
                    "https://github.com/squareup/wallet/pull/123",
                    client=client,
                )

        self.assertIn("exceeding the 20 byte limit", str(ctx.exception))
        self.assertEqual(client.calls, [
            "pull_metadata",
            "pull_commits",
            "pull_files",
            "pull_diff",
        ])

    def test_binary_or_missing_patch_file_produces_no_hunk(self):
        signals = harvest_pr(
            RunConfig(repo="squareup/wallet"),
            "https://github.com/squareup/wallet/pull/123",
            client=FakeGitHubClient(
                files=[
                    {
                        "filename": "core/image.png",
                        "status": "modified",
                        "additions": 0,
                        "deletions": 0,
                        "changes": 0,
                    }
                ],
                diff=(
                    "diff --git a/core/image.png b/core/image.png\n"
                    "index 1111111..2222222 100644\n"
                    "Binary files a/core/image.png and b/core/image.png differ\n"
                ),
            ),
        )

        files = [signal for signal in signals if signal.kind == "changed_file"]
        hunks = [signal for signal in signals if signal.kind == "diff_hunk"]
        self.assertEqual(len(files), 1)
        self.assertEqual(files[0].raw["area"], "core")
        self.assertFalse(files[0].raw["patch_present"])
        self.assertEqual(hunks, [])


class TestUnifiedDiffParsing(unittest.TestCase):
    def test_parses_added_modified_deleted_and_renamed_hunks(self):
        diff = (
            "diff --git a/app/src/Main.kt b/app/src/Main.kt\n"
            "--- a/app/src/Main.kt\n"
            "+++ b/app/src/Main.kt\n"
            "@@ -1,3 +1,3 @@\n"
            " line one\n"
            "--- removed marker\n"
            "+++ added marker\n"
            " line three\n"
            "diff --git a/server/new.rs b/server/new.rs\n"
            "new file mode 100644\n"
            "--- /dev/null\n"
            "+++ b/server/new.rs\n"
            "@@ -0,0 +1,2 @@\n"
            "+one\n"
            "+two\n"
            "diff --git a/firmware/old.c b/firmware/old.c\n"
            "deleted file mode 100644\n"
            "--- a/firmware/old.c\n"
            "+++ /dev/null\n"
            "@@ -1,2 +0,0 @@\n"
            "-one\n"
            "-two\n"
            "diff --git a/web/old.ts b/web/new.ts\n"
            "similarity index 75%\n"
            "rename from web/old.ts\n"
            "rename to web/new.ts\n"
            "--- a/web/old.ts\n"
            "+++ b/web/new.ts\n"
            "@@ -5 +5 @@\n"
            "-old\n"
            "+new\n"
        )

        hunks = parse_unified_diff_hunks(diff)

        self.assertEqual([hunk.path for hunk in hunks], [
            "app/src/Main.kt",
            "server/new.rs",
            "firmware/old.c",
            "web/new.ts",
        ])
        self.assertEqual((hunks[0].old_start, hunks[0].old_count), (1, 3))
        self.assertEqual((hunks[0].new_start, hunks[0].new_count), (1, 3))
        self.assertIn("--- removed marker\n+++ added marker\n", hunks[0].text)
        self.assertEqual((hunks[1].old_start, hunks[1].old_count), (0, 0))
        self.assertEqual((hunks[1].new_start, hunks[1].new_count), (1, 2))
        self.assertEqual((hunks[2].old_start, hunks[2].old_count), (1, 2))
        self.assertEqual((hunks[2].new_start, hunks[2].new_count), (0, 0))
        self.assertEqual((hunks[3].old_path, hunks[3].new_path), ("web/old.ts", "web/new.ts"))
        self.assertEqual(hunks[3].text, "@@ -5 +5 @@\n-old\n+new\n")


def raw_bot_review_signal(**updates) -> RawSignal:
    signal = RawSignal(
        kind="bot_review",
        source_id="bot_review:squareup/wallet#123:444",
        source_url="https://github.com/squareup/wallet/pull/123#issuecomment-444",
        repo="squareup/wallet",
        pr_number=123,
        captured_at="2026-05-04T01:00:00Z",
        author="github-actions[bot]",
        author_association="MEMBER",
        created_at="2026-05-04T00:59:00Z",
        body="Automated review finding",
        is_bot=True,
        raw={
            "provider": "codex_security_review",
            "body": "Automated review finding",
            "nested": {"url": "https://github.com/squareup/wallet/actions/runs/1"},
        },
    )
    for key, value in updates.items():
        setattr(signal, key, value)
    return signal


class TestNormalizeSignals(unittest.TestCase):
    def test_normalizes_provenance_and_body_in_memory(self):
        signal = raw_bot_review_signal()

        normalized = normalize_signals(RunConfig(harvest_version="7"), [signal])

        self.assertEqual(len(normalized), 1)
        item = normalized[0]
        self.assertIs(item.raw, signal)
        self.assertEqual(item.kind, "bot_review")
        self.assertEqual(item.source, "codex_security_review")
        self.assertEqual(item.source_id, signal.source_id)
        self.assertEqual(item.harvest_version, "7")
        self.assertEqual(item.body, "Automated review finding")
        self.assertEqual(item.raw_metadata["provider"], "codex_security_review")

    def test_copies_raw_metadata_before_returning(self):
        signal = raw_bot_review_signal()

        normalized = normalize_signals(RunConfig(), [signal])
        signal.raw["nested"]["url"] = "changed"

        self.assertEqual(
            normalized[0].raw_metadata["nested"]["url"],
            "https://github.com/squareup/wallet/actions/runs/1",
        )

    def test_falls_back_to_kind_when_source_metadata_is_missing(self):
        normalized = normalize_signals(RunConfig(), [raw_bot_review_signal(raw={})])

        self.assertEqual(normalized[0].source, "bot_review")


if __name__ == "__main__":
    unittest.main()

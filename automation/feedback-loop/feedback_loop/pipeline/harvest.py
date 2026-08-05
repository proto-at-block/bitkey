"""Stage 1: harvest.

GitHub is the raw evidence source of record. No data is persisted; each run re-fetches.
The single-PR harvest currently collects PR metadata, commits, changed files, unified diff hunks,
issue comments, inline review comments, submitted reviews, derived bot-review signals, and failed
CI/check signals. Backfill enumeration lists merged PR URLs in a bounded historical window.

All returned `body` text is untrusted data and must be treated as data, not instructions.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import re
import shlex
from typing import Any

from ..config import RunConfig
from ..github import GitHubClient, GitHubError, PullRequestRef, parse_pull_request_url
from ..models import RawSignal


class HarvestError(Exception):
    """Raised for invalid or failed harvest input."""


AREA_PREFIXES: tuple[tuple[str, str], ...] = (
    ("app/", "app"),
    ("server/", "server"),
    ("firmware/", "firmware"),
    ("web/", "web"),
    ("core/", "core"),
    ("docs/", "docs"),
    ("automation/", "automation"),
)

MAX_PR_DIFF_BYTES = 5 * 1024 * 1024
MAX_DIFF_HUNKS = 10_000
MAX_FEEDBACK_ITEMS_PER_KIND = 1_000
MAX_FEEDBACK_RAW_ITEMS_PER_KIND = MAX_FEEDBACK_ITEMS_PER_KIND * 2
MAX_FEEDBACK_BODY_BYTES_PER_KIND = 1 * 1024 * 1024
TRUSTED_FEEDBACK_AUTHOR_ASSOCIATIONS = {"OWNER", "MEMBER", "COLLABORATOR"}
TRUSTED_FEEDBACK_BOT_LOGINS = {"copilot"}
MAX_CHECK_ITEMS_PER_SOURCE = 1_000
CLOSED_PULL_REQUESTS_PAGE_SIZE = 100
MAX_BOT_REVIEW_ITEMS = MAX_FEEDBACK_ITEMS_PER_KIND
MAX_BOT_REVIEW_BODY_BYTES = MAX_FEEDBACK_BODY_BYTES_PER_KIND
CODEX_SECURITY_REVIEW_BOT_LOGINS = {"github-actions[bot]"}
BUILDERBOT_BOT_LOGINS = {"builderbot[bot]", "builder-bot[bot]"}

CODEX_SECURITY_REVIEW_MARKER = "<!-- codex-security-review -->"
_CODEX_REVIEWED_RANGE_RE = re.compile(r"Reviewed (?:pull request|push) diff only \(`([^`]+)`")
_GITHUB_ACTIONS_RUN_RE = re.compile(r"https://github\.com/[^/\s]+/[^/\s]+/actions/runs/\d+")
_FAILED_STATUS_STATES = {"error", "failure"}
_FAILED_CHECK_CONCLUSIONS = {
    "action_required",
    "cancelled",
    "failure",
    "startup_failure",
    "timed_out",
}

_HUNK_HEADER_RE = re.compile(
    r"^@@ -(?P<old_start>\d+)(?:,(?P<old_count>\d+))? "
    r"\+(?P<new_start>\d+)(?:,(?P<new_count>\d+))? @@(?P<section>.*)$"
)


@dataclass(frozen=True)
class ParsedDiffHunk:
    path: str
    old_path: str
    new_path: str
    old_start: int
    old_count: int
    new_start: int
    new_count: int
    header: str
    section: str
    text: str


@dataclass(frozen=True)
class ParsedHunkHeader:
    old_start: int
    old_count: int
    new_start: int
    new_count: int
    header: str
    section: str


@dataclass(frozen=True)
class FeedbackRawWindow:
    items: list[dict[str, Any]]
    duplicates: int
    raw_truncated: bool
    raw_item_limit: int


def list_merged_prs(
    cfg: RunConfig,
    client: GitHubClient | None = None,
) -> list[str]:
    """Enumerate merged PR URLs in the backfill window.

    Bound by --since/--until/--limit and use GitHub list+pagination rather than the search API.
    """
    if cfg.limit <= 0:
        raise HarvestError("--limit must be positive")
    since = _parse_backfill_bound(cfg.since, end_of_day=False)
    until = _parse_backfill_bound(cfg.until, end_of_day=True)
    if since is not None and until is not None and since > until:
        raise HarvestError("--since must be before --until")

    repo = cfg.repo.strip()
    github = client or GitHubClient()

    pr_urls: list[str] = []
    seen_numbers: set[int] = set()
    page = 1
    while len(pr_urls) < cfg.limit:
        try:
            pulls = github.closed_pull_requests_page(
                repo,
                page=page,
                per_page=CLOSED_PULL_REQUESTS_PAGE_SIZE,
            )
        except GitHubError as err:
            raise HarvestError(str(err)) from err

        if not pulls:
            break

        reached_older_updates = False
        for pull in pulls:
            updated_at = _parse_timestamp(_as_str(pull.get("updated_at")))
            if since is not None and updated_at is not None and updated_at < since:
                reached_older_updates = True
                break

            merged_at = _parse_timestamp(_as_str(pull.get("merged_at")))
            if merged_at is None:
                continue
            if since is not None and merged_at < since:
                continue
            if until is not None and merged_at > until:
                continue

            number = _as_int(pull.get("number"))
            if number > 0:
                if number in seen_numbers:
                    continue
                seen_numbers.add(number)

            pr_url = _as_str(pull.get("html_url"))
            if not pr_url:
                if number <= 0:
                    continue
                pr_url = f"https://github.com/{repo}/pull/{number}"
            pr_urls.append(pr_url)
            if len(pr_urls) >= cfg.limit:
                break

        if reached_older_updates or len(pulls) < CLOSED_PULL_REQUESTS_PAGE_SIZE:
            break
        page += 1
    return pr_urls


def harvest_pr(
    cfg: RunConfig,
    pr_url: str,
    client: GitHubClient | None = None,
) -> list[RawSignal]:
    """Collect raw signals for one merged PR.

    Future idempotency is owned by backfill orchestration; this function only returns in-memory
    records.
    """
    ref = _parse_and_validate_ref(cfg, pr_url)
    github = client or GitHubClient()
    captured_at = _utc_now_iso()

    try:
        pull = github.pull_metadata(ref)
    except GitHubError as err:
        raise HarvestError(str(err)) from err

    if not _as_str(pull.get("merged_at")):
        raise HarvestError(f"PR {ref.repo}#{ref.number} is not merged")

    try:
        commits = github.pull_commits(ref)
        files = github.pull_files(ref)
        diff_text = github.pull_diff(ref)
    except GitHubError as err:
        raise HarvestError(str(err)) from err
    _validate_diff_size(diff_text)

    try:
        head_sha = _nested_str(pull, "head", "sha")
        check_fetch_limit = MAX_CHECK_ITEMS_PER_SOURCE + 1
        commit_status_payloads = (
            github.commit_statuses(ref, head_sha, max_items=check_fetch_limit)
            if head_sha
            else []
        )
        check_run_payloads = (
            github.check_runs(ref, head_sha, max_items=check_fetch_limit) if head_sha else []
        )
        workflow_run_payloads = (
            github.workflow_runs_for_sha(ref, head_sha, max_items=check_fetch_limit)
            if head_sha
            else []
        )
        feedback_fetch_limit = MAX_FEEDBACK_RAW_ITEMS_PER_KIND + 1
        review_payloads = github.pull_reviews(
            ref,
            max_items=feedback_fetch_limit,
        )
        review_raw_window = _feedback_raw_window(review_payloads)
        known_review_ids = _github_payload_ids(review_raw_window.items)
        dismissed_review_ids = _dismissed_review_ids(review_raw_window.items)
        review_state_is_partial = review_raw_window.raw_truncated
        review_comment_payloads = github.pull_review_comments(
            ref,
            max_items=feedback_fetch_limit,
        )
        issue_comment_payloads = github.issue_comments(
            ref,
            max_items=feedback_fetch_limit,
        )
    except GitHubError as err:
        raise HarvestError(str(err)) from err

    issue_comment_raw_window = _feedback_raw_window(issue_comment_payloads)
    reviews, reviews_summary = _bounded_feedback_items(
        "review",
        review_payloads,
        raw_window=review_raw_window,
    )
    issue_comments, issue_comments_summary = _bounded_feedback_items(
        "issue_comment",
        issue_comment_payloads,
        raw_window=issue_comment_raw_window,
    )
    review_comments, review_comments_summary = _bounded_feedback_items(
        "review_comment",
        review_comment_payloads,
        known_review_ids=known_review_ids,
        dismissed_review_ids=dismissed_review_ids,
        review_state_is_partial=review_state_is_partial,
    )
    commit_statuses, commit_statuses_summary = _bounded_github_items(
        commit_status_payloads,
        MAX_CHECK_ITEMS_PER_SOURCE,
    )
    check_runs, check_runs_summary = _bounded_github_items(
        check_run_payloads,
        MAX_CHECK_ITEMS_PER_SOURCE,
    )
    workflow_runs, workflow_runs_summary = _bounded_github_items(
        workflow_run_payloads,
        MAX_CHECK_ITEMS_PER_SOURCE,
    )
    body_bytes_used = (
        int(issue_comments_summary["body_bytes"])
        + int(review_comments_summary["body_bytes"])
        + int(reviews_summary["body_bytes"])
    )
    feedback_body_bytes_summary: dict[str, int | bool] = {
        "fetched": body_bytes_used,
        "limit": MAX_FEEDBACK_BODY_BYTES_PER_KIND * 3,
        "truncated": (
            bool(issue_comments_summary["truncated"])
            or bool(review_comments_summary["truncated"])
            or bool(reviews_summary["truncated"])
        ),
    }

    pr_html_url = _as_str(pull.get("html_url")) or pr_url
    commits_summary = _count_summary(pull.get("commits"), len(commits))
    changed_files_summary = _count_summary(pull.get("changed_files"), len(files))
    areas = sorted({_area_for_path(_as_str(file.get("filename"))) for file in files})
    latest_feedback_at = _latest_feedback_timestamp(issue_comments, review_comments, reviews)
    latest_commit_at = _latest_commit_timestamp(commits)
    bot_reviews, bot_reviews_summary = _bounded_bot_review_signals(
        cfg,
        ref,
        issue_comment_raw_window,
        captured_at,
    )
    check_failures = _check_failure_signals(
        cfg,
        ref,
        head_sha,
        captured_at,
        latest_feedback_at,
        latest_commit_at,
        commit_statuses,
        check_runs,
        workflow_runs,
    )
    check_failures_summary = {
        "reported": len(check_failures),
        "fetched": len(check_failures),
        "truncated": (
            bool(commit_statuses_summary["truncated"])
            or bool(check_runs_summary["truncated"])
            or bool(workflow_runs_summary["truncated"])
        ),
    }

    signals: list[RawSignal] = [
        _metadata_signal(
            cfg,
            ref,
            pull,
            pr_html_url,
            captured_at,
            areas,
            commits_summary,
            changed_files_summary,
            issue_comments_summary,
            review_comments_summary,
            reviews_summary,
            feedback_body_bytes_summary,
            bot_reviews_summary,
            commit_statuses_summary,
            check_runs_summary,
            workflow_runs_summary,
            check_failures_summary,
        )
    ]
    signals.extend(_commit_signal(cfg, ref, commit, captured_at) for commit in commits)
    signals.extend(
        _changed_file_signal(cfg, ref, pr_html_url, file, captured_at) for file in files
    )

    files_by_path = _files_by_path(files)
    signals.extend(
        _diff_hunk_signal(cfg, ref, pr_html_url, files_by_path, hunk, captured_at)
        for hunk in parse_unified_diff_hunks(diff_text)
    )

    signals.extend(
        _issue_comment_signal(cfg, ref, comment, captured_at) for comment in issue_comments
    )
    signals.extend(
        _review_comment_signal(cfg, ref, comment, captured_at) for comment in review_comments
    )
    signals.extend(_review_signal(cfg, ref, review, captured_at) for review in reviews)
    signals.extend(bot_reviews)
    signals.extend(check_failures)
    return _dedupe_by_source_id(signals)


def parse_unified_diff_hunks(diff_text: str) -> list[ParsedDiffHunk]:
    """Parse file-section hunks from a GitHub unified PR diff."""

    hunks: list[ParsedDiffHunk] = []
    old_path = ""
    new_path = ""
    hunk_lines: list[str] | None = None
    hunk_header: ParsedHunkHeader | None = None

    def finish_hunk() -> None:
        nonlocal hunk_lines, hunk_header
        if hunk_lines is None or hunk_header is None:
            return
        path = new_path or old_path
        if not path:
            raise HarvestError("diff hunk is missing a file path")
        if len(hunks) >= MAX_DIFF_HUNKS:
            raise HarvestError(f"PR diff contains more than {MAX_DIFF_HUNKS} hunks")
        hunks.append(
            ParsedDiffHunk(
                path=path,
                old_path=old_path,
                new_path=new_path,
                old_start=hunk_header.old_start,
                old_count=hunk_header.old_count,
                new_start=hunk_header.new_start,
                new_count=hunk_header.new_count,
                header=hunk_header.header,
                section=hunk_header.section,
                text="".join(hunk_lines),
            )
        )
        hunk_lines = None
        hunk_header = None

    for line in diff_text.splitlines(keepends=True):
        if line.startswith("diff --git "):
            finish_hunk()
            old_path, new_path = _paths_from_diff_header(line)
            continue
        if line.startswith("@@ "):
            finish_hunk()
            hunk_header = _parse_hunk_header(line.rstrip("\r\n"))
            hunk_lines = [line]
            continue
        if hunk_lines is not None:
            hunk_lines.append(line)
            continue
        if line.startswith("--- "):
            old_path = _path_from_file_header(line[4:].rstrip("\r\n"))
            continue
        if line.startswith("+++ "):
            new_path = _path_from_file_header(line[4:].rstrip("\r\n"))

    finish_hunk()
    return hunks


def _parse_and_validate_ref(cfg: RunConfig, pr_url: str) -> PullRequestRef:
    try:
        ref = parse_pull_request_url(pr_url)
    except GitHubError as err:
        raise HarvestError(str(err)) from err
    if ref.repo.casefold() != cfg.repo.casefold():
        raise HarvestError(f"PR URL repo {ref.repo} does not match configured repo {cfg.repo}")
    return ref


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def _validate_diff_size(diff_text: str) -> None:
    diff_bytes = len(diff_text.encode("utf-8"))
    if diff_bytes > MAX_PR_DIFF_BYTES:
        raise HarvestError(
            f"PR diff is {diff_bytes} bytes, exceeding the {MAX_PR_DIFF_BYTES} byte limit"
        )


def _bounded_feedback_items(
    kind: str,
    items: list[dict[str, Any]],
    *,
    raw_window: FeedbackRawWindow | None = None,
    known_review_ids: set[str] | None = None,
    dismissed_review_ids: set[str] | None = None,
    review_state_is_partial: bool = False,
) -> tuple[list[dict[str, Any]], dict[str, int | bool]]:
    raw_window = raw_window or _feedback_raw_window(items)
    processable_items = [
        item
        for item in raw_window.items
        if _is_processable_feedback_item(
            kind,
            item,
            known_review_ids=known_review_ids,
            dismissed_review_ids=dismissed_review_ids,
            review_state_is_partial=review_state_is_partial,
        )
    ]
    kept_newest_first: list[dict[str, Any]] = []
    kept_body_bytes = 0
    body_truncated = False
    body_dropped = 0
    item_truncated = False

    for item in reversed(processable_items):
        if len(kept_newest_first) >= MAX_FEEDBACK_ITEMS_PER_KIND:
            item_truncated = True
            break
        item_body_bytes = len(_as_str(item.get("body")).encode("utf-8"))
        if kept_body_bytes + item_body_bytes > MAX_FEEDBACK_BODY_BYTES_PER_KIND:
            body_truncated = True
            body_dropped += 1
            continue
        kept_newest_first.append(item)
        kept_body_bytes += item_body_bytes
    kept = list(reversed(kept_newest_first))

    summary: dict[str, int | bool] = {
        "reported": len(raw_window.items),
        "processable": len(processable_items),
        "fetched": len(kept),
        "truncated": raw_window.raw_truncated or item_truncated or body_truncated,
        "raw_truncated": raw_window.raw_truncated,
        "item_truncated": item_truncated,
        "raw_item_limit": raw_window.raw_item_limit,
        "item_limit": MAX_FEEDBACK_ITEMS_PER_KIND,
        "body_byte_limit": MAX_FEEDBACK_BODY_BYTES_PER_KIND,
        "body_bytes": kept_body_bytes,
        "duplicates": raw_window.duplicates,
        "dropped": len(raw_window.items) - len(processable_items),
        "body_dropped": body_dropped,
    }
    return kept, summary


def _feedback_raw_window(items: list[dict[str, Any]]) -> FeedbackRawWindow:
    deduped_items = _dedupe_github_payloads_by_id(items)
    raw_item_limit = MAX_FEEDBACK_RAW_ITEMS_PER_KIND
    raw_truncated = len(items) > raw_item_limit or len(deduped_items) > raw_item_limit
    if raw_item_limit <= 0:
        window_items: list[dict[str, Any]] = []
    elif raw_truncated:
        window_items = deduped_items[-raw_item_limit:]
    else:
        window_items = deduped_items
    return FeedbackRawWindow(
        items=window_items,
        duplicates=len(items) - len(deduped_items),
        raw_truncated=raw_truncated,
        raw_item_limit=raw_item_limit,
    )


def _bounded_github_items(
    items: list[dict[str, Any]],
    limit: int,
) -> tuple[list[dict[str, Any]], dict[str, int | bool]]:
    deduped_items = _dedupe_github_payloads_by_id(items)
    kept = deduped_items[:limit]
    return kept, {
        "reported": len(deduped_items),
        "fetched": len(kept),
        "truncated": len(deduped_items) > limit,
        "item_limit": limit,
        "duplicates": len(items) - len(deduped_items),
    }


def _dedupe_github_payloads_by_id(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    deduped: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in items:
        github_id = _as_id(item.get("id"))
        if not github_id:
            deduped.append(item)
            continue
        if github_id in seen:
            continue
        seen.add(github_id)
        deduped.append(item)
    return deduped


def _github_payload_ids(items: list[dict[str, Any]]) -> set[str]:
    return {
        github_id
        for item in items
        for github_id in [_as_id(item.get("id"))]
        if github_id
    }


def _dismissed_review_ids(reviews: list[dict[str, Any]]) -> set[str]:
    return {
        review_id
        for review in reviews
        if _as_str(review.get("state")).upper() == "DISMISSED"
        for review_id in [_as_id(review.get("id"))]
        if review_id
    }


def _is_trusted_feedback_author(item: dict[str, Any]) -> bool:
    author = _login(item.get("user"))
    if _is_trusted_feedback_bot_login(author):
        return True
    association = _as_str(item.get("author_association")).upper()
    return association in TRUSTED_FEEDBACK_AUTHOR_ASSOCIATIONS


def _feedback_drop_reasons(
    kind: str,
    item: dict[str, Any],
    *,
    known_review_ids: set[str] | None = None,
    dismissed_review_ids: set[str] | None = None,
    review_state_is_partial: bool = False,
) -> list[str]:
    reasons: list[str] = []
    if not _is_trusted_feedback_author(item):
        reasons.append("untrusted_author")
    if not _as_str(item.get("body")).strip():
        reasons.append("empty_body")
    if kind == "review" and _as_str(item.get("state")).upper() == "DISMISSED":
        reasons.append("dismissed_review")
    if kind == "review_comment":
        review_id = _as_id(item.get("pull_request_review_id"))
        if review_id in (dismissed_review_ids or set()):
            reasons.append("dismissed_review_comment")
        elif (
            review_id
            and review_state_is_partial
            and known_review_ids is not None
            and review_id not in known_review_ids
        ):
            reasons.append("review_state_unfetched")
    return reasons


def _is_processable_feedback_item(
    kind: str,
    item: dict[str, Any],
    *,
    known_review_ids: set[str] | None = None,
    dismissed_review_ids: set[str] | None = None,
    review_state_is_partial: bool = False,
) -> bool:
    return not _feedback_drop_reasons(
        kind,
        item,
        known_review_ids=known_review_ids,
        dismissed_review_ids=dismissed_review_ids,
        review_state_is_partial=review_state_is_partial,
    )


def _metadata_signal(
    cfg: RunConfig,
    ref: PullRequestRef,
    pull: dict[str, Any],
    pr_html_url: str,
    captured_at: str,
    areas: list[str],
    commits_summary: dict[str, int | bool],
    changed_files_summary: dict[str, int | bool],
    issue_comments_summary: dict[str, int | bool],
    review_comments_summary: dict[str, int | bool],
    reviews_summary: dict[str, int | bool],
    feedback_body_bytes_summary: dict[str, int | bool],
    bot_reviews_summary: dict[str, int | bool],
    commit_statuses_summary: dict[str, int | bool],
    check_runs_summary: dict[str, int | bool],
    workflow_runs_summary: dict[str, int | bool],
    check_failures_summary: dict[str, int | bool],
) -> RawSignal:
    author = _login(pull.get("user"))
    body = _as_str(pull.get("body"))
    return RawSignal(
        kind="pr_metadata",
        source_id=f"pr:{cfg.repo}#{ref.number}",
        source_url=pr_html_url,
        repo=cfg.repo,
        pr_number=ref.number,
        captured_at=captured_at,
        author=author,
        author_association=_as_str(pull.get("author_association")),
        created_at=_as_str(pull.get("created_at")),
        body=body,
        is_bot=_is_bot_login(author),
        raw={
            "number": ref.number,
            "title": _as_str(pull.get("title")),
            "body": body,
            "author": author,
            "author_association": _as_str(pull.get("author_association")),
            "labels": _labels(pull),
            "requested_reviewers": _requested_reviewers(pull),
            "requested_teams": _requested_teams(pull),
            "branches": {
                "head": _branch_info(pull.get("head")),
                "base": _branch_info(pull.get("base")),
            },
            "shas": {
                "head": _nested_str(pull, "head", "sha"),
                "base": _nested_str(pull, "base", "sha"),
                "merge_commit": _as_str(pull.get("merge_commit_sha")),
            },
            "timestamps": {
                "created_at": _as_str(pull.get("created_at")),
                "updated_at": _as_str(pull.get("updated_at")),
                "closed_at": _as_str(pull.get("closed_at")),
                "merged_at": _as_str(pull.get("merged_at")),
            },
            "merged_by": _login(pull.get("merged_by")),
            "state": _as_str(pull.get("state")),
            "html_url": pr_html_url,
            "areas": areas,
            "commits": commits_summary,
            "changed_files": changed_files_summary,
            "issue_comments": issue_comments_summary,
            "review_comments": review_comments_summary,
            "reviews": reviews_summary,
            "feedback_body_bytes": feedback_body_bytes_summary,
            "bot_reviews": bot_reviews_summary,
            "commit_statuses": commit_statuses_summary,
            "check_runs": check_runs_summary,
            "workflow_runs": workflow_runs_summary,
            "check_failures": check_failures_summary,
        },
    )


def _commit_signal(
    cfg: RunConfig,
    ref: PullRequestRef,
    commit: dict[str, Any],
    captured_at: str,
) -> RawSignal:
    sha = _as_str(commit.get("sha"))
    if not sha:
        raise HarvestError(f"commit payload for {cfg.repo}#{ref.number} is missing sha")
    commit_obj = _as_dict(commit.get("commit"))
    author_obj = _as_dict(commit_obj.get("author"))
    committer_obj = _as_dict(commit_obj.get("committer"))
    author = _login(commit.get("author")) or _as_str(author_obj.get("name"))
    message = _as_str(commit_obj.get("message"))
    return RawSignal(
        kind="commit",
        source_id=f"commit:{sha}",
        source_url=_as_str(commit.get("html_url")),
        repo=cfg.repo,
        pr_number=ref.number,
        captured_at=captured_at,
        author=author,
        created_at=_as_str(author_obj.get("date")) or _as_str(committer_obj.get("date")),
        body=message,
        is_bot=_is_bot_login(author),
        raw={
            "sha": sha,
            "html_url": _as_str(commit.get("html_url")),
            "message": message,
            "author": {
                "login": _login(commit.get("author")),
                "name": _as_str(author_obj.get("name")),
                "email": _as_str(author_obj.get("email")),
                "date": _as_str(author_obj.get("date")),
            },
            "committer": {
                "login": _login(commit.get("committer")),
                "name": _as_str(committer_obj.get("name")),
                "email": _as_str(committer_obj.get("email")),
                "date": _as_str(committer_obj.get("date")),
            },
            "parents": [
                _as_str(parent.get("sha"))
                for parent in commit.get("parents", [])
                if isinstance(parent, dict) and _as_str(parent.get("sha"))
            ],
        },
    )


def _changed_file_signal(
    cfg: RunConfig,
    ref: PullRequestRef,
    pr_html_url: str,
    file: dict[str, Any],
    captured_at: str,
) -> RawSignal:
    filename = _as_str(file.get("filename"))
    if not filename:
        raise HarvestError(f"file payload for {cfg.repo}#{ref.number} is missing filename")
    area = _area_for_path(filename)
    source_url = _as_str(file.get("blob_url")) or pr_html_url
    return RawSignal(
        kind="changed_file",
        source_id=f"file:{cfg.repo}#{ref.number}:{filename}",
        source_url=source_url,
        repo=cfg.repo,
        pr_number=ref.number,
        captured_at=captured_at,
        path=filename,
        raw={
            "filename": filename,
            "previous_filename": _as_str(file.get("previous_filename")),
            "status": _as_str(file.get("status")),
            "additions": _as_int(file.get("additions")),
            "deletions": _as_int(file.get("deletions")),
            "changes": _as_int(file.get("changes")),
            "sha": _as_str(file.get("sha")),
            "blob_url": _as_str(file.get("blob_url")),
            "raw_url": _as_str(file.get("raw_url")),
            "contents_url": _as_str(file.get("contents_url")),
            "area": area,
            "patch_present": "patch" in file and file.get("patch") is not None,
        },
    )


def _diff_hunk_signal(
    cfg: RunConfig,
    ref: PullRequestRef,
    pr_html_url: str,
    files_by_path: dict[str, dict[str, Any]],
    hunk: ParsedDiffHunk,
    captured_at: str,
) -> RawSignal:
    file = files_by_path.get(hunk.path, {})
    return RawSignal(
        kind="diff_hunk",
        source_id=(
            f"hunk:{cfg.repo}#{ref.number}:{hunk.path}:"
            f"{hunk.old_start},{hunk.old_count}->{hunk.new_start},{hunk.new_count}"
        ),
        source_url=_hunk_source_url(pr_html_url, file, hunk),
        repo=cfg.repo,
        pr_number=ref.number,
        captured_at=captured_at,
        body=hunk.text,
        path=hunk.path,
        line=hunk.new_start if hunk.new_count > 0 else None,
        raw={
            "path": hunk.path,
            "old_path": hunk.old_path,
            "new_path": hunk.new_path,
            "old_start": hunk.old_start,
            "old_count": hunk.old_count,
            "new_start": hunk.new_start,
            "new_count": hunk.new_count,
            "hunk_header": hunk.header,
            "section": hunk.section,
            "area": _area_for_path(hunk.path),
            "file_status": _as_str(file.get("status")),
        },
    )


def _issue_comment_signal(
    cfg: RunConfig,
    ref: PullRequestRef,
    comment: dict[str, Any],
    captured_at: str,
) -> RawSignal:
    return _feedback_item_signal(
        cfg,
        ref,
        "issue_comment",
        comment,
        captured_at,
        github_id=_required_github_id(comment.get("id"), "issue_comment payload", cfg, ref),
        path=None,
        line=None,
    )


def _review_comment_signal(
    cfg: RunConfig,
    ref: PullRequestRef,
    comment: dict[str, Any],
    captured_at: str,
) -> RawSignal:
    path = _as_str(comment.get("path")) or None
    line = _as_optional_int(comment.get("line"))
    if line is None:
        line = _as_optional_int(comment.get("original_line"))
    return _feedback_item_signal(
        cfg,
        ref,
        "review_comment",
        comment,
        captured_at,
        github_id=_required_github_id(comment.get("id"), "review_comment payload", cfg, ref),
        path=path,
        line=line,
        raw_extra={
            "github_line": _as_optional_int(comment.get("line")),
            "original_line": _as_optional_int(comment.get("original_line")),
            "in_reply_to_id": _as_id(comment.get("in_reply_to_id")),
            "pull_request_review_id": _as_id(comment.get("pull_request_review_id")),
        },
    )


def _review_signal(
    cfg: RunConfig,
    ref: PullRequestRef,
    review: dict[str, Any],
    captured_at: str,
) -> RawSignal:
    return _feedback_item_signal(
        cfg,
        ref,
        "review",
        review,
        captured_at,
        github_id=_required_github_id(review.get("id"), "review payload", cfg, ref),
        created_at=_as_str(review.get("submitted_at")) or _as_str(review.get("created_at")),
        path=None,
        line=None,
        raw_extra={
            "submitted_at": _as_str(review.get("submitted_at")),
            "github_created_at": _as_str(review.get("created_at")),
            "state": _as_str(review.get("state")),
        },
    )


def _bot_review_signals(
    cfg: RunConfig,
    ref: PullRequestRef,
    issue_comments: list[dict[str, Any]],
    captured_at: str,
) -> list[RawSignal]:
    signals: list[RawSignal] = []
    for comment in issue_comments:
        provider = _bot_review_provider(comment)
        if provider is None:
            continue
        provider_name, match_reason = provider
        body = _as_str(comment.get("body"))
        reviewed_range = (
            _codex_reviewed_commit_range(body)
            if provider_name == "codex_security_review"
            else ""
        )
        base_sha, head_sha = _split_commit_range(reviewed_range)
        signals.append(
            _feedback_item_signal(
                cfg,
                ref,
                "bot_review",
                comment,
                captured_at,
                github_id=_required_github_id(comment.get("id"), "bot_review payload", cfg, ref),
                path=None,
                line=None,
                raw_extra={
                    "provider": provider_name,
                    "match_reason": match_reason,
                    "reviewed_commit_range": reviewed_range,
                    "reviewed_base_sha": base_sha,
                    "reviewed_head_sha": head_sha,
                    "workflow_run_url": _first_github_actions_run_url(body),
                },
            )
        )
    return signals


def _bounded_bot_review_signals(
    cfg: RunConfig,
    ref: PullRequestRef,
    raw_window: FeedbackRawWindow,
    captured_at: str,
) -> tuple[list[RawSignal], dict[str, int | bool]]:
    bot_comments = [
        comment for comment in raw_window.items if _bot_review_provider(comment) is not None
    ]
    kept_newest_first: list[dict[str, Any]] = []
    kept_body_bytes = 0
    body_truncated = False
    body_dropped = 0
    item_truncated = False

    for comment in reversed(bot_comments):
        if len(kept_newest_first) >= MAX_BOT_REVIEW_ITEMS:
            item_truncated = True
            break
        body_bytes = len(_as_str(comment.get("body")).encode("utf-8"))
        if kept_body_bytes + body_bytes > MAX_BOT_REVIEW_BODY_BYTES:
            body_truncated = True
            body_dropped += 1
            continue
        kept_newest_first.append(comment)
        kept_body_bytes += body_bytes

    kept_comments = list(reversed(kept_newest_first))
    signals = _bot_review_signals(cfg, ref, kept_comments, captured_at)
    return signals, _bot_reviews_summary(
        signals,
        reported=len(bot_comments),
        raw_window=raw_window,
        item_truncated=item_truncated,
        body_truncated=body_truncated,
        body_bytes=kept_body_bytes,
        body_dropped=body_dropped,
    )


def _bot_reviews_summary(
    signals: list[RawSignal],
    *,
    reported: int,
    raw_window: FeedbackRawWindow,
    item_truncated: bool,
    body_truncated: bool,
    body_bytes: int,
    body_dropped: int,
) -> dict[str, int | bool]:
    provider_counts: dict[str, int] = {}
    for signal in signals:
        provider = _as_str(signal.raw.get("provider"))
        if provider:
            provider_counts[provider] = provider_counts.get(provider, 0) + 1
    return {
        "reported": reported,
        "fetched": len(signals),
        "truncated": raw_window.raw_truncated or item_truncated or body_truncated,
        "raw_truncated": raw_window.raw_truncated,
        "item_truncated": item_truncated,
        "raw_item_limit": raw_window.raw_item_limit,
        "item_limit": MAX_BOT_REVIEW_ITEMS,
        "body_byte_limit": MAX_BOT_REVIEW_BODY_BYTES,
        "body_bytes": body_bytes,
        "dropped": reported - len(signals),
        "body_dropped": body_dropped,
        "codex_security_review": provider_counts.get("codex_security_review", 0),
        "builderbot": provider_counts.get("builderbot", 0),
    }


def _bot_review_provider(comment: dict[str, Any]) -> tuple[str, str] | None:
    author = _login(comment.get("user"))
    body = _as_str(comment.get("body"))
    normalized_author = author.casefold()
    if (
        normalized_author in CODEX_SECURITY_REVIEW_BOT_LOGINS
        and CODEX_SECURITY_REVIEW_MARKER in body
    ):
        return "codex_security_review", "codex-security-review-marker"
    if normalized_author in BUILDERBOT_BOT_LOGINS:
        return "builderbot", "builderbot-author"
    return None


def _codex_reviewed_commit_range(body: str) -> str:
    match = _CODEX_REVIEWED_RANGE_RE.search(body)
    return match.group(1) if match else ""


def _split_commit_range(commit_range: str) -> tuple[str, str]:
    for separator in ("...", ".."):
        if separator in commit_range:
            base_sha, head_sha = commit_range.split(separator, 1)
            return base_sha, head_sha
    return "", ""


def _first_github_actions_run_url(body: str) -> str:
    match = _GITHUB_ACTIONS_RUN_RE.search(body)
    return match.group(0) if match else ""


def _check_failure_signals(
    cfg: RunConfig,
    ref: PullRequestRef,
    head_sha: str,
    captured_at: str,
    latest_feedback_at: str,
    latest_commit_at: str,
    commit_statuses: list[dict[str, Any]],
    check_runs: list[dict[str, Any]],
    workflow_runs: list[dict[str, Any]],
) -> list[RawSignal]:
    signals: list[RawSignal] = []
    signals.extend(
        _commit_status_signal(
            cfg,
            ref,
            head_sha,
            status,
            captured_at,
            latest_feedback_at,
            latest_commit_at,
        )
        for status in commit_statuses
        if _is_failed_commit_status(status)
    )
    signals.extend(
        _check_run_signal(
            cfg,
            ref,
            head_sha,
            run,
            captured_at,
            latest_feedback_at,
            latest_commit_at,
        )
        for run in check_runs
        if _is_failed_check_payload(run)
    )
    signals.extend(
        _workflow_run_signal(
            cfg,
            ref,
            head_sha,
            run,
            captured_at,
            latest_feedback_at,
            latest_commit_at,
        )
        for run in workflow_runs
        if _is_failed_check_payload(run)
    )
    return signals


def _commit_status_signal(
    cfg: RunConfig,
    ref: PullRequestRef,
    head_sha: str,
    status: dict[str, Any],
    captured_at: str,
    latest_feedback_at: str,
    latest_commit_at: str,
) -> RawSignal:
    status_id = _required_github_id(status.get("id"), "commit_status payload", cfg, ref)
    name = _as_str(status.get("context"))
    state = _as_str(status.get("state"))
    source_url = _as_str(status.get("target_url"))
    created_at = _as_str(status.get("created_at"))
    completed_at = _as_str(status.get("updated_at")) or created_at
    return _check_signal(
        cfg,
        ref,
        head_sha,
        captured_at,
        source="commit_status",
        github_id=status_id,
        name=name,
        status=state,
        conclusion=state,
        source_url=source_url,
        author=_login(status.get("creator")),
        created_at=completed_at,
        raw_extra={
            "description": _as_str(status.get("description")),
            "created_at": created_at,
            "completed_at": completed_at,
            "timing": _check_timing(
                "",
                completed_at,
                created_at,
                latest_feedback_at,
                latest_commit_at,
            ),
        },
    )


def _check_run_signal(
    cfg: RunConfig,
    ref: PullRequestRef,
    head_sha: str,
    run: dict[str, Any],
    captured_at: str,
    latest_feedback_at: str,
    latest_commit_at: str,
) -> RawSignal:
    run_id = _required_github_id(run.get("id"), "check_run payload", cfg, ref)
    name = _as_str(run.get("name"))
    status = _as_str(run.get("status"))
    conclusion = _as_str(run.get("conclusion"))
    source_url = _as_str(run.get("html_url")) or _as_str(run.get("details_url"))
    started_at = _as_str(run.get("started_at"))
    completed_at = _as_str(run.get("completed_at"))
    return _check_signal(
        cfg,
        ref,
        head_sha,
        captured_at,
        source="check_run",
        github_id=run_id,
        name=name,
        status=status,
        conclusion=conclusion,
        source_url=source_url,
        author=_github_app_name(run.get("app")),
        created_at=completed_at or started_at,
        raw_extra={
            "started_at": started_at,
            "completed_at": completed_at,
            "timing": _check_timing(
                started_at,
                completed_at,
                "",
                latest_feedback_at,
                latest_commit_at,
            ),
        },
    )


def _workflow_run_signal(
    cfg: RunConfig,
    ref: PullRequestRef,
    head_sha: str,
    run: dict[str, Any],
    captured_at: str,
    latest_feedback_at: str,
    latest_commit_at: str,
) -> RawSignal:
    run_id = _required_github_id(run.get("id"), "workflow_run payload", cfg, ref)
    name = _as_str(run.get("name"))
    status = _as_str(run.get("status"))
    conclusion = _as_str(run.get("conclusion"))
    source_url = _as_str(run.get("html_url"))
    created_at = _as_str(run.get("created_at"))
    started_at = _as_str(run.get("run_started_at"))
    completed_at = _as_str(run.get("updated_at")) if status == "completed" else ""
    return _check_signal(
        cfg,
        ref,
        head_sha,
        captured_at,
        source="workflow_run",
        github_id=run_id,
        name=name,
        status=status,
        conclusion=conclusion,
        source_url=source_url,
        author=_login(run.get("actor")),
        created_at=completed_at or started_at or created_at,
        raw_extra={
            "event": _as_str(run.get("event")),
            "created_at": created_at,
            "started_at": started_at,
            "completed_at": completed_at,
            "timing": _check_timing(
                started_at,
                completed_at,
                created_at,
                latest_feedback_at,
                latest_commit_at,
            ),
        },
    )


def _check_signal(
    cfg: RunConfig,
    ref: PullRequestRef,
    head_sha: str,
    captured_at: str,
    *,
    source: str,
    github_id: str,
    name: str,
    status: str,
    conclusion: str,
    source_url: str,
    author: str,
    created_at: str,
    raw_extra: dict[str, Any],
) -> RawSignal:
    raw = {
        "kind": "check",
        "source": source,
        "id": github_id,
        "name": name,
        "status": status,
        "conclusion": conclusion,
        "url": source_url,
        "sha": head_sha,
    }
    raw.update(raw_extra)
    return RawSignal(
        kind="check",
        source_id=f"check:{source}:{cfg.repo}#{ref.number}:{github_id}",
        source_url=source_url,
        repo=cfg.repo,
        pr_number=ref.number,
        captured_at=captured_at,
        author=author,
        created_at=created_at,
        body=_check_body(name, status, conclusion, source_url),
        is_bot=True,
        raw=raw,
    )


def _feedback_item_signal(
    cfg: RunConfig,
    ref: PullRequestRef,
    kind: str,
    item: dict[str, Any],
    captured_at: str,
    *,
    github_id: str,
    created_at: str | None = None,
    path: str | None,
    line: int | None,
    raw_extra: dict[str, Any] | None = None,
) -> RawSignal:
    author = _login(item.get("user"))
    source_url = _as_str(item.get("html_url"))
    created_at = created_at if created_at is not None else _as_str(item.get("created_at"))
    drop_reasons = _feedback_drop_reasons(kind, item)
    raw = {
        "kind": kind,
        "id": github_id,
        "author": author,
        "author_association": _as_str(item.get("author_association")),
        "is_trusted_author": "untrusted_author" not in drop_reasons,
        "is_processable": not drop_reasons,
        "drop_reasons": drop_reasons,
        "created_at": created_at,
        "body": _as_str(item.get("body")),
        "path": path,
        "line": line,
        "url": source_url,
    }
    if raw_extra:
        raw.update(raw_extra)
    return RawSignal(
        kind=kind,
        source_id=f"{kind}:{cfg.repo}#{ref.number}:{github_id}",
        source_url=source_url,
        repo=cfg.repo,
        pr_number=ref.number,
        captured_at=captured_at,
        author=author,
        author_association=raw["author_association"],
        created_at=created_at,
        body=raw["body"],
        path=path,
        line=line,
        is_bot=_is_bot_login(author),
        raw=raw,
    )


def _is_failed_commit_status(status: dict[str, Any]) -> bool:
    return _as_str(status.get("state")).casefold() in _FAILED_STATUS_STATES


def _is_failed_check_payload(payload: dict[str, Any]) -> bool:
    return _as_str(payload.get("conclusion")).casefold() in _FAILED_CHECK_CONCLUSIONS


def _check_body(name: str, status: str, conclusion: str, source_url: str) -> str:
    outcome = conclusion or status
    parts = [part for part in (name, outcome, source_url) if part]
    return " | ".join(parts)


def _check_timing(
    started_at: str,
    completed_at: str,
    created_at: str,
    latest_feedback_at: str,
    latest_commit_at: str,
) -> dict[str, str | bool]:
    event_at = completed_at or started_at or created_at
    return {
        "latest_feedback_at": latest_feedback_at,
        "latest_commit_at": latest_commit_at,
        "event_at": event_at,
        "event_after_latest_feedback": _timestamp_after(event_at, latest_feedback_at),
        "event_after_latest_commit": _timestamp_after(event_at, latest_commit_at),
    }


def _latest_feedback_timestamp(
    issue_comments: list[dict[str, Any]],
    review_comments: list[dict[str, Any]],
    reviews: list[dict[str, Any]],
) -> str:
    timestamps: list[str] = []
    timestamps.extend(_as_str(comment.get("created_at")) for comment in issue_comments)
    timestamps.extend(_as_str(comment.get("created_at")) for comment in review_comments)
    timestamps.extend(
        _as_str(review.get("submitted_at")) or _as_str(review.get("created_at"))
        for review in reviews
    )
    return _latest_timestamp(timestamps)


def _latest_commit_timestamp(commits: list[dict[str, Any]]) -> str:
    timestamps: list[str] = []
    for commit in commits:
        commit_obj = _as_dict(commit.get("commit"))
        timestamps.append(_as_str(_as_dict(commit_obj.get("author")).get("date")))
        timestamps.append(_as_str(_as_dict(commit_obj.get("committer")).get("date")))
    return _latest_timestamp(timestamps)


def _latest_timestamp(timestamps: list[str]) -> str:
    latest: datetime | None = None
    latest_text = ""
    for timestamp in timestamps:
        parsed = _parse_timestamp(timestamp)
        if parsed is None:
            continue
        if latest is None or parsed > latest:
            latest = parsed
            latest_text = timestamp
    return latest_text


def _timestamp_after(timestamp: str, reference: str) -> bool:
    parsed_timestamp = _parse_timestamp(timestamp)
    parsed_reference = _parse_timestamp(reference)
    if parsed_timestamp is None or parsed_reference is None:
        return False
    return parsed_timestamp > parsed_reference


def _parse_timestamp(timestamp: str) -> datetime | None:
    if not timestamp:
        return None
    try:
        parsed = datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def _parse_backfill_bound(value: str | None, *, end_of_day: bool) -> datetime | None:
    if not value:
        return None
    parsed = _parse_timestamp(value)
    if parsed is None:
        raise HarvestError(f"invalid backfill timestamp: {value}")
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        if end_of_day:
            return parsed.replace(hour=23, minute=59, second=59, microsecond=999999)
        return parsed.replace(hour=0, minute=0, second=0, microsecond=0)
    return parsed


def _github_app_name(value: Any) -> str:
    app = _as_dict(value)
    return _as_str(app.get("slug")) or _as_str(app.get("name"))


def _dedupe_by_source_id(signals: list[RawSignal]) -> list[RawSignal]:
    deduped: list[RawSignal] = []
    seen: set[str] = set()
    for signal in signals:
        if signal.source_id in seen:
            continue
        seen.add(signal.source_id)
        deduped.append(signal)
    return deduped


def _parse_hunk_header(header: str) -> ParsedHunkHeader:
    match = _HUNK_HEADER_RE.match(header)
    if not match:
        raise HarvestError(f"unable to parse diff hunk header: {header}")
    old_count = match.group("old_count")
    new_count = match.group("new_count")
    return ParsedHunkHeader(
        old_start=int(match.group("old_start")),
        old_count=int(old_count) if old_count is not None else 1,
        new_start=int(match.group("new_start")),
        new_count=int(new_count) if new_count is not None else 1,
        header=header,
        section=match.group("section").strip(),
    )


def _paths_from_diff_header(header: str) -> tuple[str, str]:
    try:
        parts = shlex.split(header.strip())
    except ValueError:
        return "", ""
    if len(parts) < 4:
        return "", ""
    return _strip_diff_path(parts[2]), _strip_diff_path(parts[3])


def _path_from_file_header(path: str) -> str:
    return _strip_diff_path(path.split("\t", 1)[0])


def _strip_diff_path(path: str) -> str:
    path = _unquote_diff_path(path)
    if path == "/dev/null":
        return ""
    if path.startswith(("a/", "b/")):
        return path[2:]
    return path


def _unquote_diff_path(path: str) -> str:
    if len(path) < 2 or not (path.startswith('"') and path.endswith('"')):
        return path
    raw = path[1:-1]
    decoded = bytearray()
    idx = 0
    while idx < len(raw):
        char = raw[idx]
        if char != "\\":
            decoded.extend(char.encode("utf-8"))
            idx += 1
            continue

        idx += 1
        if idx >= len(raw):
            decoded.append(ord("\\"))
            break

        escaped = raw[idx]
        if escaped in "01234567":
            octal = escaped
            idx += 1
            while idx < len(raw) and len(octal) < 3 and raw[idx] in "01234567":
                octal += raw[idx]
                idx += 1
            decoded.append(int(octal, 8))
            continue

        decoded.extend(_decode_c_escape(escaped))
        idx += 1

    try:
        return decoded.decode("utf-8")
    except UnicodeDecodeError:
        return raw


def _decode_c_escape(escaped: str) -> bytes:
    escapes = {
        "a": b"\a",
        "b": b"\b",
        "f": b"\f",
        "n": b"\n",
        "r": b"\r",
        "t": b"\t",
        "v": b"\v",
        "\\": b"\\",
        '"': b'"',
    }
    return escapes.get(escaped, escaped.encode("utf-8"))


def _files_by_path(files: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    by_path: dict[str, dict[str, Any]] = {}
    for file in files:
        filename = _as_str(file.get("filename"))
        if filename:
            by_path[filename] = file
        previous_filename = _as_str(file.get("previous_filename"))
        if previous_filename:
            by_path[previous_filename] = file
    return by_path


def _hunk_source_url(pr_html_url: str, file: dict[str, Any], hunk: ParsedDiffHunk) -> str:
    blob_url = _as_str(file.get("blob_url"))
    if not blob_url or hunk.new_start <= 0 or hunk.new_count <= 0:
        return pr_html_url
    end = hunk.new_start + hunk.new_count - 1
    if end == hunk.new_start:
        return f"{blob_url}#L{hunk.new_start}"
    return f"{blob_url}#L{hunk.new_start}-L{end}"


def _area_for_path(path: str) -> str:
    for prefix, area in AREA_PREFIXES:
        if path.startswith(prefix):
            return area
    return "repo"


def _branch_info(value: Any) -> dict[str, str]:
    branch = _as_dict(value)
    repo = _as_dict(branch.get("repo"))
    return {
        "ref": _as_str(branch.get("ref")),
        "sha": _as_str(branch.get("sha")),
        "repo": _as_str(repo.get("full_name")),
    }


def _labels(pull: dict[str, Any]) -> list[str]:
    labels = pull.get("labels", [])
    if not isinstance(labels, list):
        return []
    return [
        _as_str(label.get("name"))
        for label in labels
        if isinstance(label, dict) and _as_str(label.get("name"))
    ]


def _requested_reviewers(pull: dict[str, Any]) -> list[str]:
    reviewers = pull.get("requested_reviewers", [])
    if not isinstance(reviewers, list):
        return []
    requested_reviewers: list[str] = []
    for reviewer in reviewers:
        login = _login(reviewer)
        if login:
            requested_reviewers.append(login)
    return requested_reviewers


def _requested_teams(pull: dict[str, Any]) -> list[str]:
    teams = pull.get("requested_teams", [])
    if not isinstance(teams, list):
        return []
    requested_teams: list[str] = []
    for team in teams:
        team_data = _as_dict(team)
        team_name = _as_str(team_data.get("slug")) or _as_str(team_data.get("name"))
        if team_name:
            requested_teams.append(team_name)
    return requested_teams


def _count_summary(reported_value: Any, fetched: int) -> dict[str, int | bool]:
    reported = _as_int(reported_value)
    return {
        "reported": reported,
        "fetched": fetched,
        "truncated": reported > fetched,
    }


def _nested_str(value: dict[str, Any], key: str, nested_key: str) -> str:
    return _as_str(_as_dict(value.get(key)).get(nested_key))


def _login(value: Any) -> str:
    return _as_str(_as_dict(value).get("login"))


def _is_bot_login(login: str) -> bool:
    normalized = login.lower()
    return (
        normalized.endswith("[bot]")
        or normalized.endswith("-bot")
        or normalized == "copilot"
    )


def _is_trusted_feedback_bot_login(login: str) -> bool:
    normalized = login.lower()
    return normalized.endswith("[bot]") or normalized in TRUSTED_FEEDBACK_BOT_LOGINS


def _required_github_id(
    value: Any,
    payload_name: str,
    cfg: RunConfig,
    ref: PullRequestRef,
) -> str:
    github_id = _as_id(value)
    if not github_id:
        raise HarvestError(f"{payload_name} for {cfg.repo}#{ref.number} is missing id")
    return github_id


def _as_dict(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _as_str(value: Any) -> str:
    return value if isinstance(value, str) else ""


def _as_id(value: Any) -> str:
    if isinstance(value, bool):
        return ""
    if isinstance(value, int):
        return str(value)
    return value if isinstance(value, str) else ""


def _as_int(value: Any) -> int:
    if isinstance(value, bool):
        return 0
    return value if isinstance(value, int) else 0


def _as_optional_int(value: Any) -> int | None:
    if isinstance(value, bool):
        return None
    return value if isinstance(value, int) else None

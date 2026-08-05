"""Owned GitHub helpers for feedback-loop harvesting.

This module intentionally keeps the GitHub subprocess boundary small and explicit. The feedback loop
uses GitHub as the re-fetchable source of record, but the rest of the pipeline should only see typed
pull request references plus validated JSON/text payloads.
"""

from __future__ import annotations

from dataclasses import dataclass
import json
from json import JSONDecodeError
import re
import shlex
import subprocess
from typing import Any
from urllib.parse import urlparse


_TAIL_FETCH_EXTRA_PAGES = 2


class GitHubError(Exception):
    """Raised when a GitHub URL, command, or response is invalid for harvest."""


@dataclass(frozen=True)
class PullRequestRef:
    hostname: str
    owner: str
    name: str
    number: int

    @property
    def repo(self) -> str:
        return f"{self.owner}/{self.name}"


_PULL_PATH_RE = re.compile(r"^/([^/]+)/([^/]+)/pull/([1-9][0-9]*)/?$")


def parse_pull_request_url(pr_url: str) -> PullRequestRef:
    """Parse a full GitHub pull request URL.

    Only the canonical web URL form is accepted:
    https://github.com/{owner}/{repo}/pull/{number}
    """

    parsed = urlparse(pr_url)
    hostname = parsed.netloc.lower()
    if parsed.scheme != "https" or hostname != "github.com":
        raise GitHubError(
            "expected a full GitHub PR URL like https://github.com/OWNER/REPO/pull/123"
        )
    match = _PULL_PATH_RE.match(parsed.path)
    if not match:
        raise GitHubError(
            "expected a full GitHub PR URL like https://github.com/OWNER/REPO/pull/123"
        )
    owner, name, number = match.groups()
    return PullRequestRef(hostname=hostname, owner=owner, name=name, number=int(number))


class GitHubClient:
    """Small `gh` CLI wrapper used by the harvest stage."""

    def closed_pull_requests(
        self,
        repo: str,
        *,
        max_items: int | None = None,
    ) -> list[dict[str, Any]]:
        ref = _repo_ref(repo)
        return self._api_paginated_list(
            ref,
            f"repos/{ref.repo}/pulls",
            query_fields=[
                "state=closed",
                "sort=updated",
                "direction=desc",
            ],
            max_items=max_items,
        )

    def closed_pull_requests_page(
        self,
        repo: str,
        *,
        page: int,
        per_page: int = 100,
    ) -> list[dict[str, Any]]:
        if page <= 0:
            raise GitHubError("closed pull request page must be positive")
        if per_page <= 0 or per_page > 100:
            raise GitHubError("closed pull request per_page must be between 1 and 100")

        ref = _repo_ref(repo)
        endpoint = f"repos/{ref.repo}/pulls"
        data = self._api_json(
            ref,
            endpoint,
            extra_args=[
                "--method",
                "GET",
                "-f",
                f"per_page={per_page}",
                "-f",
                f"page={page}",
                "-f",
                "state=closed",
                "-f",
                "sort=updated",
                "-f",
                "direction=desc",
            ],
        )
        return self._validated_page_items(data, endpoint, page)

    def pull_metadata(self, ref: PullRequestRef) -> dict[str, Any]:
        data = self._api_json(ref, f"repos/{ref.repo}/pulls/{ref.number}")
        if not isinstance(data, dict):
            raise GitHubError(
                f"GitHub returned non-object pull metadata for {ref.repo}#{ref.number}"
            )
        return data

    def pull_commits(self, ref: PullRequestRef) -> list[dict[str, Any]]:
        return self._api_paginated_list(ref, f"repos/{ref.repo}/pulls/{ref.number}/commits")

    def pull_files(self, ref: PullRequestRef) -> list[dict[str, Any]]:
        return self._api_paginated_list(ref, f"repos/{ref.repo}/pulls/{ref.number}/files")

    def issue_comments(
        self,
        ref: PullRequestRef,
        *,
        max_items: int | None = None,
    ) -> list[dict[str, Any]]:
        return self._feedback_items(
            ref,
            f"repos/{ref.repo}/issues/{ref.number}/comments",
            max_items=max_items,
        )

    def pull_review_comments(
        self,
        ref: PullRequestRef,
        *,
        max_items: int | None = None,
    ) -> list[dict[str, Any]]:
        return self._feedback_items(
            ref,
            f"repos/{ref.repo}/pulls/{ref.number}/comments",
            max_items=max_items,
        )

    def pull_reviews(
        self,
        ref: PullRequestRef,
        *,
        max_items: int | None = None,
    ) -> list[dict[str, Any]]:
        return self._feedback_items(
            ref,
            f"repos/{ref.repo}/pulls/{ref.number}/reviews",
            max_items=max_items,
        )

    def _feedback_items(
        self,
        ref: PullRequestRef,
        endpoint: str,
        *,
        max_items: int | None,
    ) -> list[dict[str, Any]]:
        if max_items is not None:
            return self._api_paginated_tail_list(
                ref,
                endpoint,
                max_items=max_items,
            )
        return self._api_paginated_list(ref, endpoint)

    def commit_statuses(
        self,
        ref: PullRequestRef,
        sha: str,
        *,
        max_items: int | None = None,
    ) -> list[dict[str, Any]]:
        return self._api_paginated_object_list(
            ref,
            f"repos/{ref.repo}/commits/{sha}/status",
            "statuses",
            max_items=max_items,
            unique_by_id=True,
        )

    def check_runs(
        self,
        ref: PullRequestRef,
        sha: str,
        *,
        max_items: int | None = None,
    ) -> list[dict[str, Any]]:
        return self._api_paginated_object_list(
            ref,
            f"repos/{ref.repo}/commits/{sha}/check-runs",
            "check_runs",
            max_items=max_items,
        )

    def workflow_runs_for_sha(
        self,
        ref: PullRequestRef,
        sha: str,
        *,
        max_items: int | None = None,
    ) -> list[dict[str, Any]]:
        return self._api_paginated_object_list(
            ref,
            f"repos/{ref.repo}/actions/runs",
            "workflow_runs",
            query_fields=[f"head_sha={sha}"],
            max_items=max_items,
        )

    def pull_diff(self, ref: PullRequestRef) -> str:
        return self._run_gh(
            [
                "api",
                "--hostname",
                ref.hostname,
                f"repos/{ref.repo}/pulls/{ref.number}",
                "--header",
                "Accept: application/vnd.github.v3.diff",
            ]
        )

    def _api_paginated_list(
        self,
        ref: PullRequestRef,
        endpoint: str,
        *,
        query_fields: list[str] | None = None,
        max_items: int | None = None,
        unique_by_id: bool = False,
    ) -> list[dict[str, Any]]:
        per_page = 100
        page = 1
        all_items: list[dict[str, Any]] = []
        seen_ids: set[str] = set()
        while True:
            fields = [
                f"per_page={per_page}",
                f"page={page}",
                *(query_fields or []),
            ]
            data = self._api_json(
                ref,
                endpoint,
                extra_args=[
                    "--method",
                    "GET",
                    *[arg for field in fields for arg in ("-f", field)],
                ],
            )
            page_items = self._validated_page_items(data, endpoint, page)
            for item in page_items:
                unique_key = _github_payload_id_key(item) if unique_by_id else None
                if unique_key is not None:
                    if unique_key in seen_ids:
                        continue
                    seen_ids.add(unique_key)
                all_items.append(item)
                if max_items is not None:
                    if len(all_items) >= max_items:
                        return all_items
            if len(page_items) < per_page:
                return all_items
            page += 1

    def _api_paginated_tail_list(
        self,
        ref: PullRequestRef,
        endpoint: str,
        *,
        max_items: int,
        unique_by_id: bool = False,
    ) -> list[dict[str, Any]]:
        if max_items <= 0:
            return []

        per_page = 100
        first_data, headers = self._api_json_with_headers(
            ref,
            endpoint,
            extra_args=[
                "--method",
                "GET",
                "-f",
                f"per_page={per_page}",
                "-f",
                "page=1",
            ],
        )
        first_page = self._validated_page_items(first_data, endpoint, 1)
        last_page = _last_page_from_link_header(headers.get("link", "")) or 1
        if last_page <= 1:
            return _newest_items(
                first_page,
                max_items,
                unique_by_id=unique_by_id,
            )

        page_fetch_limit = _tail_page_fetch_limit(max_items, per_page)
        selector = _NewestTailSelector(
            max_items,
            unique_by_id=unique_by_id,
        )

        pages_processed = 0
        for page in range(last_page, 0, -1):
            if pages_processed >= page_fetch_limit:
                break
            if page == 1:
                page_items = first_page
            else:
                data = self._api_json(
                    ref,
                    endpoint,
                    extra_args=[
                        "--method",
                        "GET",
                        "-f",
                        f"per_page={per_page}",
                        "-f",
                        f"page={page}",
                    ],
                )
                page_items = self._validated_page_items(data, endpoint, page)

            selector.add_ascending_page(page_items)
            pages_processed += 1
            if selector.limit_reached:
                break

        return selector.items_ascending()

    def _validated_page_items(
        self,
        data: Any,
        endpoint: str,
        page: int,
    ) -> list[dict[str, Any]]:
        if not isinstance(data, list):
            raise GitHubError(f"GitHub returned non-list response for {endpoint} page {page}")
        page_items: list[dict[str, Any]] = []
        for item in data:
            if not isinstance(item, dict):
                raise GitHubError(f"GitHub returned a non-object item for {endpoint} page {page}")
            page_items.append(item)
        return page_items

    def _api_paginated_object_list(
        self,
        ref: PullRequestRef,
        endpoint: str,
        list_key: str,
        *,
        query_fields: list[str] | None = None,
        max_items: int | None = None,
        unique_by_id: bool = False,
    ) -> list[dict[str, Any]]:
        per_page = 100
        page = 1
        all_items: list[dict[str, Any]] = []
        seen_ids: set[str] = set()
        while True:
            fields = [
                f"per_page={per_page}",
                f"page={page}",
                *(query_fields or []),
            ]
            data = self._api_json(
                ref,
                endpoint,
                extra_args=[
                    "--method",
                    "GET",
                    *[arg for field in fields for arg in ("-f", field)],
                ],
            )
            if not isinstance(data, dict):
                raise GitHubError(f"GitHub returned non-object response for {endpoint} page {page}")
            page_items = data.get(list_key)
            if not isinstance(page_items, list):
                raise GitHubError(
                    f"GitHub returned non-list {list_key} for {endpoint} page {page}"
                )
            for item in self._validated_object_items(page_items, f"{endpoint} page {page}"):
                unique_key = _github_payload_id_key(item) if unique_by_id else None
                if unique_key is not None:
                    if unique_key in seen_ids:
                        continue
                    seen_ids.add(unique_key)
                all_items.append(item)
                if max_items is not None and len(all_items) >= max_items:
                    return all_items
            if len(page_items) < per_page:
                return all_items
            page += 1

    def _validated_object_items(
        self,
        items: list[Any],
        endpoint: str,
    ) -> list[dict[str, Any]]:
        validated: list[dict[str, Any]] = []
        for item in items:
            if not isinstance(item, dict):
                raise GitHubError(f"GitHub returned a non-object item for {endpoint}")
            validated.append(item)
        return validated

    def _api_json(
        self,
        ref: PullRequestRef,
        endpoint: str,
        extra_args: list[str] | None = None,
    ) -> Any:
        output = self._run_gh(["api", "--hostname", ref.hostname, endpoint, *(extra_args or [])])
        try:
            return json.loads(output)
        except JSONDecodeError as err:
            raise GitHubError(f"GitHub returned invalid JSON for {endpoint}: {err}") from err

    def _api_json_with_headers(
        self,
        ref: PullRequestRef,
        endpoint: str,
        extra_args: list[str] | None = None,
    ) -> tuple[Any, dict[str, str]]:
        output = self._run_gh(
            ["api", "--hostname", ref.hostname, "--include", endpoint, *(extra_args or [])]
        )
        headers, body = _split_gh_include_output(output)
        try:
            return json.loads(body), headers
        except JSONDecodeError as err:
            raise GitHubError(f"GitHub returned invalid JSON for {endpoint}: {err}") from err

    def _run_gh(self, args: list[str]) -> str:
        try:
            completed = subprocess.run(
                ["gh", *args],
                check=False,
                capture_output=True,
                text=True,
            )
        except FileNotFoundError as err:
            raise GitHubError("GitHub CLI `gh` is not installed or not on PATH") from err

        if completed.returncode != 0:
            command = " ".join(shlex.quote(part) for part in ["gh", *args])
            detail = completed.stderr.strip() or completed.stdout.strip() or "no error output"
            raise GitHubError(f"{command} failed with exit {completed.returncode}: {detail}")
        return completed.stdout
def _github_payload_id_key(item: dict[str, Any]) -> str | None:
    item_id = item.get("id")
    if isinstance(item_id, bool):
        return None
    if isinstance(item_id, int):
        return str(item_id)
    return item_id if isinstance(item_id, str) else None


def _newest_items(
    items: list[dict[str, Any]],
    max_items: int,
    *,
    unique_by_id: bool,
) -> list[dict[str, Any]]:
    selected, _ = _newest_selection(
        items,
        max_items,
        unique_by_id=unique_by_id,
    )
    return selected


def _newest_selection(
    items: list[dict[str, Any]],
    max_items: int,
    *,
    unique_by_id: bool,
) -> tuple[list[dict[str, Any]], bool]:
    selector = _NewestTailSelector(
        max_items,
        unique_by_id=unique_by_id,
    )
    selector.add_ascending_page(items)
    return selector.items_ascending(), selector.item_limit_reached


def _tail_page_fetch_limit(max_items: int, per_page: int) -> int:
    return ((max_items + per_page - 1) // per_page) + _TAIL_FETCH_EXTRA_PAGES


class _NewestTailSelector:
    def __init__(
        self,
        max_items: int,
        *,
        unique_by_id: bool,
    ):
        self._max_items = max_items
        self._unique_by_id = unique_by_id
        self._items_newest_first: list[dict[str, Any]] = []
        self._seen_ids: set[str] = set()
        self._selected_count = 0
        self.item_limit_reached = max_items <= 0

    @property
    def limit_reached(self) -> bool:
        return self.item_limit_reached

    def add_ascending_page(self, items: list[dict[str, Any]]) -> None:
        for item in reversed(items):
            if self.limit_reached:
                return
            self._add_newer_item(item)

    def items_ascending(self) -> list[dict[str, Any]]:
        return list(reversed(self._items_newest_first))

    def _add_newer_item(self, item: dict[str, Any]) -> None:
        if self._unique_by_id:
            unique_key = _github_payload_id_key(item)
            if unique_key is not None:
                if unique_key in self._seen_ids:
                    return
                self._seen_ids.add(unique_key)

        self._items_newest_first.append(item)
        self._selected_count += 1
        if self._selected_count >= self._max_items:
            self.item_limit_reached = True


def _split_gh_include_output(output: str) -> tuple[dict[str, str], str]:
    normalized = output.replace("\r\n", "\n")
    if not normalized.startswith("HTTP/"):
        return {}, output

    parts = normalized.split("\n\n")
    body = parts[-1]
    header_blocks = parts[:-1]
    headers: dict[str, str] = {}
    for block in header_blocks:
        if not block.startswith("HTTP/"):
            continue
        headers = {}
        for line in block.splitlines()[1:]:
            if ":" not in line:
                continue
            key, value = line.split(":", 1)
            headers[key.lower()] = value.strip()
    return headers, body


def _last_page_from_link_header(link_header: str) -> int | None:
    match = re.search(r'<[^>]*[?&]page=(\d+)[^>]*>;\s*rel="last"', link_header)
    if not match:
        return None
    return int(match.group(1))


def _repo_ref(repo: str) -> PullRequestRef:
    parts = repo.split("/")
    if len(parts) != 2 or not all(part.strip() for part in parts):
        raise GitHubError("expected repo in OWNER/REPO form")
    owner, name = (part.strip() for part in parts)
    return PullRequestRef(hostname="github.com", owner=owner, name=name, number=1)

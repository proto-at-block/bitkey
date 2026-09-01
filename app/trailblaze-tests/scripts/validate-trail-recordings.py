#!/usr/bin/env python3
"""Validate committed Trailblaze recordings before they are replayed."""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

ALLOWED_TOOLS = {
    "assertVisibleBySelector",
    "launchApp",
    "tapOnElementBySelector",
}

TOOLS_KEY = re.compile(r"^(?P<indent>\s*)(?P<list_item>-\s*)?tools:\s*(?:#.*)?$")
YAML_KEY = r"(?P<name>[A-Za-z_][A-Za-z0-9_]*|\"[A-Za-z_][A-Za-z0-9_]*\"|'[A-Za-z_][A-Za-z0-9_]*')"
LIST_ITEM = re.compile(rf"^(?P<indent>\s*)-\s*{YAML_KEY}\s*:")
FLOW_LIST_ITEM = re.compile(rf"^(?P<indent>\s*)-\s*\{{\s*{YAML_KEY}\s*:")
LIST_ITEM_START = re.compile(r"^(?P<indent>\s*)-(?:\s*(?:#.*)?)?$")
MAPPING_KEY = re.compile(rf"^(?P<indent>\s*){YAML_KEY}\s*:")


def key_name(raw_name: str) -> str:
    if raw_name.startswith(("'", '"')):
        return raw_name[1:-1]
    return raw_name


def validate_tool(
    errors: list[str],
    path: Path,
    line_number: int,
    raw_name: str,
) -> None:
    tool_name = key_name(raw_name)
    if tool_name not in ALLOWED_TOOLS:
        errors.append(
            f"{path}:{line_number}: tool '{tool_name}' is not allowed in committed "
            f"recordings. Allowed tools: {', '.join(sorted(ALLOWED_TOOLS))}"
        )


@dataclass
class ToolsContext:
    indent: int
    is_list_item: bool
    item_indent: int | None = None
    split_item_indent: int | None = None
    split_tool_indent: int | None = None

    def contains(self, indent: int, raw_line: str) -> bool:
        if self.is_list_item:
            return indent > self.indent

        if indent > self.indent:
            return True

        if indent < self.indent:
            return False

        return self.item_indent in (None, indent) and (
            LIST_ITEM.match(raw_line) is not None
            or FLOW_LIST_ITEM.match(raw_line) is not None
            or LIST_ITEM_START.match(raw_line) is not None
        )

    def maybe_start_split_item(self, item_indent: int) -> bool:
        if self.item_indent is None:
            self.item_indent = item_indent

        if item_indent != self.item_indent:
            return False

        self.split_item_indent = item_indent
        return True

    def maybe_split_tool(self, key_indent: int) -> bool:
        if self.split_item_indent is None or key_indent <= self.split_item_indent:
            return False

        if self.split_tool_indent is None:
            self.split_tool_indent = key_indent

        if key_indent != self.split_tool_indent:
            return False

        self.split_item_indent = None
        return True


def validate_trail(path: Path) -> list[str]:
    errors: list[str] = []
    tool_contexts: list[ToolsContext] = []

    for line_number, raw_line in enumerate(path.read_text().splitlines(), start=1):
        stripped = raw_line.strip()
        if not stripped or stripped.startswith("#"):
            continue

        indent = len(raw_line) - len(raw_line.lstrip(" "))
        tool_contexts = [
            context for context in tool_contexts if context.contains(indent, raw_line)
        ]

        tools_match = TOOLS_KEY.match(raw_line)
        if tools_match:
            tool_contexts.append(
                ToolsContext(
                    len(tools_match.group("indent")),
                    tools_match.group("list_item") is not None,
                )
            )
            continue

        item_match = LIST_ITEM.match(raw_line) or FLOW_LIST_ITEM.match(raw_line)
        if not item_match:
            split_item_match = LIST_ITEM_START.match(raw_line)
            if split_item_match:
                item_indent = len(split_item_match.group("indent"))
                for context in reversed(tool_contexts):
                    if context.maybe_start_split_item(item_indent):
                        break
            else:
                key_match = MAPPING_KEY.match(raw_line)
                if key_match:
                    key_indent = len(key_match.group("indent"))
                    for context in reversed(tool_contexts):
                        if context.maybe_split_tool(key_indent):
                            validate_tool(errors, path, line_number, key_match.group("name"))
                            break
            continue

        item_indent = len(item_match.group("indent"))
        for context in reversed(tool_contexts):
            if context.item_indent is None:
                context.item_indent = item_indent

            if item_indent != context.item_indent:
                continue

            validate_tool(errors, path, line_number, item_match.group("name"))
            break

    return errors


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("trails")
    trail_files = sorted(root.rglob("*.trail.yaml"))
    if not trail_files:
        print(f"error: no *.trail.yaml files found under {root}", file=sys.stderr)
        return 1

    errors: list[str] = []
    for trail_file in trail_files:
        errors.extend(validate_trail(trail_file))

    if errors:
        print("Trailblaze recording validation failed:", file=sys.stderr)
        for error in errors:
            print(f"  {error}", file=sys.stderr)
        return 1

    print(f"Validated {len(trail_files)} Trailblaze recording(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

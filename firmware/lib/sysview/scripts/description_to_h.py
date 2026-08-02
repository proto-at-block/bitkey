#!/usr/bin/env python3
# Copyright 2026 Square, Inc.
#
# Generate a C header from a SEGGER SystemView description file.
# No timestamps are emitted to keep output deterministic across runs.

from __future__ import annotations

import argparse
import os
import re
import sys
from collections import OrderedDict
from typing import Dict, Iterable, List, Tuple

API_LINE_RE = re.compile(r"^(\d+)\s+(\S+)")
MASK_RE = re.compile(r"^(0x[0-9A-Fa-f]+|\d+)$")


def _sanitize_identifier(value: str) -> str:
    """Make a string safe for use as a C identifier (preserves case)."""
    cleaned = re.sub(r"[^0-9A-Za-z_]", "_", value)
    cleaned = re.sub(r"_+", "_", cleaned)
    cleaned = cleaned.lstrip("_")
    if not cleaned:
        return "UNKNOWN"
    if cleaned[0].isdigit():
        cleaned = "_" + cleaned
    return cleaned


def _api_prefix_from_desc(desc_base: str) -> str:
    """Create the SYSVIEW_<DESC> prefix for API macros (upper-case)."""
    base = desc_base
    if base.upper().startswith("SYSVIEW_"):
        base = base[len("SYSVIEW_") :]
    cleaned = _sanitize_identifier(base).upper()
    if not cleaned:
        cleaned = "UNKNOWN"
    return f"SYSVIEW_{cleaned}"


def _module_symbol_stem_from_desc(desc_base: str) -> str:
    """Create a sanitized module stem for helper symbol names."""
    base = desc_base
    if base.upper().startswith("SYSVIEW_"):
        base = base[len("SYSVIEW_") :]
    cleaned = _sanitize_identifier(base)
    if not cleaned:
        cleaned = "Unknown"
    elif cleaned[0].isalpha():
        cleaned = cleaned[0].upper() + cleaned[1:]
    return cleaned


def _parse_namedtype_pairs(text: str) -> Iterable[Tuple[str, str]]:
    for token in text.split():
        if "=" not in token:
            continue
        value, label = token.split("=", 1)
        value = value.strip()
        label = label.strip()
        if value and label:
            yield value, label


def _parse_enum_pairs(text: str) -> Iterable[Tuple[str, str]]:
    if "," in text:
        parts = text.split(",")
        for part in parts:
            part = part.strip()
            if not part or "=" not in part:
                continue
            value, label = part.split("=", 1)
            value = value.strip()
            label = label.strip()
            if value and label:
                yield value, label
    else:
        yield from _parse_namedtype_pairs(text)


def parse_description(
    lines: Iterable[str],
) -> Tuple[List[Tuple[int, str]], "OrderedDict[str, List[Tuple[str, str]]]", "OrderedDict[str, List[Tuple[str, str]]]"]:
    api_functions: List[Tuple[int, str]] = []
    named_types: "OrderedDict[str, List[Tuple[str, str]]]" = OrderedDict()
    enum_types: "OrderedDict[str, List[Tuple[str, str]]]" = OrderedDict()

    for raw_line in lines:
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue

        if line.startswith("Option"):
            continue

        if line.startswith("NamedType "):
            parts = line.split(None, 2)
            if len(parts) < 3:
                continue
            type_name = parts[1].strip()
            pairs_text = parts[2].strip()
            if type_name not in named_types:
                named_types[type_name] = []
            for value, label in _parse_namedtype_pairs(pairs_text):
                named_types[type_name].append((value, label))
            continue

        api_match = API_LINE_RE.match(line)
        if api_match:
            api_id = int(api_match.group(1))
            api_name = api_match.group(2)
            api_functions.append((api_id, api_name))
            continue

        parts = line.split(None, 2)
        if len(parts) >= 3 and MASK_RE.match(parts[1]) and "=" in parts[2]:
            enum_name = parts[0].strip()
            pairs_text = parts[2].strip()
            if enum_name not in enum_types:
                enum_types[enum_name] = []
            for value, label in _parse_enum_pairs(pairs_text):
                enum_types[enum_name].append((value, label))
            continue

    return api_functions, named_types, enum_types


def _write_header(
    header_path: str,
    api_prefix: str,
    module_symbol_stem: str,
    use_runtime_module_offset: bool,
    api_functions: List[Tuple[int, str]],
    named_types: "OrderedDict[str, List[Tuple[str, str]]]",
    enum_types: "OrderedDict[str, List[Tuple[str, str]]]",
) -> None:
    lines: List[str] = []
    header_guard = f"{_sanitize_identifier(api_prefix)}_GENERATED_H"
    lines.append("#pragma once")
    lines.append(f"#ifndef {header_guard}")
    lines.append(f"#define {header_guard}")
    lines.append("")
    lines.append("/* Generated from SystemView description file. */")
    lines.append("")

    if use_runtime_module_offset:
        offset_func_name = f"SYSVIEW_Get{module_symbol_stem}EventOffset"
        event_id_macro_name = f"{api_prefix}_EVENT_ID"
        module_display_name = module_symbol_stem.replace("_", " ").lower()
        lines.append("/*")
        lines.append(f" * SystemView {module_display_name} events are assigned a runtime module offset.")
        lines.append(" * Wrap API IDs so callsites do not need to add the offset manually.")
        lines.append(" */")
        lines.append("#ifdef __cplusplus")
        lines.append('extern "C" {')
        lines.append("#endif")
        lines.append(f"unsigned int {offset_func_name}(void);")
        lines.append("#ifdef __cplusplus")
        lines.append("}")
        lines.append("#endif")
        lines.append("")
        lines.append(f"#define {event_id_macro_name}(EventId) ({offset_func_name}() + (EventId))")
        lines.append("")

    if api_functions:
        lines.append("/* API Function IDs */")
        for api_id, api_name in api_functions:
            macro_name = f"{api_prefix}_{_sanitize_identifier(api_name)}"
            if use_runtime_module_offset:
                lines.append(f"#define {macro_name} {api_prefix}_EVENT_ID({api_id})")
            else:
                lines.append(f"#define {macro_name} {api_id}")
        ids = [api_id for api_id, _ in api_functions]
        unique_ids = sorted(set(ids))
        lines.append(f"#define {api_prefix}_API_ID_MIN {min(unique_ids)}")
        lines.append(f"#define {api_prefix}_API_ID_MAX {max(unique_ids)}")
        lines.append(f"#define {api_prefix}_API_ID_COUNT {len(unique_ids)}")
        lines.append("")

    if named_types:
        lines.append("/* NamedType Enums */")
        for type_name, entries in named_types.items():
            enum_name = f"{api_prefix}_ENUM_{_sanitize_identifier(type_name)}"
            lines.append("typedef enum {")
            seen = set()
            for value, label in entries:
                sanitized_label = _sanitize_identifier(label)
                if sanitized_label.startswith("SYSVIEW_"):
                    enum_label = sanitized_label
                else:
                    enum_label = f"SYSVIEW_{sanitized_label}"
                if enum_label in seen:
                    continue
                seen.add(enum_label)
                lines.append(f"    {enum_label} = {value},")
            lines.append(f"}} {enum_name};")
            lines.append("")

    if enum_types:
        lines.append("/* Enum Types */")
        for type_name, entries in enum_types.items():
            enum_name = f"{api_prefix}_ENUM_{_sanitize_identifier(type_name)}"
            lines.append("typedef enum {")
            seen = set()
            for value, label in entries:
                enum_label = f"e_{_sanitize_identifier(label)}"
                if enum_label in seen:
                    continue
                seen.add(enum_label)
                lines.append(f"    {enum_label} = {value},")
            lines.append(f"}} {enum_name};")
            lines.append("")

    lines.append(f"#endif /* {header_guard} */")

    with open(header_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate C header from SystemView description file.")
    parser.add_argument("-desc", required=True, help="Path to SystemView description file.")
    parser.add_argument("-header", required=False, help="Path to output header file.")
    parser.add_argument(
        "--runtime-module-offset",
        action="store_true",
        help="Wrap API IDs with a runtime module offset accessor derived from the description filename.",
    )
    args = parser.parse_args()

    try:
        with open(args.desc, "r", encoding="utf-8") as f:
            api_functions, named_types, enum_types = parse_description(f.readlines())
    except OSError as exc:
        print(f"error: unable to read description file: {exc}", file=sys.stderr)
        return 1

    desc_base = os.path.splitext(os.path.basename(args.desc))[0]
    api_prefix = _api_prefix_from_desc(desc_base)
    module_symbol_stem = _module_symbol_stem_from_desc(desc_base)
    use_runtime_module_offset = args.runtime_module_offset
    safe_base = desc_base.replace("-", "_")

    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir, os.pardir))
    source_tree_dir = os.path.join(repo_root, "sysview", "include")
    source_tree_path = os.path.join(source_tree_dir, f"{safe_base}.h")

    # The build always writes to the source-tree path so the generated header
    # is checked into git alongside the .txt it was generated from. If the
    # caller (meson custom_target) also passes -header, we additionally write
    # the same content there so the build dir has its own copy. The two paths
    # stay byte-identical because both come from the same _write_header call.
    targets = [source_tree_path]
    if args.header and os.path.abspath(args.header) != os.path.abspath(source_tree_path):
        targets.append(args.header)

    try:
        os.makedirs(source_tree_dir, exist_ok=True)
        for path in targets:
            target_dir = os.path.dirname(path)
            if target_dir:
                os.makedirs(target_dir, exist_ok=True)
            _write_header(
                path,
                api_prefix,
                module_symbol_stem,
                use_runtime_module_offset,
                api_functions,
                named_types,
                enum_types,
            )
    except OSError as exc:
        print(f"error: unable to write header file: {exc}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

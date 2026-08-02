#!/usr/bin/env python3
# Copyright 2026 Square, Inc.

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import select
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any


DEFAULT_PLATFORM = "w3-uxc"
DEFAULT_INTERFACE = "SWD"
DEFAULT_SPEED_KHZ = 4000
DEFAULT_RTT_CHANNEL = 1
DEFAULT_RTT_TELNET_PORT = 19021
DEFAULT_USB_TIMEOUT_S = 10.0
TELNET_CONFIG_GRACE_S = 0.05
STOP_FLUSH_DELAY_S = 0.20
RTT_BUFFER_DISCOVERY_TIMEOUT_S = 5.0
RTT_BUFFER_SETTLE_DELAY_S = 0.50
RTT_COMMAND_TIMEOUT_S = 3.0
RTT_COMMAND_RETRY_DELAY_S = 0.05
SVDAT_FILE_MAGIC = b"\xBF\x88\xEE\xAB"

SYSVIEW_CMD_START = 1
SYSVIEW_CMD_STOP = 2

PLATFORM_DEVICES = {
    "w1": "EFR32MG24BXXXF1536",
    "w3-core": "EFR32MG24BXXXF1536",
    "w3-uxc": "STM32U585ZI",
}

# Hardcoded RTT control-block addresses per platform. The wallet sysview
# firmware pins the SEGGER RTT control block to a fixed absolute address via
# its linker script so the host tool does not need to search memory or read
# the ELF. Keep this in sync with firmware/config/partitions/<platform>/*.ld.
PLATFORM_RTT_CB_ADDR = {
    # w3-uxc: .sram4.rtt_cb pinned to start of SRAM4 (see stm32u5xx.jinja.ld)
    "w3-uxc": 0x28000000,
}

STANDARD_EVENT_NAMES = {
    0: "NOP",
    1: "OVERFLOW",
    2: "ISR_ENTER",
    3: "ISR_EXIT",
    4: "TASK_START_EXEC",
    5: "TASK_STOP_EXEC",
    6: "TASK_START_READY",
    7: "TASK_STOP_READY",
    8: "TASK_CREATE",
    9: "TASK_INFO",
    10: "TRACE_START",
    11: "TRACE_STOP",
    12: "SYSTIME_CYCLES",
    13: "SYSTIME_US",
    14: "SYSDESC",
    15: "MARK_START",
    16: "MARK_STOP",
    17: "IDLE",
    18: "ISR_TO_SCHEDULER",
    19: "TIMER_ENTER",
    20: "TIMER_EXIT",
    21: "STACK_INFO",
    22: "MODULEDESC",
    24: "INIT",
    25: "NAME_RESOURCE",
    26: "PRINT_FORMATTED",
    27: "NUMMODULES",
    28: "END_CALL",
    29: "TASK_TERMINATE",
    31: "EX",
}

ZERO_PAYLOAD_STANDARD_EVENTS = {
    0,
    3,
    5,
    10,
    11,
    17,
    18,
    20,
}

DESCRIPTION_EVENT_RE = re.compile(
    r"^(\d+)\s+(\S+)(?:\s+(.*?))?(?:\s*\|\s*Returns\s+(%[A-Za-z_][A-Za-z0-9_]*))?\s*$"
)
# SystemView descriptions list args inline as `name = %X name = %X ...`,
# space-separated (not comma-separated). The previous regex was anchored to
# end-of-string and only captured the final arg. This one finds them all.
DESCRIPTION_ARG_RE = re.compile(
    r"([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?:0x)?(%[A-Za-z_][A-Za-z0-9_]*)"
)


def die(message: str) -> int:
    print(f"error: {message}", file=sys.stderr)
    return 1


DEFAULT_OUTPUT_DIR = Path.home() / "Downloads"


def ensure_output_path(raw: str | None, suffix: str, stem: str) -> Path:
    if not raw:
        return DEFAULT_OUTPUT_DIR / f"{stem}{suffix}"

    path = Path(raw).expanduser()
    if raw.endswith(os.sep) or path.is_dir():
        return path / f"{stem}{suffix}"
    if path.suffix.lower() == suffix.lower():
        return path
    return path.with_name(path.name + suffix)


def find_rttlogger(explicit: str | None) -> str:
    candidates = [
        explicit,
        shutil.which("JLinkRTTLogger"),
        "/Applications/SEGGER/JLink/JLinkRTTLogger",
        "/Applications/SEGGER/JLink_V874a/JLinkRTTLogger",
    ]
    for candidate in candidates:
        if candidate and os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate
    raise FileNotFoundError(
        "Could not find JLinkRTTLogger. Pass --rttlogger with the full path."
    )


def build_logger_command(args: argparse.Namespace, output_path: Path, rttlogger: str) -> list[str]:
    device = args.device or PLATFORM_DEVICES[args.platform]
    cmd = [
        rttlogger,
        "-Device",
        device,
        "-If",
        args.interface,
        "-Speed",
        str(args.speed),
    ]
    if args.usb:
        cmd.extend(["-USB", args.usb])
    elif args.jlink_ip:
        cmd.extend(["-IP", args.jlink_ip])

    if args.rttcbaddr:
        cmd.extend(["-RTTAddress", args.rttcbaddr])
    elif args.rttcbrange:
        cmd.extend(["-RTTSearchRanges", args.rttcbrange])

    cmd.extend(["-RTTChannel", str(args.rtt_channel), str(output_path)])
    return cmd


def connect_rtt_telnet(port: int, timeout_s: float) -> socket.socket:
    deadline = time.monotonic() + timeout_s
    while time.monotonic() < deadline:
        try:
            return socket.create_connection(("127.0.0.1", port), timeout=0.25)
        except OSError:
            time.sleep(0.10)
    raise TimeoutError(f"RTT telnet port {port} did not become available.")


def build_telnet_config(args: argparse.Namespace) -> bytes:
    tokens = ["RTTCh", str(args.rtt_channel)]
    if args.rttcbaddr:
        tokens.extend(["SetRTTAddr", args.rttcbaddr])
    elif args.rttcbrange:
        tokens.extend(["SetRTTSearchRanges", args.rttcbrange])
    body = ";".join(tokens)
    if not body.endswith(";"):
        body += ";"
    return f"$$SEGGER_TELNET_ConfigStr={body}$$".encode("ascii")


def send_sysview_command(args: argparse.Namespace, command_id: int) -> None:
    # SystemView starts and stops tracing when the host writes command bytes
    # on the RTT down-channel for the selected SystemView channel.
    config = build_telnet_config(args)
    with connect_rtt_telnet(args.rtt_telnet_port, DEFAULT_USB_TIMEOUT_S) as sock:
        sock.settimeout(1.0)
        sock.sendall(config)
        time.sleep(TELNET_CONFIG_GRACE_S)
        sock.sendall(bytes([command_id]))


@dataclass
class DescriptionArg:
    name: str
    fmt: str


@dataclass
class DescriptionEvent:
    event_id: int
    name: str
    args: list[DescriptionArg]
    return_fmt: str | None
    source_file: str


class DescriptionDB:
    def __init__(self) -> None:
        self.events: dict[int, DescriptionEvent] = {}
        self.module_events: dict[str, dict[int, DescriptionEvent]] = {}
        self.named_types: dict[str, dict[int, str]] = {}
        self._registered_module_offsets: set[tuple[str, int]] = set()

    def add_path(self, path: Path) -> None:
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except OSError as exc:
            raise RuntimeError(f"unable to read description file {path}: {exc}") from exc

        path_events: dict[int, DescriptionEvent] = {}
        for raw_line in lines:
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("Option"):
                continue
            if line.startswith("NamedType "):
                parts = line.split(None, 2)
                if len(parts) >= 3:
                    type_name = parts[1].strip()
                    entries = self.named_types.setdefault(type_name, {})
                    for token in parts[2].split():
                        if "=" not in token:
                            continue
                        raw_value, label = token.split("=", 1)
                        try:
                            parsed_value = int(raw_value, 0)
                        except ValueError:
                            continue
                        entries[parsed_value] = label
                continue
            match = DESCRIPTION_EVENT_RE.match(line)
            if not match:
                continue

            event_id = int(match.group(1))
            name = match.group(2)
            arg_text = (match.group(3) or "").strip()
            return_fmt = match.group(4)
            args: list[DescriptionArg] = []
            if arg_text:
                # SystemView descriptions are space-separated `name = %X`
                # repeated. findall picks up every arg in declaration order.
                for arg_name, arg_fmt in DESCRIPTION_ARG_RE.findall(arg_text):
                    args.append(DescriptionArg(name=arg_name, fmt=arg_fmt[1:]))

            event = DescriptionEvent(
                event_id=event_id,
                name=name,
                args=args,
                return_fmt=return_fmt[1:] if return_fmt else None,
                source_file=path.name,
            )
            self.events.setdefault(event_id, event)
            path_events.setdefault(event_id, event)

        stem = path.stem
        module_name = stem[len("SYSVIEW_"):] if stem.startswith("SYSVIEW_") else ""
        if module_name and path_events:
            self.module_events.setdefault(module_name, {}).update(path_events)

    def register_runtime_module(self, description: str, event_offset: int) -> None:
        if "=" not in description:
            return
        prefix, module_name = description.split("=", 1)
        if prefix not in {"M", "T"} or not module_name:
            return
        key = (module_name, event_offset)
        if key in self._registered_module_offsets:
            return

        specs = self.module_events.get(module_name)
        if not specs:
            return

        for local_event_id, event in specs.items():
            runtime_event_id = event_offset + local_event_id
            self.events[runtime_event_id] = DescriptionEvent(
                event_id=runtime_event_id,
                name=event.name,
                args=event.args,
                return_fmt=event.return_fmt,
                source_file=event.source_file,
            )

        self._registered_module_offsets.add(key)

    @classmethod
    def from_path(cls, path: Path) -> "DescriptionDB":
        db = cls()
        if path.is_dir():
            for child in sorted(path.glob("*.txt")):
                db.add_path(child)
        else:
            db.add_path(path)
        return db


class TraceParseError(RuntimeError):
    pass


@dataclass
class ParsedEvent:
    event_id: int
    event_name: str
    event_type: str
    raw_args: dict[str, Any]
    display_args: dict[str, Any]
    detail: str


class TraceContext:
    def __init__(self) -> None:
        self.timestamp_cycles = 0
        self.sys_freq: int | None = None
        self.cpu_freq: int | None = None
        self.ram_base = 0x20000000
        self.id_shift = 2
        self.absolute_us_offset: float | None = None
        self.task_names: dict[int, str] = {}
        self.resource_names: dict[int, str] = {}
        self.marker_names: dict[int, str] = {}
        self.irq_names: dict[int, str] = {}
        self.app_name: str | None = None
        self.device_name: str | None = None
        self.os_name: str | None = None

    def expand_id(self, shrunk: int) -> int:
        return self.ram_base + (shrunk << self.id_shift)

    def format_task(self, shrunk: int) -> str:
        address = self.expand_id(shrunk)
        name = self.task_names.get(shrunk)
        if name:
            return f"{name}@0x{address:08X}"
        return f"0x{address:08X}"

    def format_resource(self, shrunk: int) -> str:
        address = self.expand_id(shrunk)
        name = self.resource_names.get(shrunk)
        if name:
            return f"{name}@0x{address:08X}"
        return f"0x{address:08X}"

    def format_irq(self, irq_id: int) -> str:
        return self.irq_names.get(irq_id, str(irq_id))

    def relative_us(self) -> float | None:
        if not self.sys_freq:
            return None
        return (self.timestamp_cycles * 1_000_000.0) / self.sys_freq

    def absolute_us(self) -> float | None:
        relative_us = self.relative_us()
        if relative_us is None or self.absolute_us_offset is None:
            return None
        return relative_us + self.absolute_us_offset


def decode_varint(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while True:
        if offset >= len(data):
            raise TraceParseError("unexpected end of file while decoding varint")
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, offset
        shift += 7
        if shift > 35:
            raise TraceParseError("varint is too large")


def decode_counted_string(data: bytes, offset: int) -> tuple[str, int]:
    if offset >= len(data):
        raise TraceParseError("unexpected end of file while decoding counted string")
    length = data[offset]
    offset += 1
    end = offset + length
    if end > len(data):
        raise TraceParseError("unexpected end of file while reading string contents")
    return data[offset:end].decode("utf-8", errors="replace"), end


def decode_remaining_varints(payload: bytes) -> list[int]:
    values = []
    offset = 0
    while offset < len(payload):
        value, offset = decode_varint(payload, offset)
        values.append(value)
    return values


def try_decode_remaining_varints(payload: bytes) -> list[int] | None:
    try:
        return decode_remaining_varints(payload)
    except TraceParseError:
        return None


def standard_payload_end(data: bytes, event_id: int, payload_start: int) -> int:
    payload_end = payload_start
    if event_id in ZERO_PAYLOAD_STANDARD_EVENTS:
        return payload_end
    if event_id in {1, 2, 12, 15, 16, 19}:
        _, payload_end = decode_varint(data, payload_start)
        return payload_end
    if event_id in {4, 6, 8}:
        _, payload_end = decode_varint(data, payload_start)
        return payload_end
    if event_id == 7:
        _, payload_end = decode_varint(data, payload_start)
        _, payload_end = decode_varint(data, payload_end)
        return payload_end
    if event_id == 9:
        _, payload_end = decode_varint(data, payload_start)
        _, payload_end = decode_varint(data, payload_end)
        _, payload_end = decode_counted_string(data, payload_end)
        return payload_end
    if event_id == 13:
        _, payload_end = decode_varint(data, payload_start)
        _, payload_end = decode_varint(data, payload_end)
        return payload_end
    if event_id == 14:
        _, payload_end = decode_counted_string(data, payload_start)
        return payload_end
    if event_id == 21:
        _, payload_end = decode_varint(data, payload_start)
        _, payload_end = decode_varint(data, payload_end)
        _, payload_end = decode_varint(data, payload_end)
        _, payload_end = decode_varint(data, payload_end)
        return payload_end
    if event_id == 22:
        _, payload_end = decode_varint(data, payload_start)
        _, payload_end = decode_varint(data, payload_end)
        _, payload_end = decode_counted_string(data, payload_end)
        return payload_end
    raise TraceParseError(f"unsupported standard event ID {event_id}")


def parse_headerless_trace(data: bytes) -> bytes:
    payload = data
    if data.startswith(b";\n;") or data.startswith(b";\r\n;"):
        for marker in (b"\n;\n\n", b"\r\n;\r\n\r\n"):
            position = data.find(marker)
            if position != -1:
                payload = data[position + len(marker) :]
                break
    if payload.startswith(SVDAT_FILE_MAGIC) and len(payload) >= 5:
        payload = payload[5:]
    return payload


def format_number(value: int, fmt: str, context: TraceContext) -> str:
    if fmt in {"t", "T"}:
        return context.format_task(value)
    if fmt == "I":
        return context.format_resource(value)
    if fmt == "d":
        if value & 0x80000000:
            value -= 0x1_0000_0000
        return str(value)
    if fmt in {"x", "X"}:
        width = 8 if value <= 0xFFFFFFFF else 16
        return f"0x{value:0{width}X}"
    if fmt in {"p", "s"}:
        return f"0x{value:08X}"
    return str(value)


def format_value(value: int, fmt: str, context: TraceContext, descriptions: DescriptionDB) -> str:
    named_type = descriptions.named_types.get(fmt)
    if named_type:
        label = named_type.get(value)
        if label is None and value <= 0xFFFFFFFF:
            signed_value = value if value < 0x80000000 else value - 0x1_0000_0000
            label = named_type.get(signed_value)
        if label is not None:
            return label
    return format_number(value, fmt, context)


def render_display_args(
    raw_args: dict[str, Any], formats: dict[str, str], context: TraceContext, descriptions: DescriptionDB
) -> dict[str, Any]:
    display_args: dict[str, Any] = {}
    for key, value in raw_args.items():
        fmt = formats.get(key)
        if fmt and isinstance(value, int):
            display_args[key] = format_value(value, fmt, context, descriptions)
        else:
            display_args[key] = value
    return display_args


def _format_packed_i2c_bytes(chunk_len: int, words: list[int]) -> str:
    remaining = chunk_len
    rendered: list[str] = []
    for word in words:
        take = min(remaining, 4)
        for idx in range(take):
            rendered.append(f"{(word >> (idx * 8)) & 0xFF:02X}")
        remaining -= take
        if remaining == 0:
            break
    return " ".join(rendered)


def _rewrite_i2c_data_display_args(raw_args: dict[str, Any], display_args: dict[str, Any]) -> dict[str, Any]:
    chunk_len = int(raw_args.get("bytes", 0))
    words = [int(raw_args.get("data0", 0)), int(raw_args.get("data1", 0))]
    rewritten: dict[str, Any] = {}
    for key, value in display_args.items():
        if key in {"data0", "data1"}:
            continue
        rewritten[key] = value
    rewritten["data"] = _format_packed_i2c_bytes(chunk_len, words)
    return rewritten


def render_detail(name: str, display_args: dict[str, Any]) -> str:
    if not display_args:
        return name
    pieces = [f"{key}={value}" for key, value in display_args.items()]
    return f"{name} " + ", ".join(pieces)


def parse_sysdesc(value: str, context: TraceContext) -> None:
    for piece in value.split(","):
        piece = piece.strip()
        if not piece or "=" not in piece:
            continue
        key, parsed_value = piece.split("=", 1)
        key = key.strip()
        parsed_value = parsed_value.strip()
        if key == "N":
            context.app_name = parsed_value
        elif key == "D":
            context.device_name = parsed_value
        elif key == "O":
            context.os_name = parsed_value
        elif key.startswith("I#"):
            try:
                context.irq_names[int(key[2:])] = parsed_value
            except ValueError:
                continue


def parse_standard_event(
    event_id: int, payload: bytes, context: TraceContext, descriptions: DescriptionDB
) -> ParsedEvent:
    offset = 0
    raw_args: dict[str, Any] = {}
    formats: dict[str, str] = {}
    name = STANDARD_EVENT_NAMES.get(event_id, f"EVENT_{event_id}")

    def read_u32(arg_name: str, fmt: str = "u") -> int:
        nonlocal offset
        value, offset = decode_varint(payload, offset)
        raw_args[arg_name] = value
        formats[arg_name] = fmt
        return value

    if event_id in ZERO_PAYLOAD_STANDARD_EVENTS:
        pass
    elif event_id == 1:
        read_u32("drop_count")
    elif event_id == 2:
        read_u32("irq_id")
    elif event_id in {4, 6, 8}:
        read_u32("task_id", "t")
    elif event_id == 19:
        read_u32("timer_id", "I")
    elif event_id == 7:
        read_u32("task_id", "t")
        read_u32("cause")
    elif event_id == 9:
        read_u32("task_id", "t")
        read_u32("priority")
        task_name, offset = decode_counted_string(payload, offset)
        raw_args["name"] = task_name
    elif event_id == 12:
        read_u32("systime_cycles")
    elif event_id == 13:
        low = read_u32("systime_us_low")
        high = read_u32("systime_us_high")
        raw_args["systime_us"] = low | (high << 32)
    elif event_id == 14:
        sysdesc, offset = decode_counted_string(payload, offset)
        raw_args["sysdesc"] = sysdesc
    elif event_id in {15, 16}:
        read_u32("marker_id")
    elif event_id == 21:
        # SEGGER STACK_INFO layout: TaskID, StackBase, StackSize, StackUsage.
        read_u32("task_id", "t")
        read_u32("stack_base", "p")
        read_u32("stack_size")
        read_u32("stack_usage")
    elif event_id == 22:
        read_u32("module_id")
        read_u32("event_offset")
        module_name, offset = decode_counted_string(payload, offset)
        raw_args["description"] = module_name
    else:
        raise TraceParseError(f"unsupported standard event ID {event_id}")

    if offset != len(payload):
        raise TraceParseError(f"unexpected payload size for standard event {event_id}")

    if event_id == 9:
        task_id = raw_args["task_id"]
        context.task_names[task_id] = raw_args["name"]
    elif event_id == 14:
        parse_sysdesc(raw_args["sysdesc"], context)
    elif event_id == 22:
        descriptions.register_runtime_module(raw_args["description"], raw_args["event_offset"])

    display_args = render_display_args(raw_args, formats, context, descriptions)

    if event_id == 2:
        display_args["irq_id"] = context.format_irq(raw_args["irq_id"])
    elif event_id == 13:
        display_args["systime_us"] = raw_args["systime_us"]
    elif event_id == 14:
        display_args = {"sysdesc": raw_args["sysdesc"]}
    elif event_id == 22:
        display_args = {
            "module_id": raw_args["module_id"],
            "event_offset": raw_args["event_offset"],
            "description": raw_args["description"],
        }

    detail = render_detail(name, display_args)
    return ParsedEvent(
        event_id=event_id,
        event_name=name,
        event_type="standard",
        raw_args=raw_args,
        display_args=display_args,
        detail=detail,
    )


def parse_length_prefixed_event(
    event_id: int, payload: bytes, context: TraceContext, descriptions: DescriptionDB
) -> ParsedEvent:
    raw_args: dict[str, Any] = {}
    formats: dict[str, str] = {}
    name = STANDARD_EVENT_NAMES.get(event_id, f"EVENT_{event_id}")

    if event_id == 24:
        offset = 0
        sys_freq, offset = decode_varint(payload, offset)
        cpu_freq, offset = decode_varint(payload, offset)
        ram_base, offset = decode_varint(payload, offset)
        id_shift, offset = decode_varint(payload, offset)
        if offset != len(payload):
            raise TraceParseError("unexpected payload size for INIT")
        context.sys_freq = sys_freq
        context.cpu_freq = cpu_freq
        context.ram_base = ram_base
        context.id_shift = id_shift
        raw_args = {
            "sys_freq": sys_freq,
            "cpu_freq": cpu_freq,
            "ram_base": ram_base,
            "id_shift": id_shift,
        }
        display_args = {
            "sys_freq": sys_freq,
            "cpu_freq": cpu_freq,
            "ram_base": f"0x{ram_base:08X}",
            "id_shift": id_shift,
        }
        return ParsedEvent(event_id, name, "system", raw_args, display_args, render_detail(name, display_args))

    if event_id == 25:
        offset = 0
        resource_id, offset = decode_varint(payload, offset)
        resource_name, offset = decode_counted_string(payload, offset)
        if offset != len(payload):
            raise TraceParseError("unexpected payload size for NAME_RESOURCE")
        context.resource_names[resource_id] = resource_name
        raw_args = {"resource_id": resource_id, "name": resource_name}
        display_args = {
            "resource_id": context.format_resource(resource_id),
            "name": resource_name,
        }
        return ParsedEvent(event_id, name, "system", raw_args, display_args, render_detail(name, display_args))

    if event_id == 26:
        offset = 0
        format_string, offset = decode_counted_string(payload, offset)
        options, offset = decode_varint(payload, offset)
        num_args, offset = decode_varint(payload, offset)
        args: list[int] = []
        for _ in range(num_args):
            value, offset = decode_varint(payload, offset)
            args.append(value)
        if offset != len(payload):
            raise TraceParseError("unexpected payload size for PRINT_FORMATTED")
        level = {0: "log", 1: "warning", 2: "error"}.get(options & 0x03, str(options))
        raw_args = {
            "format": format_string,
            "options": options,
            "level": level,
            "args": args,
        }
        display_args = {
            "level": level,
            "format": format_string,
            "args": args,
        }
        detail = f"PRINT_FORMATTED level={level}, format={format_string!r}, args={args}"
        return ParsedEvent(event_id, name, "system", raw_args, display_args, detail)

    if event_id == 27:
        offset = 0
        num_modules, offset = decode_varint(payload, offset)
        if offset != len(payload):
            raise TraceParseError("unexpected payload size for NUMMODULES")
        raw_args = {"num_modules": num_modules}
        return ParsedEvent(event_id, name, "system", raw_args, raw_args.copy(), render_detail(name, raw_args))

    if event_id == 28:
        offset = 0
        call_event_id, offset = decode_varint(payload, offset)
        raw_args["call_event_id"] = call_event_id
        desc_event = descriptions.events.get(call_event_id)
        standard_name = STANDARD_EVENT_NAMES.get(call_event_id)
        if offset < len(payload):
            return_value, offset = decode_varint(payload, offset)
            raw_args["return_value"] = return_value
        if offset != len(payload):
            raise TraceParseError("unexpected payload size for END_CALL")
        display_args = raw_args.copy()
        event_name = desc_event.name if desc_event else standard_name
        if event_name:
            display_args["event"] = event_name
        if "return_value" in raw_args and desc_event and desc_event.return_fmt:
            display_args["return_value"] = format_value(
                raw_args["return_value"], desc_event.return_fmt, context, descriptions
            )
        detail = "END_CALL"
        if event_name:
            detail = f"END_CALL {event_name}"
        if "return_value" in display_args:
            detail += f" return={display_args['return_value']}"
        return ParsedEvent(event_id, name, "system", raw_args, display_args, detail)

    if event_id == 29:
        offset = 0
        task_id, offset = decode_varint(payload, offset)
        if offset != len(payload):
            raise TraceParseError("unexpected payload size for TASK_TERMINATE")
        raw_args = {"task_id": task_id}
        display_args = {"task_id": context.format_task(task_id)}
        return ParsedEvent(event_id, name, "system", raw_args, display_args, render_detail(name, display_args))

    if event_id == 31:
        offset = 0
        subevent_id, offset = decode_varint(payload, offset)
        if subevent_id == 0:
            marker_id, offset = decode_varint(payload, offset)
            raw_args = {"subevent_id": subevent_id, "marker_id": marker_id}
            marker_name = context.marker_names.get(marker_id)
            display_args = {
                "marker_id": f"{marker_name}({marker_id})" if marker_name else marker_id
            }
            detail = render_detail("MARK", display_args)
        elif subevent_id == 1:
            marker_id, offset = decode_varint(payload, offset)
            marker_name, offset = decode_counted_string(payload, offset)
            context.marker_names[marker_id] = marker_name
            raw_args = {
                "subevent_id": subevent_id,
                "marker_id": marker_id,
                "name": marker_name,
            }
            display_args = {"marker_id": marker_id, "name": marker_name}
            detail = render_detail("NAME_MARKER", display_args)
        else:
            raw_args = {
                "subevent_id": subevent_id,
                "payload": decode_remaining_varints(payload[offset:]),
            }
            display_args = raw_args.copy()
            detail = render_detail("EX", display_args)
            offset = len(payload)
        if offset != len(payload):
            raise TraceParseError("unexpected payload size for EX")
        event_name = "NAME_MARKER" if subevent_id == 1 else "MARK" if subevent_id == 0 else "EX"
        return ParsedEvent(event_id, event_name, "extended", raw_args, display_args, detail)

    spec = descriptions.events.get(event_id)
    if spec:
        offset = 0
        for arg in spec.args:
            if offset >= len(payload):
                # Some FreeRTOS API events encode fewer args than the
                # description file lists (the upstream description is
                # generated and may overshoot). Stop reading once the
                # payload is exhausted; remaining args stay unset.
                break
            try:
                value, new_offset = decode_varint(payload, offset)
            except TraceParseError:
                break
            raw_args[arg.name] = value
            formats[arg.name] = arg.fmt
            offset = new_offset
        if offset < len(payload):
            extra = try_decode_remaining_varints(payload[offset:])
            if extra:
                raw_args["_extra"] = extra
        display_args = render_display_args(raw_args, formats, context, descriptions)
        if spec.name in {"i2c_tx_data", "i2c_rx_data"}:
            display_args = _rewrite_i2c_data_display_args(raw_args, display_args)
        return ParsedEvent(
            event_id=event_id,
            event_name=spec.name,
            event_type="custom",
            raw_args=raw_args,
            display_args=display_args,
            detail=render_detail(spec.name, display_args),
        )

    decoded_payload = try_decode_remaining_varints(payload)
    if decoded_payload is None:
        raw_args = {"payload_hex": payload.hex()}
    else:
        raw_args = {"payload": decoded_payload}
    return ParsedEvent(
        event_id=event_id,
        event_name=name,
        event_type="custom",
        raw_args=raw_args,
        display_args=raw_args.copy(),
        detail=render_detail(name, raw_args),
    )


################################################################################
# SystemView-format CSV converter
#
# Produces a CSV that matches the layout SEGGER's SystemView app emits via
# its headless `-export` mode. The columns, timestamp formatting, context
# state machine and per-event detail wording are all reproduced here so the
# output is byte-comparable against SystemView's own files.
################################################################################

# Context type tags packed into the high 32 bits of the contextint columns.
_SV_CTX_NONE = 0          # boot / "Idle" placeholder
_SV_CTX_ISR = 1           # currently inside an ISR
_SV_CTX_RETURN_IDLE = 3   # ISR exit returning straight back to idle
_SV_CTX_SCHEDULER = 4     # context = scheduler (between tasks)
_SV_CTX_TASK = 8          # context = a FreeRTOS task
_SV_CTX_SYSIDLE = 0x10    # context = SystemIdle (the IDLE task accumulating)

# SystemView UI display names per standard event id.
_SV_EVENT_NAMES = {
    0: "NOP",
    1: "Drop",
    2: "ISR Enter",
    3: "ISR Exit",
    4: "Task Run",
    5: "Task Stop",
    6: "Task Ready",
    7: "Task Block",
    8: "Task Create",
    9: "Task Info",
    10: "Start",
    11: "Stop",
    12: "System Time (cycles)",
    13: "System Time (us)",
    14: "System Description",
    15: "Mark Start",
    16: "Mark Stop",
    17: "System Idle",
    18: "ISR Exit",
    19: "Timer Enter",
    20: "Timer Exit",
    21: "Stack Info",
    22: "Module Description",
    24: "Init",
    25: "Resource Name",
    26: "Print",
    27: "Num Modules",
    28: "End Call",
    29: "Task Terminate",
    31: "EX",
}


def _format_sv_timestamp(cycles: int, freq: int) -> str:
    """0.001 668 756 — seconds with 9 decimals, 3-digit space-separated groups."""
    if freq <= 0:
        return "0.000 000 000"
    secs = cycles / freq
    text = f"{secs:.9f}"
    int_part, _, dec_part = text.partition(".")
    grouped = " ".join(dec_part[i : i + 3] for i in range(0, 9, 3))
    return f"{int_part}.{grouped}"


def _format_sv_context_int(type_tag: int, handle: int) -> str:
    """0xTTTTTTTTHHHHHHHH — 64-bit hex (high 32 type, low 32 shrunk id)."""
    return f"0x{type_tag:08X}{handle & 0xFFFFFFFF:08X}"


def _shorten_task_name(name: str) -> str:
    # SystemView truncates the task context column to 15 chars.
    return name[:15]


def _format_sv_context(type_tag: int, handle: int, task_names: dict[int, str], irq_names: dict[int, str]) -> str:
    if type_tag == _SV_CTX_NONE:
        return "Idle"
    if type_tag == _SV_CTX_ISR:
        return irq_names.get(handle, f"ISR {handle}")
    if type_tag == _SV_CTX_SCHEDULER:
        return "Scheduler"
    if type_tag == _SV_CTX_SYSIDLE:
        return "Idle"
    if type_tag == _SV_CTX_RETURN_IDLE:
        return "Idle"
    if type_tag == _SV_CTX_TASK:
        name = task_names.get(handle)
        if name:
            return _shorten_task_name(name)
        return f"Task 0x{handle:04X}"
    return "Idle"


def _format_us(us: float) -> str:
    if us >= 1000.0:
        return f"{us / 1000.0:.3f} ms"
    return f"{us:.3f} us"


@dataclass
class _SVEvent:
    seq: int
    offset: int
    size: int
    raw: bytes
    event_id: int
    payload: bytes
    cycles: int          # cumulative cycles after this event's timestamp delta
    delta: int           # raw delta varint value
    parsed: ParsedEvent | None  # decoded args (None for events we couldn't parse)


def _walk_sv_events(payload: bytes, descriptions: DescriptionDB) -> tuple[list[_SVEvent], TraceContext, list[str]]:
    """Walk the SVDat byte stream and decode every event in order.

    Returns the per-event records (with byte offset/size/raw bytes), the
    populated TraceContext (task names, IRQ names, sys_freq, etc.), and any
    parser warnings.
    """
    context = TraceContext()
    events: list[_SVEvent] = []
    warnings: list[str] = []
    body = parse_headerless_trace(payload)
    # Skip the leading SystemView RTT sync sequence (10 zero bytes). SystemView's
    # own export drops these from the CSV.
    sync_skip = 0
    while sync_skip < min(10, len(body)) and body[sync_skip] == 0:
        sync_skip += 1
    offset = sync_skip
    seq = 0
    cycles = 0

    while offset < len(body):
        event_offset = offset
        try:
            lead = body[offset]
            if lead < 24:
                event_id = lead
                offset += 1
                payload_start = offset
                payload_end = standard_payload_end(body, event_id, payload_start)
                ev_payload = body[payload_start:payload_end]
                try:
                    parsed = parse_standard_event(event_id, ev_payload, context, descriptions)
                except TraceParseError as exc:
                    warnings.append(f"event {event_id} @ {event_offset}: {exc}")
                    parsed = None
                delta, offset = decode_varint(body, payload_end)
            else:
                event_id, after_id = decode_varint(body, offset)
                payload_length, payload_start = decode_varint(body, after_id)
                payload_end = payload_start + payload_length
                if payload_end > len(body):
                    raise TraceParseError("payload runs past EOF")
                ev_payload = body[payload_start:payload_end]
                try:
                    parsed = parse_length_prefixed_event(event_id, ev_payload, context, descriptions)
                except TraceParseError as exc:
                    warnings.append(f"event {event_id} @ {event_offset}: {exc}")
                    parsed = None
                delta, offset = decode_varint(body, payload_end)
        except TraceParseError as exc:
            warnings.append(f"stopped parsing near offset {event_offset}: {exc}")
            break

        cycles = (cycles + delta) & 0xFFFFFFFFFFFFFFFF
        size = offset - event_offset
        events.append(
            _SVEvent(
                seq=seq,
                offset=event_offset,
                size=size,
                raw=bytes(body[event_offset:offset]),
                event_id=event_id,
                payload=bytes(ev_payload),
                cycles=cycles,
                delta=delta,
                parsed=parsed,
            )
        )
        seq += 1

    return events, context, warnings


def _compute_sv_contexts(events: list[_SVEvent]) -> list[tuple[int, int, int, int, int, int]]:
    """Compute (in_type, in_id, cur_type, cur_id, out_type, out_id) for each event.

    The state machine matches what SystemView's official exporter produces:

      Entering events    (in = old, current = NEW, out = NEW):
        - 4  TASK_START_EXEC      -> Task
        - 2  ISR_ENTER            -> ISR  (push previous on save stack)
        - 17 IDLE / SystemIdle    -> SystemIdle

      Exiting events     (in = old, current = OLD, out = NEW):
        - 5  TASK_STOP_EXEC       -> Scheduler
        - 7  TASK_STOP_READY      -> Scheduler
        - 3  ISR_EXIT             -> pop saved
        - 18 ISR_TO_SCHEDULER     -> Scheduler

      Pass-through events (in = current = out = unchanged):
        every other event id (queue ops, vTaskDelay, marker, etc.)
    """
    rows: list[tuple[int, int, int, int, int, int]] = []
    cur_type = _SV_CTX_NONE
    cur_id = 0
    saved_type = _SV_CTX_NONE
    saved_id = 0

    ENTERING = {2, 4, 17}
    EXITING = {3, 5, 7, 18}

    for ev in events:
        eid = ev.event_id
        args = ev.parsed.raw_args if ev.parsed else {}

        in_type, in_id = cur_type, cur_id
        new_type, new_id = cur_type, cur_id

        if eid == 4:  # TASK_START_EXEC
            new_type = _SV_CTX_TASK
            new_id = args.get("task_id", 0)
        elif eid == 5:  # TASK_STOP_EXEC
            new_type = _SV_CTX_SCHEDULER
            new_id = 0
        elif eid == 7:  # TASK_STOP_READY (Task Block)
            new_type = _SV_CTX_SCHEDULER
            new_id = 0
        elif eid == 17:  # IDLE / System Idle
            new_type = _SV_CTX_SYSIDLE
            new_id = 0
        elif eid == 2:  # ISR_ENTER
            saved_type, saved_id = cur_type, cur_id
            new_type = _SV_CTX_ISR
            new_id = args.get("irq_id", 0)
        elif eid == 3:  # ISR_EXIT  -> back to whatever was active
            new_type, new_id = saved_type, saved_id
        elif eid == 18:  # ISR_TO_SCHEDULER
            new_type = _SV_CTX_SCHEDULER
            new_id = 0

        if eid in ENTERING:
            # current column reflects the new context (we just switched in)
            rows.append((in_type, in_id, new_type, new_id, new_type, new_id))
            cur_type, cur_id = new_type, new_id
        elif eid in EXITING:
            # current column still shows the OLD context (this event is the
            # last thing it did); out moves to the new context.
            rows.append((in_type, in_id, in_type, in_id, new_type, new_id))
            cur_type, cur_id = new_type, new_id
        else:
            rows.append((cur_type, cur_id, cur_type, cur_id, cur_type, cur_id))

    return rows


def _format_sv_event_data(raw: bytes) -> str:
    return "".join(f"{b:02X} " for b in raw)


def _format_sv_detail(ev: _SVEvent, ctx_states: tuple[int, int, int, int, int, int],
                     events: list[_SVEvent], idx: int, context: TraceContext) -> str:
    """Render the human-friendly detail string for one event."""
    args = ev.parsed.raw_args if ev.parsed else {}
    eid = ev.event_id
    parsed = ev.parsed

    if eid == 0:  # NOP
        return ""
    if eid == 10:  # Trace Start
        return ""
    if eid == 11:  # Trace Stop
        return ""
    if eid == 1:  # Drop / Overflow
        return f"Dropped {args.get('drop_count', 0)} events"

    if eid == 24:  # Init
        return (
            f"Cycle Freq.: {args.get('sys_freq', 0)}, "
            f"CPU Freq.: {args.get('cpu_freq', 0)}, "
            f"ID Base: 0x{args.get('ram_base', 0):X}, "
            f"ID Shift: {args.get('id_shift', 0)}"
        )

    if eid == 14:  # System Description
        return args.get("sysdesc", "")

    if eid == 13:  # System Time (us)
        return f"{args.get('systime_us', 0)} us"

    if eid == 12:  # System Time (cycles)
        return f"{args.get('systime_cycles', 0)} cycles"

    if eid == 9:  # Task Info
        task_id = args.get("task_id", 0)
        prio = args.get("priority", 0)
        name = args.get("name", "")
        return f"{name} (0x{task_id:08X}): Priority={prio}"

    if eid == 21:  # Stack Info
        task_id = args.get("task_id", 0)
        stack_base = args.get("stack_base", 0)
        stack_size = args.get("stack_size", 0)
        stack_value = args.get("stack_usage", 0)
        name = context.task_names.get(task_id, "")
        prefix = f"{name} (0x{task_id:08X})" if name else f"0x{task_id:08X}"
        return f"{prefix}: {stack_value}/{stack_size} bytes used @ 0x{stack_base:08X}"

    if eid == 25:  # Resource Name
        rid = args.get("resource_id", 0)
        return f"0x{rid:08X}: {args.get('name', '')}"

    if eid == 22:  # Module Description
        return args.get("description", "")

    if eid == 27:  # Num Modules
        return f"Registered Modules: {args.get('num_modules', 0)}"

    if eid == 2:  # ISR Enter
        # Look ahead for the matching ISR Exit (id 3 or 18) to compute "Runs for".
        depth = 1
        for j in range(idx + 1, len(events)):
            nxt = events[j].event_id
            if nxt == 2:
                depth += 1
            elif nxt in (3, 18):
                depth -= 1
                if depth == 0:
                    delta_cycles = events[j].cycles - ev.cycles
                    if context.sys_freq:
                        return f"Runs for {_format_us(delta_cycles * 1_000_000.0 / context.sys_freq)}"
                    return ""
        return ""

    if eid == 3:  # ISR Exit (back to previous)
        prev_type = ctx_states[2]
        if prev_type == _SV_CTX_NONE or prev_type == _SV_CTX_SYSIDLE or prev_type == _SV_CTX_RETURN_IDLE:
            return "Returns to Idle"
        if prev_type == _SV_CTX_SCHEDULER:
            return "Returns to Scheduler"
        if prev_type == _SV_CTX_TASK:
            handle = ctx_states[3]
            name = context.task_names.get(handle)
            return f"Returns to {name}" if name else f"Returns to Task 0x{handle:04X}"
        return ""

    if eid == 18:  # ISR Exit -> Scheduler
        return "Returns to Scheduler"

    if eid == 4:  # Task Run
        # Find next event that takes the CPU away (Block, Stop, ISR_ENTER).
        for j in range(idx + 1, len(events)):
            nxt = events[j].event_id
            if nxt in (2, 5, 7, 17):
                delta_cycles = events[j].cycles - ev.cycles
                if context.sys_freq:
                    return f"Runs for {_format_us(delta_cycles * 1_000_000.0 / context.sys_freq)}"
                break
        return ""

    if eid == 7:  # Task Block
        return "Delayed"

    if eid == 5:  # Task Stop
        return ""

    if eid == 6:  # Task Ready
        task_id = args.get("task_id", 0)
        # Find when this task is actually next run (event 4 with same task_id).
        for j in range(idx + 1, len(events)):
            n = events[j]
            if n.event_id == 4 and n.parsed and n.parsed.raw_args.get("task_id") == task_id:
                delta_cycles = n.cycles - ev.cycles
                if context.sys_freq:
                    name = context.task_names.get(task_id) or f"Task 0x{task_id:04X}"
                    return f"{name}, runs after {_format_us(delta_cycles * 1_000_000.0 / context.sys_freq)}"
                break
        name = context.task_names.get(task_id) or f"Task 0x{task_id:04X}"
        return name

    if eid == 17:  # System Idle
        for j in range(idx + 1, len(events)):
            n = events[j]
            if n.event_id in (2, 4, 6):
                delta_cycles = n.cycles - ev.cycles
                if context.sys_freq:
                    return f"Idle for {_format_us(delta_cycles * 1_000_000.0 / context.sys_freq)}"
                break
        return ""

    # Length-prefixed / FreeRTOS API events: use the description-driven name
    # and assemble "name = value" pairs in declaration order.
    if parsed and parsed.event_type == "custom" and parsed.display_args:
        bits = []
        for key, value in parsed.display_args.items():
            if key == "_extra":
                continue
            bits.append(f"{key} = {value}")
        return " ".join(bits)

    if parsed and parsed.detail:
        return parsed.detail

    return ""


def _sv_event_label(ev: _SVEvent) -> str:
    """Return the SystemView UI label for this event."""
    if ev.event_id < 24:
        return _SV_EVENT_NAMES.get(ev.event_id, f"Event {ev.event_id}")
    if ev.event_id in _SV_EVENT_NAMES:
        return _SV_EVENT_NAMES[ev.event_id]
    if ev.parsed and ev.parsed.event_name and ev.parsed.event_name not in {"NAME_RESOURCE", "MODULEDESC"}:
        return ev.parsed.event_name
    return f"Event {ev.event_id}"


def _write_systemview_csv(data: bytes, output_path: Path, descriptions: DescriptionDB,
                          error_log: "ErrorLog | None" = None) -> list[str]:
    events, context, warnings = _walk_sv_events(data, descriptions)
    contexts = _compute_sv_contexts(events)

    # Surface every unique event id that we couldn't resolve from either
    # the standard event table or the loaded description files. These show
    # up as "Event N" in the CSV and indicate either a corrupt/torn frame
    # or (more likely) that the wallet description directory is out of
    # date relative to the firmware build.
    if error_log is not None:
        unknown_ids: set[int] = set()
        for ev in events:
            if ev.event_id == 0:
                continue
            if ev.event_id < 24 and ev.event_id in STANDARD_EVENT_NAMES:
                continue
            if ev.event_id in STANDARD_EVENT_NAMES:
                continue
            if ev.event_id in descriptions.events:
                continue
            unknown_ids.add(ev.event_id)
        for eid in sorted(unknown_ids):
            error_log.add(
                "description",
                f"event id {eid} has no entry in the description files "
                f"(possible corruption or stale SYSVIEW_*.txt)",
            )

        # Surface every parser warning collected during the walk so the
        # log captures torn-frame / EOF / payload mismatch cases.
        for w in warnings:
            error_log.add("parse", w)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    seq_out = 0
    base_cycles: int | None = None
    with output_path.open("w", encoding="utf-8", newline="") as fp:
        fp.write(
            "sequencenum,timestamp,context,event,detail,timestampint,contextinint,"
            "contextint,contextoutint,eventint,eventoffset,eventsize,eventdata\n"
        )
        for idx, ev in enumerate(events):
            # SystemView's CSV exporter drops NOP padding events.
            if ev.event_id == 0:
                continue

            if base_cycles is None:
                base_cycles = ev.cycles

            ctx_state = contexts[idx]
            in_type, in_id, cur_type, cur_id, out_type, out_id = ctx_state

            label = _sv_event_label(ev)
            detail = _format_sv_detail(ev, ctx_state, events, idx, context)

            relative_cycles = ev.cycles - base_cycles
            ts = _format_sv_timestamp(relative_cycles, context.sys_freq or 1)
            cur_name = _format_sv_context(cur_type, cur_id, context.task_names, context.irq_names)

            row_context = f'"{cur_name}"'
            row_event = f'"{label}"'
            row_detail = f'"{detail}"'

            fp.write(
                f"{seq_out},{ts},{row_context},{row_event},{row_detail},"
                f"{relative_cycles},"
                f"{_format_sv_context_int(in_type, in_id)},"
                f"{_format_sv_context_int(cur_type, cur_id)},"
                f"{_format_sv_context_int(out_type, out_id)},"
                f"{ev.event_id},{ev.offset},{ev.size},{_format_sv_event_data(ev.raw)}\n"
            )
            seq_out += 1

    return warnings


def convert_trace_to_csv(input_path: Path, output_path: Path, description_path: Path,
                         error_log: "ErrorLog | None" = None) -> list[str]:
    """Convert a captured .SVDat into a SystemView-format CSV."""
    descriptions = DescriptionDB.from_path(description_path)
    return _write_systemview_csv(input_path.read_bytes(), output_path, descriptions, error_log)


def _legacy_convert_trace_to_csv_unused(input_path: Path, output_path: Path, description_path: Path) -> list[str]:
    """Convert a captured .SVDat into a CSV that matches SystemView's own
    headless export format byte-for-byte (column set, timestamp formatting,
    context state, detail strings, eventdata hex dump).
    """
    descriptions = DescriptionDB.from_path(description_path)
    data = input_path.read_bytes()
    return _write_systemview_csv(data, output_path, descriptions)


def parse_trace_with_deltas(data: bytes, descriptions: DescriptionDB) -> tuple[list[dict[str, Any]], list[str]]:
    payload = parse_headerless_trace(data)
    context = TraceContext()
    rows: list[dict[str, Any]] = []
    warnings: list[str] = []
    offset = 0
    index = 0

    while offset < len(payload):
        try:
            lead = payload[offset]
            event_offset = offset
            if lead < 24:
                event_id = lead
                offset += 1
                payload_start = offset
                payload_end = standard_payload_end(payload, event_id, payload_start)

                parsed = parse_standard_event(
                    event_id, payload[payload_start:payload_end], context, descriptions
                )
                delta, offset = decode_varint(payload, payload_end)
            else:
                event_id, offset = decode_varint(payload, offset)
                payload_length, offset = decode_varint(payload, offset)
                payload_end = offset + payload_length
                if payload_end > len(payload):
                    raise TraceParseError("unexpected end of file while reading payload")
                parsed = parse_length_prefixed_event(
                    event_id, payload[offset:payload_end], context, descriptions
                )
                delta, offset = decode_varint(payload, payload_end)
        except TraceParseError as exc:
            remaining = len(payload) - offset
            if remaining < 64:
                warnings.append(f"stopped parsing near EOF: {exc}")
                break
            raise

        context.timestamp_cycles = (context.timestamp_cycles + delta) & 0xFFFFFFFF
        if parsed.event_id == 13 and "systime_us" in parsed.raw_args:
            relative_us = context.relative_us()
            if relative_us is not None:
                context.absolute_us_offset = parsed.raw_args["systime_us"] - relative_us

        index += 1
        rows.append(
            {
                "index": index,
                "offset": event_offset,
                "timestamp_cycles": context.timestamp_cycles,
                "timestamp_us": (
                    f"{context.relative_us():.6f}" if context.relative_us() is not None else ""
                ),
                "absolute_us": (
                    f"{context.absolute_us():.6f}" if context.absolute_us() is not None else ""
                ),
                "event_id": parsed.event_id,
                "event_name": parsed.event_name,
                "event_type": parsed.event_type,
                "detail": parsed.detail,
                "args_json": json.dumps(
                    parsed.display_args, separators=(",", ":"), ensure_ascii=True
                ),
                "raw_args_json": json.dumps(
                    parsed.raw_args, separators=(",", ":"), ensure_ascii=True
                ),
            }
        )

    return rows, warnings


class ErrorLog:
    """Collects errors/warnings encountered during capture and conversion.

    Repeats of the same (category, message) pair are coalesced into a single
    entry with a count, so a transient probe error that fires every poll does
    not balloon the log into the megabytes.
    """

    def __init__(self, path: Path | None) -> None:
        self.path = path
        self.entries: list[tuple[float, str, str, int]] = []
        self._index: dict[tuple[str, str], int] = {}
        self.fatal = False

    def add(self, category: str, message: str, *, fatal: bool = False) -> None:
        if fatal:
            self.fatal = True
        key = (category, message)
        idx = self._index.get(key)
        now = time.time()
        if idx is None:
            self._index[key] = len(self.entries)
            self.entries.append((now, category, message, 1))
        else:
            ts, cat, msg, count = self.entries[idx]
            self.entries[idx] = (ts, cat, msg, count + 1)

    def has_errors(self) -> bool:
        return bool(self.entries)

    def flush(self) -> None:
        if self.path is None:
            return
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.path.open("a", encoding="utf-8") as fp:
            if not self.entries:
                stamp = time.strftime("%Y-%m-%d %H:%M:%S")
                fp.write(f"[{stamp}] info: capture finished with no errors.\n")
                return
            for ts, category, message, count in self.entries:
                stamp = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(ts))
                suffix = f" (x{count})" if count > 1 else ""
                fp.write(f"[{stamp}] {category}: {message}{suffix}\n")


def _raise_process_priority(error_log: ErrorLog) -> None:
    """Best-effort: raise current process priority for steady RTT polling.

    macOS does not allow non-root processes to set a negative `nice` value,
    so the wrapper script (`capture_trace.sh`) launches us via
    `taskpolicy -c user-interactive -l 1 -t 1` which puts the process into
    the highest user QoS band without requiring sudo. We additionally try
    `os.setpriority(PRIO_PROCESS, -20)` here so that runs launched via
    `sudo` (or any process with CAP_SYS_NICE) get the full boost. The
    function never raises — failures are recorded in the error log so the
    caller can decide what to do.
    """
    try:
        current = os.getpriority(os.PRIO_PROCESS, 0)
    except Exception as exc:  # noqa: BLE001
        error_log.add("priority", f"getpriority failed: {exc}")
        return

    new = current
    for target in (-20, -15, -10, -5, -1):
        try:
            os.setpriority(os.PRIO_PROCESS, 0, target)
            new = target
            break
        except (PermissionError, OSError):
            continue

    in_taskpolicy = os.environ.get("DYLD_INSERT_LIBRARIES", "")  # weak hint
    qos_hint = "taskpolicy" if "taskpolicy" in in_taskpolicy.lower() else ""
    if new < current:
        print(f"Process nice {current} -> {new}.{(' ' + qos_hint) if qos_hint else ''}")
    elif sys.platform == "darwin":
        # If we couldn't lower nice but the wrapper bumped QoS via taskpolicy
        # we still get a meaningful boost. Be quiet rather than alarming.
        print(f"Process nice {current} (run via sudo for negative nice).")
    else:
        print(f"Process nice {current}.")


def _stdin_has_line() -> bool:
    """Return True if a line is waiting on stdin (non-blocking)."""
    if not sys.stdin.isatty():
        return False
    try:
        ready, _, _ = select.select([sys.stdin], [], [], 0)
    except (OSError, ValueError):
        return False
    return bool(ready)


def _format_rate(bytes_per_sec: float) -> str:
    if bytes_per_sec >= 1024 * 1024:
        return f"{bytes_per_sec / (1024 * 1024):.2f} MiB/s"
    if bytes_per_sec >= 1024:
        return f"{bytes_per_sec / 1024:.2f} KiB/s"
    return f"{bytes_per_sec:.0f} B/s"


def _format_size(num_bytes: int) -> str:
    if num_bytes >= 1024 * 1024:
        return f"{num_bytes / (1024 * 1024):.2f} MiB"
    if num_bytes >= 1024:
        return f"{num_bytes / 1024:.2f} KiB"
    return f"{num_bytes} B"


def _wait_for_rtt_buffers(
    jlink: Any,
    channel: int,
    error_log: ErrorLog,
    *,
    timeout_s: float,
) -> tuple[int, int]:
    deadline = time.monotonic() + timeout_s
    last_error: str | None = None
    num_up = 0
    num_down = 0
    while time.monotonic() < deadline:
        try:
            num_up = jlink.rtt_get_num_up_buffers()
            num_down = jlink.rtt_get_num_down_buffers()
            if num_up > channel and num_down > channel:
                return (num_up, num_down)
        except Exception as exc:  # noqa: BLE001
            last_error = str(exc)
        time.sleep(0.05)

    if last_error:
        error_log.add("rtt-start", f"buffer discovery transient error: {last_error}")
    return (num_up, num_down)


def _send_rtt_command(
    jlink: Any,
    channel: int,
    command_id: int,
    *,
    label: str,
    error_log: ErrorLog,
    timeout_s: float,
) -> bool:
    deadline = time.monotonic() + timeout_s
    last_error: str | None = None
    while time.monotonic() < deadline:
        try:
            written = jlink.rtt_write(channel, [command_id])
            if written == 1:
                return True
            if written not in (0, 1):
                last_error = f"unexpected write result {written}"
        except Exception as exc:  # noqa: BLE001
            last_error = str(exc)
        time.sleep(RTT_COMMAND_RETRY_DELAY_S)

    if last_error:
        error_log.add(f"sysview-{label}", last_error)
        print(f"warning: failed to send SystemView {label.upper()}: {last_error}", file=sys.stderr)
    else:
        error_log.add(
            f"sysview-{label}",
            f"timed out waiting to write command {command_id} to RTT down-channel {channel}",
        )
        print(
            f"warning: timed out sending SystemView {label.upper()} on RTT down-channel {channel}",
            file=sys.stderr,
        )
    return False


def scan_svdat_for_overflows(svdat_path: Path) -> tuple[int, int]:
    """Return (overflow_event_count, total_dropped_events) by parsing the SVDat.

    SystemView emits standard event ID 1 (OVERFLOW) with a single varint
    payload representing the number of dropped events since the last overflow
    record. We do a minimal stream walk so this stays cheap even on large
    captures and does not depend on the full CSV parser.
    """
    if not svdat_path.exists():
        return (0, 0)
    try:
        data = parse_headerless_trace(svdat_path.read_bytes())
    except Exception:  # noqa: BLE001
        return (0, 0)

    overflow_records = 0
    dropped_total = 0
    offset = 0
    n = len(data)
    while offset < n:
        first = data[offset]
        offset += 1
        if first == 0:
            # NOP padding
            continue
        if first < 24:  # standard event id
            try:
                payload_end = standard_payload_end(data, first, offset)
                if first == 1:
                    drop_count, _ = decode_varint(data, offset)
                    overflow_records += 1
                    dropped_total += drop_count
                offset = payload_end
                # standard events have a trailing timestamp varint
                _, offset = decode_varint(data, offset)
            except TraceParseError:
                break
            continue
        if first < 0x80:  # non-standard event with inline length
            event_id = first >> 1
            del event_id
            try:
                length, offset = decode_varint(data, offset)
                offset += length
                _, offset = decode_varint(data, offset)
            except TraceParseError:
                break
            continue
        # Multi-byte event id
        try:
            event_id, offset = decode_varint(data, offset - 1)
            length, offset = decode_varint(data, offset)
            offset += length
            _, offset = decode_varint(data, offset)
        except TraceParseError:
            break
    return overflow_records, dropped_total


def pylink_capture(
    args: argparse.Namespace,
    svdat_path: Path,
    error_log: ErrorLog,
) -> int:
    """Capture a SystemView trace with pylink-square.

    Uses a single J-Link connection so we can both write the SystemView
    START/STOP byte on the RTT down-channel and read events from the
    up-channel without contending for the probe.
    """
    try:
        import pylink  # type: ignore
    except ImportError:
        error_log.add("setup", "pylink-square not installed", fatal=True)
        print("error: pylink-square is required. Install with: pip install pylink-square", file=sys.stderr)
        return 1

    _raise_process_priority(error_log)

    device = args.device or PLATFORM_DEVICES[args.platform]
    channel = args.rtt_channel
    read_chunk = 16384  # bigger reads = fewer round trips, less risk of overflow

    jlink = pylink.JLink()
    try:
        if args.usb:
            jlink.open(serial_no=int(args.usb))
        elif args.jlink_ip:
            jlink.open(ip_addr=args.jlink_ip)
        else:
            jlink.open()
    except Exception as exc:  # noqa: BLE001
        error_log.add("jlink-open", str(exc))
        print(f"error: unable to open J-Link: {exc}", file=sys.stderr)
        return 1

    try:
        try:
            iface_map = {
                "SWD": pylink.enums.JLinkInterfaces.SWD,
                "JTAG": pylink.enums.JLinkInterfaces.JTAG,
                "FINE": pylink.enums.JLinkInterfaces.FINE,
            }
            jlink.set_tif(iface_map[args.interface])
            jlink.connect(device, speed=args.speed)
        except Exception as exc:  # noqa: BLE001
            error_log.add("jlink-connect", str(exc))
            print(f"error: J-Link connect failed: {exc}", file=sys.stderr)
            return 1

        # Resolve the RTT control block hint:
        #   1. explicit --rttcbaddr        -> exact address
        #   2. explicit --rttcbrange       -> custom search range
        #   3. otherwise platform default  -> e.g. w3-uxc SRAM4
        rtt_cb_addr: int | None = None
        if args.rttcbaddr:
            try:
                rtt_cb_addr = int(args.rttcbaddr, 0)
            except ValueError:
                error_log.add("rtt-start", f"invalid --rttcbaddr: {args.rttcbaddr}", fatal=True)
                return 1
        elif not args.rttcbrange and args.platform in PLATFORM_RTT_CB_ADDR:
            # Use the hardcoded fixed-location address from the linker script.
            rtt_cb_addr = PLATFORM_RTT_CB_ADDR[args.platform]

        if args.rttcbrange:
            try:
                base_str, len_str = args.rttcbrange.split()
                base = int(base_str, 0)
                length = int(len_str, 0)
                jlink.exec_command(f"SetRTTSearchRanges {base} {length}")
                print(f"RTT search range: 0x{base:08X} +0x{length:X}")
            except Exception as exc:  # noqa: BLE001
                error_log.add("rtt-start", f"unable to set RTT search range: {exc}")

        try:
            jlink.rtt_start(rtt_cb_addr)
            if rtt_cb_addr is not None:
                print(f"RTT control block: 0x{rtt_cb_addr:08X}")
        except Exception as exc:  # noqa: BLE001
            error_log.add("rtt-start", str(exc), fatal=True)
            print(f"error: rtt_start failed: {exc}", file=sys.stderr)
            return 1

        num_up, num_down = _wait_for_rtt_buffers(
            jlink,
            channel,
            error_log,
            timeout_s=RTT_BUFFER_DISCOVERY_TIMEOUT_S,
        )
        if num_up <= channel or num_down <= channel:
            error_log.add(
                "rtt-start",
                (
                    f"RTT channel {channel} not ready within {RTT_BUFFER_DISCOVERY_TIMEOUT_S:.1f}s "
                    f"(up={num_up}, down={num_down})"
                ),
                fatal=True,
            )
            print(
                (
                    "error: target did not advertise the expected RTT buffers "
                    f"for channel {channel} (up={num_up}, down={num_down})."
                ),
                file=sys.stderr,
            )
            return 1

        # The probe may briefly report the buffers before the target-side RTT
        # descriptors are ready to accept down-channel writes. Give them a
        # moment to settle so START reliably lands in the SystemView buffer.
        time.sleep(RTT_BUFFER_SETTLE_DELAY_S)

        # Send the SystemView START byte on the down-channel.
        start_sent = _send_rtt_command(
            jlink,
            channel,
            SYSVIEW_CMD_START,
            label="start",
            error_log=error_log,
            timeout_s=RTT_COMMAND_TIMEOUT_S,
        )
        if not start_sent:
            error_log.add(
                "sysview-start",
                "failed to arm target-side SystemView recording",
                fatal=True,
            )
            return 1

        print(f"Headless RTT capture started for {device} on channel {channel}.")
        print(f"Trace will be saved to {svdat_path}")
        if args.time:
            print(f"Time limit: {args.time}s")
        print("Press Enter (or Ctrl-C) to stop capture.")

        bytes_total = 0
        last_report_t = time.monotonic()
        last_report_bytes = 0
        start_t = last_report_t
        deadline_t = start_t + args.time if args.time else None
        stop_reason = "user"
        connection_lost = False
        rtt_error_count = 0

        with svdat_path.open("wb") as svdat_fp:
            try:
                while True:
                    if deadline_t is not None and time.monotonic() >= deadline_t:
                        stop_reason = "time-limit"
                        break
                    if _stdin_has_line():
                        try:
                            sys.stdin.readline()
                        except Exception:  # noqa: BLE001
                            pass
                        stop_reason = "user"
                        break

                    try:
                        chunk = jlink.rtt_read(channel, read_chunk)
                    except Exception as exc:  # noqa: BLE001
                        msg = str(exc)
                        rtt_error_count += 1
                        # A lost J-Link connection is non-recoverable from
                        # within the same session and means we are dropping
                        # data on the target. Bail out immediately so the
                        # caller sees a non-zero exit and the partial trace.
                        if "lost" in msg.lower() or "connection" in msg.lower():
                            error_log.add("rtt-read", msg, fatal=True)
                            print(
                                f"error: J-Link connection lost during capture: {msg}",
                                file=sys.stderr,
                            )
                            connection_lost = True
                            stop_reason = "connection-lost"
                            break
                        error_log.add("rtt-read", msg)
                        chunk = []

                    if chunk:
                        buf = bytes(chunk)
                        svdat_fp.write(buf)
                        bytes_total += len(buf)
                        # Drain everything that's queued before yielding so the
                        # target buffer doesn't have a chance to overflow.
                        while True:
                            try:
                                more = jlink.rtt_read(channel, read_chunk)
                            except Exception:  # noqa: BLE001
                                more = []
                            if not more:
                                break
                            buf = bytes(more)
                            svdat_fp.write(buf)
                            bytes_total += len(buf)
                        svdat_fp.flush()
                    else:
                        # Buffer empty: very short sleep to avoid pegging a CPU.
                        time.sleep(0.001)

                    now = time.monotonic()
                    if now - last_report_t >= 1.0:
                        delta_bytes = bytes_total - last_report_bytes
                        rate = delta_bytes / (now - last_report_t)
                        print(
                            f"  [{int(now - start_t):4d}s] size={_format_size(bytes_total)}  rate={_format_rate(rate)}",
                            flush=True,
                        )
                        last_report_t = now
                        last_report_bytes = bytes_total
            except KeyboardInterrupt:
                print("\nInterrupted, stopping capture...")
                stop_reason = "ctrl-c"

            # Send the SystemView STOP byte and drain remaining data
            # (skip if the probe has already gone away).
            if not connection_lost:
                _send_rtt_command(
                    jlink,
                    channel,
                    SYSVIEW_CMD_STOP,
                    label="stop",
                    error_log=error_log,
                    timeout_s=RTT_COMMAND_TIMEOUT_S,
                )

                drain_deadline = time.monotonic() + STOP_FLUSH_DELAY_S
                while time.monotonic() < drain_deadline:
                    try:
                        chunk = jlink.rtt_read(channel, read_chunk)
                    except Exception:  # noqa: BLE001
                        chunk = []
                    if not chunk:
                        time.sleep(0.01)
                        continue
                    buf = bytes(chunk)
                    svdat_fp.write(buf)
                    bytes_total += len(buf)

        elapsed = time.monotonic() - start_t
        avg_rate = bytes_total / elapsed if elapsed > 0 else 0.0
        print(
            f"Stop reason: {stop_reason}. Captured {_format_size(bytes_total)} in {elapsed:.1f}s "
            f"(avg {_format_rate(avg_rate)})."
        )
        if bytes_total == 0:
            error_log.add(
                "capture",
                "no bytes received from RTT up-buffer",
                fatal=True,
            )
            print("warning: no bytes captured. Is the target running and SystemView enabled?", file=sys.stderr)
        if rtt_error_count and not connection_lost:
            error_log.add("rtt-read", f"transient rtt_read errors: {rtt_error_count}")

    finally:
        try:
            jlink.rtt_stop()
        except Exception:  # noqa: BLE001
            pass
        try:
            jlink.close()
        except Exception:  # noqa: BLE001
            pass

    return 2 if connection_lost else 0


def run_capture(args: argparse.Namespace) -> int:
    if args.input_svdat:
        if args.svdat:
            return die("--svdat cannot be used with --input-svdat.")
        if not args.csv:
            return die("--input-svdat requires --csv.")

    if args.rttcbaddr and args.rttcbrange:
        return die("Use only one of --rttcbaddr or --rttcbrange.")

    script_dir = Path(__file__).resolve().parent
    default_description = script_dir.parent / "Description"

    stem = time.strftime("%Y%m%d-%H%M%S")
    svdat_path = (
        Path(args.input_svdat).expanduser().resolve()
        if args.input_svdat
        else ensure_output_path(args.svdat, ".SVDat", stem).resolve()
    )
    # csv and log default to the same Downloads/<stem>.{csv,log} pair when no
    # explicit path is supplied. csv generation is suppressed only by --no-csv.
    csv_path = ensure_output_path(args.csv, ".csv", stem).resolve() if not args.no_csv else None
    description_path = None
    if csv_path is not None:
        description_path = (
            Path(args.description).expanduser().resolve()
            if args.description
            else default_description.resolve()
        )
        if not description_path.exists():
            return die(f"description path does not exist: {description_path}")

    log_path = ensure_output_path(args.log, ".log", stem).resolve()
    error_log = ErrorLog(log_path)

    if args.input_svdat:
        if not svdat_path.exists():
            return die(f"input trace does not exist: {svdat_path}")
        warnings = convert_trace_to_csv(svdat_path, csv_path, description_path, error_log)
        print(f"Converted trace: {svdat_path}")
        print(f"Exported CSV: {csv_path}")
        for warning in warnings:
            print(f"warning: {warning}", file=sys.stderr)
            error_log.add("csv-convert", warning)
        error_log.flush()
        return 0

    if args.dry_run:
        print("Mode            : headless (pylink)")
        print(f"Device          : {args.device or PLATFORM_DEVICES[args.platform]}")
        print(f"Interface       : {args.interface} @ {args.speed} kHz")
        print(f"RTT channel     : {args.rtt_channel}")
        print(f"SVDat output    : {svdat_path}")
        if csv_path is not None:
            print(f"CSV output      : {csv_path}")
            print(f"Description dir : {description_path}")
        if args.time:
            print(f"Time limit      : {args.time}s")
        if log_path is not None:
            print(f"Error log       : {log_path}")
        return 0

    svdat_path.parent.mkdir(parents=True, exist_ok=True)
    if csv_path is not None:
        csv_path.parent.mkdir(parents=True, exist_ok=True)

    rc = pylink_capture(args, svdat_path, error_log)

    if not svdat_path.exists():
        error_log.add("capture", f"trace file not created: {svdat_path}", fatal=True)
        error_log.flush()
        return die(f"expected trace file was not created: {svdat_path}")

    print(f"Saved trace: {svdat_path}")

    # Detect target-side data loss (SystemView OVERFLOW events).
    overflow_records, dropped_events = scan_svdat_for_overflows(svdat_path)
    if overflow_records:
        msg = (
            f"target reported {overflow_records} OVERFLOW records, "
            f"{dropped_events} events dropped"
        )
        error_log.add("data-loss", msg, fatal=True)
        print(f"error: {msg}", file=sys.stderr)
    else:
        print("Data loss check: no OVERFLOW events.")

    if csv_path is not None:
        try:
            warnings = convert_trace_to_csv(svdat_path, csv_path, description_path, error_log)
            print(f"Exported CSV: {csv_path}")
            for warning in warnings:
                print(f"warning: {warning}", file=sys.stderr)
                error_log.add("csv-convert", warning)
        except Exception as exc:  # noqa: BLE001
            error_log.add("csv-convert", str(exc), fatal=True)
            print(f"error: csv conversion failed: {exc}", file=sys.stderr)
            error_log.flush()
            return 1

    error_log.flush()
    if log_path is not None:
        suffix = "" if error_log.has_errors() else " (no errors)"
        print(f"Error log       : {log_path}{suffix}")

    print(f"\nTo view the trace:\n  open -a SystemView {shlex_quote(str(svdat_path))}")

    if rc != 0:
        return rc
    if error_log.fatal:
        return 2
    return 0


def shlex_quote(value: str) -> str:
    if re.fullmatch(r"[A-Za-z0-9_./:-]+", value):
        return value
    return "'" + value.replace("'", "'\"'\"'") + "'"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Capture a SEGGER SystemView trace over RTT without the SystemView GUI and "
            "optionally convert it to CSV using repo-local tooling. The script also "
            "writes the SystemView START/STOP commands over the RTT down-channel."
        )
    )
    parser.add_argument(
        "--svdat",
        help="Output .SVDat path. Defaults to ~/Downloads/<datetime>.SVDat.",
    )
    parser.add_argument(
        "--csv",
        help="Output .csv path. Defaults to ~/Downloads/<datetime>.csv.",
    )
    parser.add_argument(
        "--no-csv",
        action="store_true",
        help="Skip CSV conversion entirely.",
    )
    parser.add_argument(
        "--input-svdat",
        help="Skip capture and convert an existing trace to CSV. Requires --csv.",
    )
    parser.add_argument(
        "--description",
        help="Optional description file or directory used for CSV conversion. Defaults to sysview/Description.",
    )
    parser.add_argument(
        "--platform",
        choices=sorted(PLATFORM_DEVICES),
        default=DEFAULT_PLATFORM,
        help=f"Target platform. Default: {DEFAULT_PLATFORM}.",
    )
    parser.add_argument("--device", help="Explicit J-Link target device name.")
    parser.add_argument("--usb", help="Optional J-Link serial number.")
    parser.add_argument("--jlink-ip", help="Optional J-Link IP / hostname.")
    parser.add_argument(
        "--interface",
        choices=["SWD", "JTAG", "FINE"],
        default=DEFAULT_INTERFACE,
        help=f"J-Link target interface. Default: {DEFAULT_INTERFACE}.",
    )
    parser.add_argument(
        "--speed",
        type=int,
        default=DEFAULT_SPEED_KHZ,
        help=f"J-Link speed in kHz. Default: {DEFAULT_SPEED_KHZ}.",
    )
    parser.add_argument(
        "--rtt-channel",
        type=int,
        default=DEFAULT_RTT_CHANNEL,
        help=f"RTT channel used by SystemView. Default: {DEFAULT_RTT_CHANNEL}.",
    )
    parser.add_argument("--rttcbaddr", help="Optional RTT control block address.")
    parser.add_argument(
        "--rttcbrange",
        help='Optional RTT search range, e.g. "0x20008000 0x1000".',
    )
    parser.add_argument(
        "--rtt-telnet-port",
        type=int,
        default=DEFAULT_RTT_TELNET_PORT,
        help=(
            "Local J-Link RTT telnet port used for the RTT down-channel START/STOP "
            f"writes. Default: {DEFAULT_RTT_TELNET_PORT}."
        ),
    )
    parser.add_argument("--rttlogger", help="Optional JLinkRTTLogger path (legacy/unused).")
    parser.add_argument(
        "--time",
        type=float,
        default=0.0,
        help="Capture time limit in seconds. 0 = run until Enter or Ctrl-C.",
    )
    parser.add_argument(
        "--log",
        help="Error log path. Defaults to ~/Downloads/<datetime>.log.",
    )
    parser.add_argument("--dry-run", action="store_true", help="Print commands and exit.")
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        return run_capture(args)
    except Exception as exc:  # noqa: BLE001
        return die(str(exc))


if __name__ == "__main__":
    raise SystemExit(main())

"""Feedback-loop pipeline stages.

harvest -> normalize -> classify -> cluster -> triage -> propose -> emit

Each stage module exposes a typed function with a documented contract and a stub that raises
NotImplementedError pointing at its implementing ticket. The CLI (feedback_loop/cli.py) wires them
together so the control flow is reviewable before any stage is implemented.
"""

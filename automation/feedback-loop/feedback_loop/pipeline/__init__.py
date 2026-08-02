"""Feedback-loop pipeline stages.

harvest -> normalize -> facts -> noise -> llm_classify -> llm_cluster -> triage -> llm_evaluator
-> readiness -> plan_cluster_memory_upserts -> emit

Each stage module exposes a typed function with a documented contract. The CLI
(feedback_loop/cli.py) wires them together so the control flow is reviewable.
"""

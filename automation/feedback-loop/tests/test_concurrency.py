"""Tests for bounded fan-out helpers and stage parity under concurrency."""

from __future__ import annotations

import os
import sys
import threading
import time
import unittest
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.cluster_memory import ClusterMemoryReadResult  # noqa: E402
from feedback_loop.concurrency import (  # noqa: E402
    harvest_max_workers,
    llm_max_workers,
    parallel_map_indexed,
)
from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.llm import FakeLlmClient, LlmClientError, ThrottledLlmClient  # noqa: E402
from feedback_loop.pipeline.llm_classify import classify_signals  # noqa: E402
from feedback_loop.pipeline.llm_evaluator import evaluate_llm_learnings  # noqa: E402
from feedback_loop.replay_gate import run_replay_gate  # noqa: E402
from tests.test_llm_classify import classification, feedback_signal, pr_facts  # noqa: E402
from tests.test_llm_evaluator import (  # noqa: E402
    cluster,
    extractor_response,
    fake_repo,
    judge_response,
    planner_response,
    route,
)
from tests.test_replay_gate import (  # noqa: E402
    FakeGit,
    learning,
    proposal,
    replay_case,
)


class ParallelMapIndexedTest(unittest.TestCase):
    def test_results_ordered_despite_out_of_order_completion(self) -> None:
        release = threading.Event()

        def fn(item: str) -> str:
            if item == "b":
                self.assertTrue(release.wait(timeout=5), "item c never released item b")
            if item == "c":
                release.set()
            return item.upper()

        slots = parallel_map_indexed(["a", "b", "c"], fn, max_workers=2)
        self.assertEqual([slot.unwrap() for slot in slots], ["A", "B", "C"])

    def test_exceptions_captured_per_slot_and_reraised_on_unwrap(self) -> None:
        def fn(item: int) -> int:
            if item == 1:
                raise ValueError("boom on item 1")
            return item * 10

        slots = parallel_map_indexed([0, 1, 2], fn, max_workers=3)
        self.assertEqual(slots[0].unwrap(), 0)
        self.assertEqual(slots[2].unwrap(), 20)
        with self.assertRaisesRegex(ValueError, "boom on item 1"):
            slots[1].unwrap()

    def test_single_worker_runs_sequentially_on_calling_thread(self) -> None:
        seen: list[tuple[int, int]] = []

        def fn(item: int) -> int:
            seen.append((item, threading.get_ident()))
            return item

        slots = parallel_map_indexed([3, 1, 2], fn, max_workers=1)
        self.assertEqual([slot.unwrap() for slot in slots], [3, 1, 2])
        self.assertEqual([item for item, _ in seen], [3, 1, 2])
        self.assertEqual({ident for _, ident in seen}, {threading.get_ident()})

    def test_warm_first_completes_item_zero_before_fanout(self) -> None:
        lock = threading.Lock()
        starts: dict[int, float] = {}
        warm_done = {"at": 0.0}

        def fn(item: int) -> int:
            with lock:
                starts[item] = time.monotonic()
            if item == 0:
                time.sleep(0.05)
                warm_done["at"] = time.monotonic()
            return item

        slots = parallel_map_indexed([0, 1, 2, 3], fn, max_workers=4, warm_first=True)
        self.assertEqual([slot.unwrap() for slot in slots], [0, 1, 2, 3])
        for item in (1, 2, 3):
            self.assertGreaterEqual(starts[item], warm_done["at"])

    def test_empty_and_single_item_inputs(self) -> None:
        self.assertEqual(parallel_map_indexed([], lambda item: item, max_workers=4), [])
        slots = parallel_map_indexed(["only"], lambda item: item, max_workers=4)
        self.assertEqual([slot.unwrap() for slot in slots], ["only"])


class ThrottledLlmClientTest(unittest.TestCase):
    def test_caps_in_flight_calls(self) -> None:
        lock = threading.Lock()
        state = {"active": 0, "peak": 0}

        def responder(request: dict) -> dict:
            with lock:
                state["active"] += 1
                state["peak"] = max(state["peak"], state["active"])
            time.sleep(0.02)
            with lock:
                state["active"] -= 1
            return {"ok": True}

        throttled = ThrottledLlmClient(FakeLlmClient(responder=responder), max_concurrent=2)
        slots = parallel_map_indexed(
            list(range(6)),
            lambda item: throttled.complete_json({"task": "ping", "input": item}),
            max_workers=6,
            warm_first=False,
        )
        self.assertEqual(len([slot.unwrap() for slot in slots]), 6)
        self.assertLessEqual(state["peak"], 2)


class ConcurrencyKnobTest(unittest.TestCase):
    def test_defaults_to_one_without_env(self) -> None:
        with mock.patch.dict(os.environ, {}, clear=True):
            self.assertEqual(llm_max_workers(), 1)
            self.assertEqual(harvest_max_workers(), 1)
            self.assertEqual(llm_max_workers(RunConfig()), 1)

    def test_env_knobs(self) -> None:
        env = {
            "FEEDBACK_LOOP_LLM_CONCURRENCY": "4",
            "FEEDBACK_LOOP_HARVEST_CONCURRENCY": "3",
        }
        with mock.patch.dict(os.environ, env, clear=True):
            self.assertEqual(llm_max_workers(RunConfig()), 4)
            self.assertEqual(harvest_max_workers(RunConfig()), 3)

    def test_cfg_extra_overrides_env(self) -> None:
        with mock.patch.dict(os.environ, {"FEEDBACK_LOOP_LLM_CONCURRENCY": "4"}, clear=True):
            cfg = RunConfig(extra={"llm_concurrency": 2})
            self.assertEqual(llm_max_workers(cfg), 2)

    def test_invalid_values_fall_back_to_one(self) -> None:
        with mock.patch.dict(os.environ, {"FEEDBACK_LOOP_LLM_CONCURRENCY": "lots"}, clear=True):
            self.assertEqual(llm_max_workers(RunConfig()), 1)
        self.assertEqual(llm_max_workers(RunConfig(extra={"llm_concurrency": 0})), 1)


def classify_responder(request: dict) -> dict | Exception:
    """Request-keyed classifier fake: the batch containing sig-09 always fails transport."""
    ids = [item["signal_id"] for item in request["input"]["signals"]]
    if "sig-09" in ids:
        raise LlmClientError("transport boom")
    return {"classifications": [classification(signal_id) for signal_id in ids]}


class ClassifyParityTest(unittest.TestCase):
    """Sequential vs concurrent classify must produce identical stage results."""

    def run_classify(self, concurrency: int):
        # 18 single-signal PRs -> 3 batches (MAX_CLASSIFY_PRS_PER_CALL=8): 8 + 8 + 2.
        signals = [
            feedback_signal(f"sig-{number:02d}", pr_number=number) for number in range(1, 19)
        ]
        facts = {}
        for number in range(1, 19):
            facts.update(pr_facts(number))
        cfg = RunConfig(extra={"llm_concurrency": concurrency})
        client = FakeLlmClient(responder=classify_responder)
        return classify_signals(cfg, client, signals, facts)

    def test_concurrent_matches_sequential(self) -> None:
        sequential = self.run_classify(1)
        concurrent = self.run_classify(4)

        def fingerprint(result):
            return [
                (signal.source_id, signal.primary_class, signal.manual_triage, signal.rationale)
                for signal in result.signals
            ]

        self.assertEqual(fingerprint(concurrent), fingerprint(sequential))
        self.assertEqual(concurrent.batch_count, sequential.batch_count)
        self.assertEqual(concurrent.failed_batches, sequential.failed_batches)
        self.assertEqual(concurrent.failed_batches, 1)
        self.assertEqual(
            concurrent.unclassified_signal_ids, sequential.unclassified_signal_ids
        )
        self.assertEqual(concurrent.llm_calls, sequential.llm_calls)
        self.assertEqual(concurrent.errors, sequential.errors)


DOCS_TARGET = "docs/docs/automation/feedback-loop.md"


def evaluator_responder(request: dict) -> dict:
    task = request["task"]
    if task == "extract_learnings":
        first = extractor_response(
            routes=[
                route(
                    "docs",
                    summary="Document the feedback-loop route rationale for future agents.",
                    target=DOCS_TARGET,
                )
            ]
        )["learnings"][0]
        second = dict(first)
        second["learning_id"] = "learn-2"
        return {"learnings": [first, second]}
    if task == "plan_route_patch":
        return planner_response(
            "docs",
            DOCS_TARGET,
            content="## Feedback loop\n\nDocument route handoff criteria.\n",
        )
    if task == "judge_proposals":
        return judge_response([request["input"]["proposal"]["proposal_id"]])
    raise AssertionError(f"unexpected task: {task}")


class EvaluatorParityTest(unittest.TestCase):
    """Sequential vs concurrent route evaluation must produce identical artifacts."""

    def run_evaluator(self, concurrency: int):
        item = cluster()
        cfg = RunConfig(
            extra={
                "llm_client": FakeLlmClient(responder=evaluator_responder),
                "repo_reality": fake_repo(),
                "replay_cases": (),
                "llm_concurrency": concurrency,
            }
        )
        return evaluate_llm_learnings(
            cfg,
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

    def test_concurrent_matches_sequential(self) -> None:
        sequential = self.run_evaluator(1)
        concurrent = self.run_evaluator(4)

        def fingerprint(result):
            return [
                (item.learning_id, item.route_id, item.destination, item.eval_state)
                for item in result.proposals
            ]

        self.assertEqual(len(sequential.proposals), 2)
        self.assertEqual(fingerprint(concurrent), fingerprint(sequential))
        self.assertEqual(
            [record.proposal_id for record in concurrent.eval_records],
            [record.proposal_id for record in sequential.eval_records],
        )
        for key in ("planner_calls", "judge_calls", "repair_calls", "replay_calls"):
            self.assertEqual(concurrent.summary[key], sequential.summary[key], key)
        self.assertEqual(concurrent.errors, sequential.errors)


def replay_responder(request: dict) -> dict:
    case = request["input"]["case"]
    return {
        "findings": [
            {
                "case_id": case["case_id"],
                "summary": (
                    "Retry path drops the original status word before the command retries"
                ),
                "source_url": case["anchor_url"],
            }
        ]
    }


class ReplayGateParityTest(unittest.TestCase):
    """Sequential vs concurrent replay-case runs must produce identical gate results."""

    def run_gate(self, max_workers: int):
        cases = [replay_case(f"case-{index}") for index in (1, 2, 3)]
        return run_replay_gate(
            proposal(),
            learning(),
            client=FakeLlmClient(responder=replay_responder),
            git=FakeGit(commits={"aaaa111", "bbbb222"}),
            cases=cases,
            max_workers=max_workers,
        )

    def test_concurrent_matches_sequential(self) -> None:
        sequential = self.run_gate(1)
        concurrent = self.run_gate(3)
        self.assertEqual(sequential.status, "passed")
        self.assertEqual(concurrent, sequential)
        self.assertEqual(concurrent.llm_calls, 3)


if __name__ == "__main__":
    unittest.main()

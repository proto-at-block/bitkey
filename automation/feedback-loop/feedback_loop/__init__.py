"""AI feedback loop pipeline (substrate-agnostic).

Turns merged-PR review feedback into durable, evidence-backed guidance proposals.
Stateless + event-driven: GitHub is the source of record for raw evidence, Linear cluster issues are
the durable memory, and in-repo artifacts stay bounded and reviewable (see docs/docs/automation/).

Origin: BKW-80. Scaffold — pipeline stages are stubbed; see feedback_loop/pipeline/.
"""

__version__ = "0.0.0"

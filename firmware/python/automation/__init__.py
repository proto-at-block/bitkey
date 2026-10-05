import sys
from pathlib import Path


FIRMWARE_DIR = str(Path(__file__).resolve().parents[2])
sys.path.insert(0, FIRMWARE_DIR)

import tasks  # noqa: E402

import struct
from dataclasses import dataclass
from typing import List

# Must match bitlog_event_t in lib/bitlog.
_BITLOG_EVENT_FMT = '<HHB3s3s'


@dataclass
class BitlogEvent:
    timestamp_delta: int
    event: int
    status: int
    pc: int
    lr: int

    def __repr__(self):
        return (f"bitlog:\n"
                f"  delta  : {self.timestamp_delta}\n"
                f"  event  : 0x{self.event:02x}\n"
                f"  status : 0x{self.status:02x}\n"
                f"  pc     : 0x{self.pc:06x}\n"
                f"  lr     : 0x{self.lr:06x}")


def parse(serialized: bytes) -> BitlogEvent:
    """Parse a single bitlog event."""
    deserialized = list(struct.unpack(_BITLOG_EVENT_FMT, serialized))
    deserialized[3] = int.from_bytes(deserialized[3], byteorder='little')  # pc
    deserialized[4] = int.from_bytes(deserialized[4], byteorder='little')  # lr
    return BitlogEvent(*deserialized)


def parse_events(serialized_events: bytes) -> List[BitlogEvent]:
    """Parse many bitlog events."""
    size = struct.calcsize(_BITLOG_EVENT_FMT)
    num_events = len(serialized_events) // size

    events = []
    for i in range(num_events):
        events.append(parse(serialized_events[i * size:(i + 1) * size]))

    return events

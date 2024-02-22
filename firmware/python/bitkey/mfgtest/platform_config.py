from dataclasses import dataclass
from enum import IntEnum


class Port(IntEnum):
    A = 0
    B = 1
    C = 2
    D = 3

    @staticmethod
    def from_str(s):
        s = s.lower()
        val = ord(s) - 97
        return Port(val)


@dataclass
class Gpio:
    port: Port
    pin: int


@dataclass
class LedConfig:
    r: Gpio
    g: Gpio
    b: Gpio
    w: Gpio


@dataclass
class PlatformConfig:
    led: LedConfig


class SerialNumber:
    def __init__(self, serial: str):
        self.serial = serial

    def valid(self):
        return len(self.serial) == 16 or self.unprogrammed()

    def unprogrammed(self):
        return self.serial == ""

    def __str__(self):
        return self.serial


class BoardId:
    def __init__(self, board_id: str):
        self.board_id = board_id

    def valid(self):
        return len(self.board_id) == 2

    def __str__(self):
        return self.board_id


class ChipId:
    def __init__(self, chip_id: str):
        self.chip_id = chip_id

    def valid(self):
        return len(self.chip_id) == 16

    def __str__(self):
        return self.chip_id


class DevInfo:
    def __init__(self, devinfo: str):
        self.raw_devinfo = devinfo


@dataclass
class ChargerInfo:
    status: bool
    mode: str
    registers: str


@dataclass
class FuelStatus:
    repsoc: int
    vcell: int


@dataclass
class DeviceIdentifiers:
    mlb_serial: SerialNumber
    assy_serial: SerialNumber
    board_id: BoardId
    chip_id: ChipId
    devinfo: DevInfo

    def __str__(self):
        return """
identifiers:
  mlb_serial: %s
  assy_serial: %s
  board_id: %s
  chip_id: %s
  devinfo: %s""" % (self.mlb_serial,
                    self.assy_serial,
                    self.board_id,
                    self.chip_id,
                    self.devinfo.raw_devinfo)


# TODO Shouldn't have to duplicate this here and in platform.c, but config is so minimal
# currently we can defer this.
W1A_PROTO0 = "w1a-proto0"
W1A_PROTO0_CONFIG = PlatformConfig(
    led=LedConfig(
        r=Gpio(Port.B, 4),
        g=Gpio(Port.C, 1),
        b=Gpio(Port.C, 2),
        w=Gpio(Port.A, 9),  # TODO undo
    ))

W1A_EVT = "w1a-evt"
W1A_EVT_CONFIG = PlatformConfig(
    led=LedConfig(
        r=Gpio(Port.B, 4),
        g=Gpio(Port.C, 1),
        b=Gpio(Port.C, 2),
        w=Gpio(Port.A, 9),
    ))

CONFIG_MAP = {
    W1A_PROTO0: W1A_PROTO0_CONFIG,
    W1A_EVT: W1A_EVT_CONFIG,
}

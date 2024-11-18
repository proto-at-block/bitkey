package build.wallet.firmware

import build.wallet.queueprocessor.Queue

interface FirmwareCoredumpEventQueue : Queue<FirmwareCoredump>

package build.wallet.firmware

import build.wallet.queueprocessor.Queue

interface FirmwareTelemetryEventQueue : Queue<FirmwareTelemetryEvent>

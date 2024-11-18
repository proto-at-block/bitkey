package build.wallet.firmware

import build.wallet.queueprocessor.Processor

interface FirmwareTelemetryEventProcessor : Processor<FirmwareTelemetryEvent>

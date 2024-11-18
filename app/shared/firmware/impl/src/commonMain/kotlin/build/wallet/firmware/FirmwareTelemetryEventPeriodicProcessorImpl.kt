package build.wallet.firmware

import build.wallet.queueprocessor.PeriodicProcessor
import build.wallet.queueprocessor.PeriodicProcessorImpl
import kotlin.time.Duration.Companion.minutes

class FirmwareTelemetryEventPeriodicProcessorImpl(
  queue: FirmwareTelemetryEventQueue,
  processor: FirmwareTelemetryEventProcessor,
) : FirmwareTelemetryEventPeriodicProcessor,
  PeriodicProcessor by PeriodicProcessorImpl(
    queue = queue,
    processor = processor,
    retryFrequency = 1.minutes,
    retryBatchSize = 10
  )

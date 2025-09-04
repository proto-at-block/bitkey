package build.wallet.firmware

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.app.AppSessionManager
import build.wallet.queueprocessor.PeriodicProcessor
import build.wallet.queueprocessor.PeriodicProcessorImpl
import kotlin.time.Duration.Companion.minutes

@BitkeyInject(AppScope::class, boundTypes = [FirmwareTelemetryEventPeriodicProcessor::class])
class FirmwareTelemetryEventPeriodicProcessorImpl(
  queue: FirmwareTelemetryEventQueue,
  processor: FirmwareTelemetryEventProcessor,
  appSessionManager: AppSessionManager,
) : FirmwareTelemetryEventPeriodicProcessor,
  PeriodicProcessor by PeriodicProcessorImpl(
    queue = queue,
    processor = processor,
    retryFrequency = 1.minutes,
    retryBatchSize = 10,
    appSessionManager = appSessionManager
  )

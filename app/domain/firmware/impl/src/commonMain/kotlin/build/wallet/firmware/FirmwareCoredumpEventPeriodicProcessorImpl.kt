package build.wallet.firmware

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.queueprocessor.PeriodicProcessor
import build.wallet.queueprocessor.PeriodicProcessorImpl
import kotlin.time.Duration.Companion.minutes

@BitkeyInject(AppScope::class, boundTypes = [FirmwareCoredumpEventPeriodicProcessor::class])
class FirmwareCoredumpEventPeriodicProcessorImpl(
  queue: FirmwareCoredumpEventQueue,
  processor: FirmwareCoredumpEventProcessor,
) : FirmwareCoredumpEventPeriodicProcessor,
  PeriodicProcessor by PeriodicProcessorImpl(
    queue = queue,
    processor = processor,
    retryFrequency = 1.minutes,
    retryBatchSize = 10
  )

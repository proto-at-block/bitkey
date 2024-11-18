package build.wallet.notifications

import build.wallet.queueprocessor.PeriodicProcessor
import build.wallet.queueprocessor.PeriodicProcessorImpl
import kotlin.time.Duration.Companion.minutes

class RegisterWatchAddressPeriodicProcessorImpl(
  private val queue: RegisterWatchAddressQueue,
  private val processor: RegisterWatchAddressProcessor,
) : RegisterWatchAddressPeriodicProcessor,
  PeriodicProcessor by PeriodicProcessorImpl(
    queue = queue,
    processor = processor,
    retryFrequency = 1.minutes,
    retryBatchSize = 1
  )

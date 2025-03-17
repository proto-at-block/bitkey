package build.wallet.notifications

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.queueprocessor.PeriodicProcessor
import build.wallet.queueprocessor.PeriodicProcessorImpl
import kotlin.time.Duration.Companion.minutes

@BitkeyInject(AppScope::class, boundTypes = [RegisterWatchAddressPeriodicProcessor::class])
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

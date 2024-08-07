package build.wallet.worker

import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_INITIALIZE
import build.wallet.configuration.MobilePayFiatConfigSyncWorker
import build.wallet.f8e.debug.NetworkingDebugConfigRepository
import build.wallet.feature.FeatureFlagSyncWorker
import build.wallet.queueprocessor.PeriodicProcessor

fun interface AppWorkerProvider {
  /**
   * Provides all [AppWorker]s that should be executed on application startup.
   */
  fun allWorkers(): Set<AppWorker>
}

/**
 * Implementation of [AppWorkerProvider] that provides all actual [AppWorker]s that should be
 * executed on application startup.
 */
class AppWorkerProviderImpl(
  private val eventTracker: EventTracker,
  private val periodicEventProcessor: PeriodicProcessor,
  private val periodicFirmwareCoredumpProcessor: PeriodicProcessor,
  private val periodicFirmwareTelemetryProcessor: PeriodicProcessor,
  private val periodicRegisterWatchAddressProcessor: PeriodicProcessor,
  private val networkingDebugConfigRepository: NetworkingDebugConfigRepository,
  private val mobilePayFiatConfigSyncWorker: MobilePayFiatConfigSyncWorker,
  private val featureFlagSyncWorker: FeatureFlagSyncWorker,
) : AppWorkerProvider {
  override fun allWorkers(): Set<AppWorker> {
    return setOf(
      AppWorker { eventTracker.track(action = ACTION_APP_OPEN_INITIALIZE) },
      AppWorker(networkingDebugConfigRepository::launchSync),
      AppWorker(periodicEventProcessor::start),
      AppWorker(periodicFirmwareCoredumpProcessor::start),
      AppWorker(periodicFirmwareTelemetryProcessor::start),
      AppWorker(periodicRegisterWatchAddressProcessor::start),
      mobilePayFiatConfigSyncWorker,
      featureFlagSyncWorker
    )
  }
}

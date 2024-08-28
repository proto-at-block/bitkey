package build.wallet.worker

import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_INITIALIZE
import build.wallet.availability.AppFunctionalitySyncWorker
import build.wallet.bitcoin.address.BitcoinRegisterWatchAddressWorker
import build.wallet.bitcoin.transactions.TransactionSyncWorker
import build.wallet.configuration.MobilePayFiatConfigSyncWorker
import build.wallet.f8e.debug.NetworkingDebugConfigRepository
import build.wallet.feature.FeatureFlagSyncWorker
import build.wallet.fwup.FirmwareDataSyncWorker
import build.wallet.limit.MobilePayBalanceSyncWorker
import build.wallet.money.currency.FiatCurrenciesSyncWorker
import build.wallet.money.exchange.ExchangeRateSyncWorker
import build.wallet.notifications.NotificationTouchpointSyncWorker
import build.wallet.queueprocessor.PeriodicProcessor
import build.wallet.recovery.socrec.EndorseTrustedContactsWorker
import build.wallet.recovery.socrec.SocRecSyncRelationshipsWorker

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
  private val exchangeRateSyncWorker: ExchangeRateSyncWorker,
  private val periodicEventProcessor: PeriodicProcessor,
  private val periodicFirmwareCoredumpProcessor: PeriodicProcessor,
  private val periodicFirmwareTelemetryProcessor: PeriodicProcessor,
  private val periodicRegisterWatchAddressProcessor: PeriodicProcessor,
  private val networkingDebugConfigRepository: NetworkingDebugConfigRepository,
  private val mobilePayFiatConfigSyncWorker: MobilePayFiatConfigSyncWorker,
  private val featureFlagSyncWorker: FeatureFlagSyncWorker,
  private val firmwareDataSyncWorker: FirmwareDataSyncWorker,
  private val notificationTouchpointSyncWorker: NotificationTouchpointSyncWorker,
  private val bitcoinAddressRegisterWatchAddressWorker: BitcoinRegisterWatchAddressWorker,
  private val endorseTrustedContactsWorker: EndorseTrustedContactsWorker,
  private val transactionsSyncWorker: TransactionSyncWorker,
  private val fiatCurrenciesSyncWorker: FiatCurrenciesSyncWorker,
  private val socRecSyncRelationshipsWorker: SocRecSyncRelationshipsWorker,
  private val mobilePayBalanceSyncWorker: MobilePayBalanceSyncWorker,
  private val appFunctionalitySyncWorker: AppFunctionalitySyncWorker,
) : AppWorkerProvider {
  override fun allWorkers(): Set<AppWorker> {
    return setOf(
      AppWorker { eventTracker.track(action = ACTION_APP_OPEN_INITIALIZE) },
      exchangeRateSyncWorker,
      AppWorker(networkingDebugConfigRepository::launchSync),
      AppWorker(periodicEventProcessor::start),
      AppWorker(periodicFirmwareCoredumpProcessor::start),
      AppWorker(periodicFirmwareTelemetryProcessor::start),
      AppWorker(periodicRegisterWatchAddressProcessor::start),
      mobilePayFiatConfigSyncWorker,
      featureFlagSyncWorker,
      firmwareDataSyncWorker,
      notificationTouchpointSyncWorker,
      endorseTrustedContactsWorker,
      bitcoinAddressRegisterWatchAddressWorker,
      transactionsSyncWorker,
      fiatCurrenciesSyncWorker,
      mobilePayBalanceSyncWorker,
      fiatCurrenciesSyncWorker,
      socRecSyncRelationshipsWorker,
      appFunctionalitySyncWorker
    )
  }
}

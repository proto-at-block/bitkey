package build.wallet.worker

import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_INITIALIZE
import build.wallet.availability.AppFunctionalitySyncWorker
import build.wallet.bitcoin.address.BitcoinRegisterWatchAddressWorker
import build.wallet.bitcoin.transactions.TransactionSyncWorker
import build.wallet.configuration.MobilePayFiatConfigSyncWorker
import build.wallet.f8e.debug.NetworkingDebugService
import build.wallet.feature.FeatureFlagSyncWorker
import build.wallet.fwup.FirmwareDataSyncWorker
import build.wallet.inheritance.InheritanceClaimsSyncWorker
import build.wallet.inheritance.InheritanceMaterialSyncWorker
import build.wallet.limit.MobilePayBalanceSyncWorker
import build.wallet.money.currency.FiatCurrenciesSyncWorker
import build.wallet.money.exchange.ExchangeRateSyncWorker
import build.wallet.notifications.NotificationTouchpointSyncWorker
import build.wallet.queueprocessor.PeriodicProcessor
import build.wallet.relationships.EndorseTrustedContactsWorker
import build.wallet.relationships.SyncRelationshipsWorker

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
  private val networkingDebugService: NetworkingDebugService,
  private val mobilePayFiatConfigSyncWorker: MobilePayFiatConfigSyncWorker,
  private val featureFlagSyncWorker: FeatureFlagSyncWorker,
  private val firmwareDataSyncWorker: FirmwareDataSyncWorker,
  private val notificationTouchpointSyncWorker: NotificationTouchpointSyncWorker,
  private val bitcoinAddressRegisterWatchAddressWorker: BitcoinRegisterWatchAddressWorker,
  private val endorseTrustedContactsWorker: EndorseTrustedContactsWorker,
  private val transactionsSyncWorker: TransactionSyncWorker,
  private val fiatCurrenciesSyncWorker: FiatCurrenciesSyncWorker,
  private val syncRelationshipsWorker: SyncRelationshipsWorker,
  private val mobilePayBalanceSyncWorker: MobilePayBalanceSyncWorker,
  private val appFunctionalitySyncWorker: AppFunctionalitySyncWorker,
  private val inheritanceMaterialSyncWorker: InheritanceMaterialSyncWorker,
  private val inheritanceClaimsSyncWorker: InheritanceClaimsSyncWorker,
) : AppWorkerProvider {
  override fun allWorkers(): Set<AppWorker> {
    return setOf(
      AppWorker { eventTracker.track(action = ACTION_APP_OPEN_INITIALIZE) },
      exchangeRateSyncWorker,
      AppWorker(networkingDebugService::launchSync),
      AppWorker(periodicEventProcessor::start),
      AppWorker(periodicFirmwareCoredumpProcessor::start),
      AppWorker(periodicFirmwareTelemetryProcessor::start),
      AppWorker(periodicRegisterWatchAddressProcessor::start),
      mobilePayFiatConfigSyncWorker,
      featureFlagSyncWorker,
      firmwareDataSyncWorker,
      inheritanceMaterialSyncWorker,
      notificationTouchpointSyncWorker,
      endorseTrustedContactsWorker,
      bitcoinAddressRegisterWatchAddressWorker,
      transactionsSyncWorker,
      fiatCurrenciesSyncWorker,
      mobilePayBalanceSyncWorker,
      fiatCurrenciesSyncWorker,
      syncRelationshipsWorker,
      appFunctionalitySyncWorker,
      inheritanceClaimsSyncWorker
    )
  }
}

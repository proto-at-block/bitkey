package build.wallet.analytics.events

import build.wallet.account.AccountRepository
import build.wallet.account.AccountStatus
import build.wallet.account.analytics.AppInstallation
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.events.screen.EventTrackerCountInfo
import build.wallet.analytics.events.screen.EventTrackerFingerprintScanStatsInfo
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.v1.Action
import build.wallet.analytics.v1.Action.*
import build.wallet.analytics.v1.Event
import build.wallet.analytics.v1.FingerprintScanStats
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.keybox.config.TemplateFullAccountConfigDao
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.settings.LocaleCountryCodeProvider
import build.wallet.platform.settings.LocaleCurrencyCodeProvider
import build.wallet.queueprocessor.Processor
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.getOrElse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class EventTrackerImpl(
  private val appCoroutineScope: CoroutineScope,
  private val clock: Clock,
  private val appDeviceIdDao: AppDeviceIdDao,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val accountRepository: AccountRepository,
  private val templateFullAccountConfigDao: TemplateFullAccountConfigDao,
  private val countryCodeProvider: LocaleCountryCodeProvider,
  private val eventProcessor: Processor<QueueAnalyticsEvent>,
  private val hardwareInfoProvider: HardwareInfoProvider,
  private val appInstallationDao: AppInstallationDao,
  private val platformInfoProvider: PlatformInfoProvider,
  private val appSessionManager: AppSessionManager,
  private val eventStore: EventStore,
  private val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val localeCurrencyCodeProvider: LocaleCurrencyCodeProvider,
  private val analyticsTrackingPreference: AnalyticsTrackingPreference,
) : EventTracker {
  private sealed interface ActionType {
    data class Generic(val action: Action, val screenId: String?) : ActionType

    data class ScreenView(val screenInfo: EventTrackerScreenInfo) : ActionType

    data class Count(val countInfo: EventTrackerCountInfo) : ActionType

    data class FingerprintScanStats(
      val fingerprintScanStatsInfo: EventTrackerFingerprintScanStatsInfo,
    ) : ActionType
  }

  override fun track(
    action: Action,
    context: EventTrackerContext?,
  ) {
    track(
      ActionType.Generic(action, context?.name)
    )
  }

  override fun track(eventTrackerCountInfo: EventTrackerCountInfo) {
    track(ActionType.Count(eventTrackerCountInfo))
  }

  override fun track(eventTrackerScreenInfo: EventTrackerScreenInfo) {
    // Early return if this screen should not be tracked.
    // The reason [EventTrackerScreenInfo] would still be present for the screen is because
    // it also serves as the screen's ID, so it is needed to maintain screen uniqueness,
    // especially for form screens.

    if (!eventTrackerScreenInfo.eventTrackerShouldTrack) {
      return
    }

    track(ActionType.ScreenView(eventTrackerScreenInfo))
  }

  override fun track(eventTrackerFingerprintScanStatsInfo: EventTrackerFingerprintScanStatsInfo) {
    track(ActionType.FingerprintScanStats(eventTrackerFingerprintScanStatsInfo))
  }

  private fun track(actionType: ActionType) {
    val action = when (actionType) {
      is ActionType.Generic -> actionType.action
      is ActionType.ScreenView -> ACTION_APP_SCREEN_IMPRESSION
      is ActionType.Count -> ACTION_APP_COUNT
      is ActionType.FingerprintScanStats -> ACTION_HW_FINGERPRINT_SCAN_STATS
    }

    val screenId = when (actionType) {
      is ActionType.Generic -> actionType.screenId
      is ActionType.ScreenView -> actionType.screenInfo.screenId
      is ActionType.Count -> null
      is ActionType.FingerprintScanStats -> null
    }

    val countId = when (actionType) {
      is ActionType.Generic -> null
      is ActionType.ScreenView -> null
      is ActionType.Count -> actionType.countInfo.counterId
      is ActionType.FingerprintScanStats -> null
    }

    val count = when (actionType) {
      is ActionType.Generic -> 0
      is ActionType.ScreenView -> 0
      is ActionType.Count -> actionType.countInfo.count
      is ActionType.FingerprintScanStats -> 0
    }

    val fingerprintScanStats = when (actionType) {
      is ActionType.Generic -> null
      is ActionType.ScreenView -> null
      is ActionType.Count -> null
      is ActionType.FingerprintScanStats -> actionType.fingerprintScanStatsInfo.stats
    }

    createAndLogEvent(action, screenId, countId, count, fingerprintScanStats)
  }

  private fun createAndLogEvent(
    action: Action,
    screenId: String?,
    countId: String?,
    count: Int,
    fingerprintScanStats: FingerprintScanStats?,
  ) {
    appCoroutineScope.launch {
      val account = getCurrentAccount()
      val accountId = account?.accountId?.serverId.orEmpty()
      val activeSpendingKeysetId = when (account) {
        is FullAccount -> account.keybox.activeSpendingKeyset.localId
        else -> ""
      }

      val f8eEnvironment =
        account?.config?.f8eEnvironment ?: run {
          // fallback on template config
          templateFullAccountConfigDao.config().first().get()?.f8eEnvironment
            // fallback on Production
            ?: Production
        }

      val platformInfo = platformInfoProvider.getPlatformInfo()

      // In case of iOS we rely on a generated appDeviceId stored in the keychain.
      // Due to lack of availability of a similar solution on Android, we use ANDROID_ID instead.
      var appDeviceId = platformInfo.device_id
      if (deviceInfoProvider.getDeviceInfo().devicePlatform == DevicePlatform.IOS) {
        appDeviceId = appDeviceIdDao.getOrCreateAppDeviceIdIfNotExists().getOr("UNKNOWN")
      }
      val appInstallationId =
        appInstallationDao.getOrCreateAppInstallation()
          .getOrElse {
            log(LogLevel.Error, throwable = it.cause) { "Failed to get App Installation" }
            AppInstallation(localId = "", hardwareSerialNumber = null)
          }.localId

      val bitcoinDisplayUnit = bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value
      val fiatCurrencyPreference = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value

      val event =
        Event(
          event_time = clock.now().toString(),
          session_id = appSessionManager.getSessionId(),
          account_id = accountId,
          action = action,
          country = countryCodeProvider.countryCode(),
          locale_currency = localeCurrencyCodeProvider.localeCurrencyCode() ?: "",
          platform_info = platformInfo,
          hw_info = hardwareInfoProvider.getHardwareInfo(),
          screen_id = screenId ?: "",
          counter_id = countId ?: "",
          counter_count = count,
          app_device_id = appDeviceId,
          keyset_id = activeSpendingKeysetId,
          app_installation_id = appInstallationId,
          fiat_currency_preference = fiatCurrencyPreference.textCode.code,
          bitcoin_display_preference = bitcoinDisplayUnit.name,
          fingerprint_scan_stats = fingerprintScanStats
        )

      // Always add the event to the local store
      eventStore.add(event)

      // But only actually track the event if the preference is enabled
      if (analyticsTrackingPreference.get()) {
        eventProcessor.process(QueueAnalyticsEvent(f8eEnvironment, event))
          .logFailure { "Failed to append event to queue: $event" }
      }
    }
  }

  private suspend fun getCurrentAccount(): Account? {
    return accountRepository.accountStatus().first().get()?.let {
      when (it) {
        is AccountStatus.ActiveAccount -> it.account
        is AccountStatus.LiteAccountUpgradingToFullAccount -> it.account
        AccountStatus.NoAccount -> null
        is AccountStatus.OnboardingAccount -> it.account
      }
    }
  }
}

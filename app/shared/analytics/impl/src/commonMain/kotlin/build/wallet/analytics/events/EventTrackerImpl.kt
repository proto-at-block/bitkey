package build.wallet.analytics.events

import build.wallet.account.analytics.AppInstallation
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.v1.Action
import build.wallet.analytics.v1.Action.ACTION_APP_SCREEN_IMPRESSION
import build.wallet.analytics.v1.Event
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.config.TemplateKeyboxConfigDao
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
  private val keyboxDao: KeyboxDao,
  private val templateKeyboxConfigDao: TemplateKeyboxConfigDao,
  private val countryCodeProvider: LocaleCountryCodeProvider,
  private val eventProcessor: Processor<QueueAnalyticsEvent>,
  private val hardwareInfoProvider: HardwareInfoProvider,
  private val appInstallationDao: AppInstallationDao,
  private val platformInfoProvider: PlatformInfoProvider,
  private val sessionIdProvider: SessionIdProvider,
  private val eventStore: EventStore,
  private val analyticsTrackingEnabledFeatureFlag: AnalyticsTrackingEnabledFeatureFlag,
  private val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val localeCurrencyCodeProvider: LocaleCurrencyCodeProvider,
) : EventTracker {
  override fun track(action: Action) {
    track(
      action = action,
      screenId = null
    )
  }

  override fun track(eventTrackerScreenInfo: EventTrackerScreenInfo) {
    // Early return if this screen should not be tracked.
    // The reason [EventTrackerScreenInfo] would still be present for the screen is because
    // it also serves as the screen's ID, so it is needed to maintain screen uniqueness,
    // especially for form screens.
    if (!eventTrackerScreenInfo.eventTrackerShouldTrack) {
      return
    }

    track(
      action = ACTION_APP_SCREEN_IMPRESSION,
      screenId =
        when (val context = eventTrackerScreenInfo.eventTrackerScreenIdContext) {
          null ->
            eventTrackerScreenInfo.eventTrackerScreenId.name

          else ->
            "${eventTrackerScreenInfo.eventTrackerScreenId.name}_${context.name}"
        }
    )
  }

  private fun track(
    action: Action,
    screenId: String?,
  ) {
    appCoroutineScope.launch {
      // TODO(W-3387): use account ID from in-progress recovery when there is
      //               no active or onboarding keybox present.
      val keybox = keyboxDao.getActiveOrOnboardingKeybox()
      val accountId = keybox.get()?.fullAccountId?.serverId.orEmpty()
      val keysetId = keybox.get()?.activeSpendingKeyset?.localId.orEmpty()
      val platformInfo = platformInfoProvider.getPlatformInfo()
      val f8eEnvironment = getKeyboxConfig()?.f8eEnvironment ?: Production // assume prod when no keybox is available

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
      val fiatCurrencyPreference =
        fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value
          ?: fiatCurrencyPreferenceRepository.defaultFiatCurrency.value

      val event =
        Event(
          event_time = clock.now().toString(),
          session_id = sessionIdProvider.getSessionId(),
          account_id = accountId,
          action = action,
          country = countryCodeProvider.countryCode(),
          locale_currency = localeCurrencyCodeProvider.localeCurrencyCode() ?: "",
          platform_info = platformInfo,
          hw_info = hardwareInfoProvider.getHardwareInfo(),
          screen_id = screenId ?: "",
          app_device_id = appDeviceId,
          keyset_id = keysetId,
          app_installation_id = appInstallationId,
          fiat_currency_preference = fiatCurrencyPreference.textCode.code,
          bitcoin_display_preference = bitcoinDisplayUnit.name
        )

      // Always add the event to the local store
      eventStore.add(event)

      // But only actually track the event if the feature flag is on
      if (analyticsTrackingEnabledFeatureFlag.flagValue().value.value) {
        eventProcessor.process(QueueAnalyticsEvent(f8eEnvironment, event))
          .logFailure { "Failed to append event to queue: $event" }
      }
    }
  }

  private suspend fun getKeyboxConfig(): KeyboxConfig? {
    return when (val keybox = keyboxDao.getActiveOrOnboardingKeybox().getOr(null)) {
      // when null, we try to grab the config from the template keybox
      null -> templateKeyboxConfigDao.config().first().getOr(null)
      else -> keybox.config
    }
  }
}

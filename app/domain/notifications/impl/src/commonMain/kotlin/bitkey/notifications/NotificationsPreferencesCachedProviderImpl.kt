package bitkey.notifications

import bitkey.account.AccountConfigService
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.notifications.NotificationTouchpointF8eClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.logging.logFailure
import build.wallet.store.KeyValueStoreFactory
import com.github.michaelbull.result.*
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.SuspendSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal const val NOTIFICATIONS_PREFERENCES_CACHE = "NOTIFICATIONS_PREFERENCES_CACHE"
internal const val PREFS_INITIALIZED = "prefs-initialized"
internal const val MONEY_MOVEMENT_CHANNELS = "moneyMovement-channels"
internal const val PRODUCT_MARKETING_CHANNELS = "productMarketing-channels"
internal const val ACCOUNT_SECURITY_CHANNELS = "accountSecurity-channels"

@BitkeyInject(AppScope::class)
@OptIn(ExperimentalSettingsApi::class)
class NotificationsPreferencesCachedProviderImpl(
  private val notificationTouchpointF8eClient: NotificationTouchpointF8eClient,
  private val keyValueStoreFactory: KeyValueStoreFactory,
  private val accountConfigService: AccountConfigService,
  private val accountService: AccountService,
) : NotificationsPreferencesCachedProvider {
  private val preferencesFlow = MutableStateFlow<Result<NotificationPreferences, Error>?>(null)

  override suspend fun initialize() {
    val prefsCache = keyValueStoreFactory.getOrCreate(NOTIFICATIONS_PREFERENCES_CACHE)
    val loadedPrefs = loadCachedPreferences(prefsCache)
    val account = accountService.getAccount<FullAccount>().get()

    if (account == null) {
      preferencesFlow.update { Err(Error("No account available.")) }
      return
    }

    if (loadedPrefs != null) {
      preferencesFlow.update { Ok(loadedPrefs) }

      notificationTouchpointF8eClient.getNotificationsPreferences(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId
      ).onSuccess { serverPrefs ->
        // Emit again, but only if server values differ
        if (serverPrefs != loadedPrefs) {
          preferencesFlow.update { Ok(serverPrefs) }

          cacheNotificationPreferences(
            prefsCache = prefsCache,
            notificationPreferences = serverPrefs
          )
        }
      }.logFailure {
        // We assume the local values are sufficient and do not emit an error
        // [NotificationTouchpointF8eClientImpl] will log the error detail for us
        "Failed to load prefs. Using cached values."
      }
    } else {
      notificationTouchpointF8eClient.getNotificationsPreferences(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId
      ).onSuccess { serverPrefs ->
        preferencesFlow.update { Ok(serverPrefs) }
        cacheNotificationPreferences(
          prefsCache = prefsCache,
          notificationPreferences = serverPrefs
        )
      }.onFailure {
        // No saved values. Emit error.
        preferencesFlow.update { it }
      }
    }
  }

  override fun getNotificationsPreferences(): Flow<Result<NotificationPreferences, Error>?> {
    return preferencesFlow
  }

  override suspend fun updateNotificationsPreferences(
    accountId: AccountId,
    preferences: NotificationPreferences,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, NetworkingError> {
    val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
    return notificationTouchpointF8eClient.updateNotificationsPreferences(
      f8eEnvironment = f8eEnvironment,
      accountId = accountId,
      preferences = preferences,
      hwFactorProofOfPossession = hwFactorProofOfPossession
    ).onSuccess {
      val prefsCache = keyValueStoreFactory.getOrCreate(NOTIFICATIONS_PREFERENCES_CACHE)
      cacheNotificationPreferences(
        prefsCache = prefsCache,
        notificationPreferences = preferences
      )
      preferencesFlow.update { Ok(preferences) }
      Ok(Unit)
    }.onFailure {
      // On failure, we do not update local values
      Err(it)
    }
  }

  /**
   * Helper functions available for tests
   */
  internal companion object {
    internal suspend fun loadCachedPreferences(
      prefsCache: SuspendSettings,
    ): NotificationPreferences? {
      val existingValues = prefsCache.getBoolean(PREFS_INITIALIZED, false)
      return if (existingValues) {
        NotificationPreferences(
          moneyMovement = notificationChannels(prefsCache, MONEY_MOVEMENT_CHANNELS),
          productMarketing = notificationChannels(prefsCache, PRODUCT_MARKETING_CHANNELS),
          accountSecurity = notificationChannels(prefsCache, ACCOUNT_SECURITY_CHANNELS)
        )
      } else {
        null
      }
    }

    internal suspend fun cacheNotificationPreferences(
      prefsCache: SuspendSettings,
      notificationPreferences: NotificationPreferences,
    ) {
      prefsCache.putString(
        MONEY_MOVEMENT_CHANNELS,
        joinChannels(notificationPreferences.moneyMovement)
      )
      prefsCache.putString(
        PRODUCT_MARKETING_CHANNELS,
        joinChannels(notificationPreferences.productMarketing)
      )
      prefsCache.putString(
        ACCOUNT_SECURITY_CHANNELS,
        joinChannels(notificationPreferences.accountSecurity)
      )
      prefsCache.putBoolean(PREFS_INITIALIZED, true)
    }

    private fun joinChannels(channels: Set<NotificationChannel>): String =
      channels
        .joinToString(separator = ",") { it.name }

    private suspend fun notificationChannels(
      prefsCache: SuspendSettings,
      key: String,
    ) = prefsCache.getString(key, "").split(",")
      .filter { it.isNotEmpty() }
      .mapNotNull { NotificationChannel.valueOfOrNull(it) }
      .toSet()
  }
}

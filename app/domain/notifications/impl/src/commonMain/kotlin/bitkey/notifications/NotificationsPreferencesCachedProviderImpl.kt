package bitkey.notifications

import bitkey.account.AccountConfigService
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
import kotlinx.coroutines.flow.flow

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
) : NotificationsPreferencesCachedProvider {
  override fun getNotificationsPreferences(
    accountId: AccountId,
  ): Flow<Result<NotificationPreferences, NetworkingError>> =
    flow {
      val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
      val prefsCache = keyValueStoreFactory.getOrCreate(NOTIFICATIONS_PREFERENCES_CACHE)
      val loadedPrefs = loadCachedPreferences(prefsCache)
      if (loadedPrefs != null) {
        // Emit saved values right away
        emit(Ok(loadedPrefs))

        notificationTouchpointF8eClient.getNotificationsPreferences(
          f8eEnvironment = f8eEnvironment,
          accountId = accountId
        ).onSuccess { serverPrefs ->
          // Emit again, but only if server values differ
          if (serverPrefs != loadedPrefs) {
            emit(Ok(serverPrefs))
            cacheNotificationPreferences(
              prefsCache = prefsCache,
              notificationPreferences = serverPrefs
            )
          }
        }
          .logFailure {
            // We assume the local values are sufficient and do not emit an error
            // [NotificationTouchpointF8eClientImpl] will log the error detail for us
            "Failed to load prefs. Using cached values."
          }
      } else {
        notificationTouchpointF8eClient.getNotificationsPreferences(
          f8eEnvironment = f8eEnvironment,
          accountId = accountId
        ).onSuccess { serverPrefs ->
          emit(Ok(serverPrefs))
          cacheNotificationPreferences(
            prefsCache = prefsCache,
            notificationPreferences = serverPrefs
          )
        }.onFailure {
          // No saved values. Emit error.
          emit(Err(it))
        }
      }
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
      fun joinChannels(channels: Set<NotificationChannel>): String =
        channels
          .joinToString(separator = ",") { it.name }

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

    private suspend fun notificationChannels(
      prefsCache: SuspendSettings,
      key: String,
    ) = prefsCache.getString(key, "").split(",")
      .filter { it.isNotEmpty() }
      .mapNotNull { NotificationChannel.valueOfOrNull(it) }
      .toSet()
  }
}

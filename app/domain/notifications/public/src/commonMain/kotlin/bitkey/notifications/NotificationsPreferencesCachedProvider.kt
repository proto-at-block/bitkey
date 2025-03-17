package bitkey.notifications

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Wraps calls to the server endpoints for notifications preferences data, and stores them in a local
 * key/value cache.
 */
interface NotificationsPreferencesCachedProvider {
  /**
   * Returns the notification preferences for the given account. The value returned is a Flow
   * of Result, because there could be more than one result. Here is the logical flow:
   *
   * If there is a cached value, that is emitted immediately. We then check the server. If the
   * server data is the same as the cached data, we do nothing. If it is *different*, the updated
   * data is cached, and that is emitted. If there's an error on the server call, we log it but
   * ignore it otherwise.
   *
   * If there is no cached data, we call the server. On success, the data is cached and emitted. On
   * server error, an error is emitted.
   *
   * To be clear, the *only* time an error is emitted is if there's no local cache data and the server
   * call fails.
   */
  fun getNotificationsPreferences(
    accountId: AccountId,
  ): Flow<Result<NotificationPreferences, NetworkingError>>

  /**
   * Updates the notification preferences for the given account. The data is sent to the server, and
   * only cached if the server call succeeds. If the call fails, an error is emitted.
   */
  suspend fun updateNotificationsPreferences(
    accountId: AccountId,
    preferences: NotificationPreferences,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, NetworkingError>
}

package bitkey.notifications

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

data class NotificationsPreferencesCachedProviderMock(
  val getNotificationPreferencesResult: Result<NotificationPreferences, NetworkingError> = Ok(
    NotificationPreferences(
      emptySet(), emptySet(), emptySet()
    )
  ),
  val updateNotificationsPreferencesResult: Result<Unit, NetworkingError> = Ok(Unit),
) : NotificationsPreferencesCachedProvider {
  val notificationPreferences = MutableStateFlow(getNotificationPreferencesResult)

  override suspend fun initialize() {
    // no-op in mock
  }

  override fun getNotificationsPreferences(): Flow<Result<NotificationPreferences, Error>> =
    notificationPreferences

  override suspend fun updateNotificationsPreferences(
    accountId: AccountId,
    preferences: NotificationPreferences,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, NetworkingError> = updateNotificationsPreferencesResult
}

package build.wallet.statemachine.notifications

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import build.wallet.notifications.NotificationPreferences
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

  override fun getNotificationsPreferences(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
  ): Flow<Result<NotificationPreferences, NetworkingError>> = notificationPreferences

  override suspend fun updateNotificationsPreferences(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    preferences: NotificationPreferences,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, NetworkingError> = updateNotificationsPreferencesResult
}

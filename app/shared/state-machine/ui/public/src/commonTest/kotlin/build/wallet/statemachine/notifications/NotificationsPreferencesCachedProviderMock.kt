package build.wallet.statemachine.notifications

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import build.wallet.notifications.NotificationPreferences
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class NotificationsPreferencesCachedProviderMock(
  val getNotificationPreferencesResult: Result<NotificationPreferences, NetworkingError> = Ok(
    NotificationPreferences(
      emptySet(), emptySet(), emptySet()
    )
  ),
  val updateNotificationsPreferencesResult: Result<Unit, NetworkingError> = Ok(Unit),
) : NotificationsPreferencesCachedProvider {
  override suspend fun getNotificationsPreferences(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Flow<Result<NotificationPreferences, NetworkingError>> =
    flowOf(getNotificationPreferencesResult)

  override suspend fun updateNotificationsPreferences(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    preferences: NotificationPreferences,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, NetworkingError> = updateNotificationsPreferencesResult
}

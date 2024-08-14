package build.wallet.notifications

import build.wallet.account.AccountRepository
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.f8e.notifications.NotificationTouchpointF8eClient
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

class NotificationTouchpointServiceImpl(
  private val notificationTouchpointF8eClient: NotificationTouchpointF8eClient,
  private val notificationTouchpointDao: NotificationTouchpointDao,
  private val accountRepository: AccountRepository,
) : NotificationTouchpointService, NotificationTouchpointSyncWorker {
  override fun notificationTouchpointData(): Flow<NotificationTouchpointData> {
    return combine(
      notificationTouchpointDao.email(),
      notificationTouchpointDao.phoneNumber()
    ) { email, phoneNumber ->
      NotificationTouchpointData(
        email = email,
        phoneNumber = phoneNumber
      )
    }
  }

  override suspend fun executeWork() {
    accountRepository.accountStatus()
      .collectLatest { result ->
        result.onSuccess {
          if (it is ActiveAccount) {
            val account = it.account
            if (account is FullAccount) {
              syncNotificationTouchpoints(account)
            }
          }
        }
      }
  }

  private suspend fun syncNotificationTouchpoints(account: FullAccount): Result<Unit, Error> {
    return coroutineBinding {
      notificationTouchpointF8eClient.getTouchpoints(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId
      ).onSuccess { touchpoints ->
        notificationTouchpointDao.clear()
        touchpoints.forEach { touchpoint ->
          notificationTouchpointDao.storeTouchpoint(touchpoint)
            .logFailure {
              "Failed to store touchpoint from server in db with ID ${touchpoint.touchpointId}"
            }
        }
      }.onFailure {
        /**
         * Don't do anything if we fail. [NotificationTouchpointF8eClient] will log the failure,
         * and we will try to pull the touchpoints again the next time the app opens.
         */
      }
    }
  }
}

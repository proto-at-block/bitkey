package build.wallet.notifications

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.VerifyTouchpointClientErrorCode
import build.wallet.f8e.notifications.NotificationTouchpointF8eClient
import build.wallet.f8e.recovery.RecoveryNotificationVerificationF8eClient
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

class NotificationTouchpointServiceImpl(
  private val notificationTouchpointF8eClient: NotificationTouchpointF8eClient,
  private val notificationTouchpointDao: NotificationTouchpointDao,
  private val recoveryNotificationVerificationF8eClient: RecoveryNotificationVerificationF8eClient,
  private val accountService: AccountService,
) : NotificationTouchpointService, NotificationTouchpointSyncWorker {
  override suspend fun executeWork() {
    accountService.accountStatus()
      .collectLatest { result ->
        result.onSuccess {
          if (it is ActiveAccount) {
            val account = it.account
            if (account is FullAccount || account is SoftwareAccount) {
              syncNotificationTouchpoints(
                accountId = account.accountId,
                f8eEnvironment = account.config.f8eEnvironment
              )
            }
          }
        }
      }
  }

  override suspend fun syncNotificationTouchpoints(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<List<NotificationTouchpoint>, Error> {
    return notificationTouchpointF8eClient.getTouchpoints(
      f8eEnvironment = f8eEnvironment,
      accountId = accountId
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

  override suspend fun sendVerificationCodeToTouchpoint(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    touchpoint: NotificationTouchpoint,
    hwProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, Error> {
    return recoveryNotificationVerificationF8eClient.sendVerificationCodeToTouchpoint(
      f8eEnvironment = f8eEnvironment,
      fullAccountId = fullAccountId,
      touchpoint = touchpoint,
      hardwareProofOfPossession = hwProofOfPossession
    )
  }

  override suspend fun verifyCode(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    verificationCode: String,
    hwProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, F8eError<VerifyTouchpointClientErrorCode>> {
    return recoveryNotificationVerificationF8eClient.verifyCode(
      f8eEnvironment = f8eEnvironment,
      fullAccountId = fullAccountId,
      verificationCode = verificationCode,
      hardwareProofOfPossession = hwProofOfPossession
    )
  }
}
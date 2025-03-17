package build.wallet.notifications

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.VerifyTouchpointClientErrorCode
import bitkey.notifications.NotificationTouchpoint
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class NotificationTouchpointServiceFake : NotificationTouchpointService {
  private val defaultData = NotificationTouchpointData(
    phoneNumber = null,
    email = null
  )

  private val notificationTouchpointData: MutableStateFlow<NotificationTouchpointData> =
    MutableStateFlow(defaultData)

  override fun notificationTouchpointData() = notificationTouchpointData

  var syncNotificationTouchpointsResult: Result<List<NotificationTouchpoint>, Error> = Ok(emptyList())

  override suspend fun syncNotificationTouchpoints(
    accountId: AccountId,
  ): Result<List<NotificationTouchpoint>, Error> {
    return syncNotificationTouchpointsResult
  }

  var sendVerificationCodeToTouchpointResult: Result<Unit, Error> = Ok(Unit)

  override suspend fun sendVerificationCodeToTouchpoint(
    fullAccountId: FullAccountId,
    touchpoint: NotificationTouchpoint,
    hwProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, Error> {
    return sendVerificationCodeToTouchpointResult
  }

  var verifyCodeResult: Result<Unit, F8eError<VerifyTouchpointClientErrorCode>> = Ok(Unit)

  override suspend fun verifyCode(
    fullAccountId: FullAccountId,
    verificationCode: String,
    hwProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, F8eError<VerifyTouchpointClientErrorCode>> {
    return verifyCodeResult
  }

  fun reset() {
    notificationTouchpointData.value = defaultData
    syncNotificationTouchpointsResult = Ok(emptyList())
    sendVerificationCodeToTouchpointResult = Ok(Unit)
    verifyCodeResult = Ok(Unit)
  }
}

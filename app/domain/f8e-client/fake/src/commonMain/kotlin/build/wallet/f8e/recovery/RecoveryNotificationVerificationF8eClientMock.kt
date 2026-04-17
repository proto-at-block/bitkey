package build.wallet.f8e.recovery

import app.cash.turbine.Turbine
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.VerifyTouchpointClientErrorCode
import bitkey.notifications.NotificationTouchpoint
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class RecoveryNotificationVerificationF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : RecoveryNotificationVerificationF8eClient {
  data class SendTouchpointCall(
    val fullAccountId: FullAccountId,
    val touchpoint: NotificationTouchpoint,
  )

  data class VerifyTouchpointCall(
    val fullAccountId: FullAccountId,
    val verificationCode: String,
  )

  val sendCodeCalls = turbine("send code calls")
  val verifyCodeCalls = turbine("verify code calls")

  var sendCodeResult: Result<Unit, NetworkingError> = Ok(Unit)
  var verifyCodeResult: Result<Unit, F8eError<VerifyTouchpointClientErrorCode>> = Ok(Unit)

  override suspend fun sendVerificationCodeToTouchpoint(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    touchpoint: NotificationTouchpoint,
  ): Result<Unit, NetworkingError> {
    sendCodeCalls.add(SendTouchpointCall(fullAccountId, touchpoint))
    return sendCodeResult
  }

  override suspend fun verifyCode(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    verificationCode: String,
  ): Result<Unit, F8eError<VerifyTouchpointClientErrorCode>> {
    verifyCodeCalls.add(
      VerifyTouchpointCall(fullAccountId, verificationCode)
    )
    return verifyCodeResult
  }

  fun reset() {
    sendCodeResult = Ok(Unit)
    verifyCodeResult = Ok(Unit)
  }
}

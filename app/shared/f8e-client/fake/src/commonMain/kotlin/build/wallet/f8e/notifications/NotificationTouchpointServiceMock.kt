package build.wallet.f8e.notifications

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.email.Email
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AddTouchpointClientErrorCode
import build.wallet.f8e.error.code.VerifyTouchpointClientErrorCode
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.ktor.result.NetworkingError
import build.wallet.notifications.NotificationTouchpoint
import build.wallet.notifications.NotificationTouchpoint.EmailTouchpoint
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class NotificationTouchpointServiceMock(
  turbine: (String) -> Turbine<Any>,
) : NotificationTouchpointService {
  val addTouchpointCalls = turbine("add touchpoint calls")
  val verifyTouchpointCalls = turbine("verify touchpoint calls")
  val activateTouchpointCalls = turbine("activate touchpoint calls")
  val getTouchpointsCalls = turbine("get touchpoints calls")

  data class AddTouchpointParams(
    val fullAccountId: FullAccountId,
    val touchpoint: NotificationTouchpoint,
  )

  var addTouchpointResult: Result<NotificationTouchpoint, F8eError<AddTouchpointClientErrorCode>> =
    Ok(
      EmailTouchpoint(touchpointId = "123", Email("a@b.com"))
    )

  override suspend fun addTouchpoint(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    touchpoint: NotificationTouchpoint,
  ): Result<NotificationTouchpoint, F8eError<AddTouchpointClientErrorCode>> {
    addTouchpointCalls += AddTouchpointParams(fullAccountId, touchpoint)
    return addTouchpointResult
  }

  data class VerifyTouchpointParams(
    val touchpointId: String,
    val verificationCode: String,
  )

  var verifyTouchpointResult: Result<Unit, F8eError<VerifyTouchpointClientErrorCode>> = Ok(Unit)

  override suspend fun verifyTouchpoint(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    touchpointId: String,
    verificationCode: String,
  ): Result<Unit, F8eError<VerifyTouchpointClientErrorCode>> {
    verifyTouchpointCalls +=
      VerifyTouchpointParams(
        touchpointId,
        verificationCode
      )
    return verifyTouchpointResult
  }

  data class ActivateTouchpointParams(
    val touchpointId: String,
    val hwFactorProofOfPossession: HwFactorProofOfPossession?,
  )

  var activateTouchpointResult: Result<Unit, NetworkError> = Ok(Unit)

  override suspend fun activateTouchpoint(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    touchpointId: String,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, NetworkingError> {
    activateTouchpointCalls +=
      ActivateTouchpointParams(
        touchpointId,
        hwFactorProofOfPossession
      )
    return activateTouchpointResult
  }

  var getTouchpointsResult: Result<List<NotificationTouchpoint>, NetworkingError> = Ok(emptyList())

  override suspend fun getTouchpoints(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<List<NotificationTouchpoint>, NetworkingError> {
    getTouchpointsCalls += fullAccountId
    return getTouchpointsResult
  }

  fun reset() {
    addTouchpointResult = Ok(EmailTouchpoint(touchpointId = "123", Email("a@b.com")))
    verifyTouchpointResult = Ok(Unit)
    getTouchpointsResult = Ok(emptyList())
  }
}

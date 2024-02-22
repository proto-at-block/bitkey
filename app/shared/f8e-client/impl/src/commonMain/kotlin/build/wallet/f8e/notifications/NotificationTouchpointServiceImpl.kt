package build.wallet.f8e.notifications

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.email.Email
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AddTouchpointClientErrorCode
import build.wallet.f8e.error.code.VerifyTouchpointClientErrorCode
import build.wallet.f8e.error.logF8eFailure
import build.wallet.f8e.error.toF8eError
import build.wallet.f8e.notifications.F8eNotificationTouchpoint.F8eEmailTouchpoint
import build.wallet.f8e.notifications.F8eNotificationTouchpoint.F8ePhoneNumberTouchpoint
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.catching
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.log
import build.wallet.logging.logNetworkFailure
import build.wallet.mapUnit
import build.wallet.notifications.NotificationTouchpoint
import build.wallet.notifications.NotificationTouchpoint.EmailTouchpoint
import build.wallet.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.phonenumber.PhoneNumberValidator
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class NotificationTouchpointServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val phoneNumberValidator: PhoneNumberValidator,
) : NotificationTouchpointService {
  override suspend fun addTouchpoint(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    touchpoint: NotificationTouchpoint,
  ): Result<NotificationTouchpoint, F8eError<AddTouchpointClientErrorCode>> {
    return f8eHttpClient.authenticated(f8eEnvironment, fullAccountId)
      .bodyResult<AddTouchpointResponse> {
        post("/api/accounts/${fullAccountId.serverId}/touchpoints") {
          setBody(
            when (touchpoint) {
              // TODO (W-2564): Convert from strong type to E.164 format
              is PhoneNumberTouchpoint ->
                AddPhoneNumberRequest(
                  touchpointId = touchpoint.touchpointId,
                  phoneNumber = touchpoint.value.formattedE164Value
                )

              is EmailTouchpoint ->
                AddEmailRequest(
                  touchpointId = touchpoint.touchpointId,
                  email = touchpoint.value.value
                )
            }
          )
        }
      }
      .mapError { it.toF8eError<AddTouchpointClientErrorCode>() }
      .logF8eFailure { "Failed to add notification touchpoint" }
      .map { response ->
        when (touchpoint) {
          is PhoneNumberTouchpoint ->
            PhoneNumberTouchpoint(
              touchpointId = response.touchpointId,
              value = touchpoint.value
            )

          is EmailTouchpoint ->
            EmailTouchpoint(
              touchpointId = response.touchpointId,
              value = touchpoint.value
            )
        }
      }
  }

  override suspend fun verifyTouchpoint(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    touchpointId: String,
    verificationCode: String,
  ): Result<Unit, F8eError<VerifyTouchpointClientErrorCode>> {
    return f8eHttpClient.authenticated(f8eEnvironment, fullAccountId)
      .catching {
        post("/api/accounts/${fullAccountId.serverId}/touchpoints/$touchpointId/verify") {
          setBody(VerifyTouchpointRequest(verificationCode = verificationCode))
        }
      }
      .mapError { it.toF8eError<VerifyTouchpointClientErrorCode>() }
      .logF8eFailure { "Failed to verify notification touchpoint" }
      .mapUnit()
  }

  override suspend fun activateTouchpoint(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    touchpointId: String,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient.authenticated(
      f8eEnvironment = f8eEnvironment,
      accountId = fullAccountId,
      hwFactorProofOfPossession = hwFactorProofOfPossession
    )
      .catching {
        post("/api/accounts/${fullAccountId.serverId}/touchpoints/$touchpointId/activate") {
          setBody(ActivateTouchpointRequest)
        }
      }
      .mapUnit()
      .logNetworkFailure { "Failed to activate notification touchpoint" }
  }

  override suspend fun getTouchpoints(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<List<NotificationTouchpoint>, NetworkingError> {
    return f8eHttpClient.authenticated(f8eEnvironment, fullAccountId)
      .bodyResult<GetTouchpointsResponse> {
        get("/api/accounts/${fullAccountId.serverId}/touchpoints")
      }
      .map { response ->
        response.touchpoints.mapNotNull { f8eTouchpoint ->
          when (f8eTouchpoint) {
            is F8eEmailTouchpoint ->
              EmailTouchpoint(
                touchpointId = f8eTouchpoint.touchpointId,
                value = Email(f8eTouchpoint.email)
              )
            is F8ePhoneNumberTouchpoint -> {
              val phoneNumber = phoneNumberValidator.validatePhoneNumber(f8eTouchpoint.phoneNumber)
              when (phoneNumber) {
                null -> {
                  log(Error) { "Unable to validate phone number from server" }
                  null
                }
                else ->
                  PhoneNumberTouchpoint(
                    touchpointId = f8eTouchpoint.touchpointId,
                    value = phoneNumber
                  )
              }
            }
          }
        }
      }
      .logNetworkFailure { "Failed to get notification touchpoints" }
  }
}

private typealias AddPhoneNumberRequest = F8ePhoneNumberTouchpoint
private typealias AddEmailRequest = F8eEmailTouchpoint

@Serializable
private data class AddTouchpointResponse(
  @SerialName("touchpoint_id")
  val touchpointId: String,
)

@Serializable
private data class VerifyTouchpointRequest(
  @SerialName("verification_code")
  val verificationCode: String,
)

@Serializable
data object ActivateTouchpointRequest

@Serializable
private data class GetTouchpointsResponse(
  @SerialName("touchpoints")
  val touchpoints: List<F8eNotificationTouchpoint>,
)

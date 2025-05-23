package build.wallet.f8e.notifications

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AddTouchpointClientErrorCode
import bitkey.f8e.error.code.VerifyTouchpointClientErrorCode
import bitkey.f8e.error.toF8eError
import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationPreferences
import bitkey.notifications.NotificationTouchpoint
import bitkey.notifications.NotificationTouchpoint.EmailTouchpoint
import bitkey.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.bitkey.f8e.AccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.email.Email
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.client.plugins.withHardwareFactor
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.notifications.F8eNotificationTouchpoint.F8eEmailTouchpoint
import build.wallet.f8e.notifications.F8eNotificationTouchpoint.F8ePhoneNumberTouchpoint
import build.wallet.ktor.result.*
import build.wallet.logging.logError
import build.wallet.logging.logNetworkFailure
import build.wallet.mapUnit
import build.wallet.phonenumber.PhoneNumberValidator
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class NotificationTouchpointF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val phoneNumberValidator: PhoneNumberValidator,
) : NotificationTouchpointF8eClient {
  override suspend fun addTouchpoint(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    touchpoint: NotificationTouchpoint,
  ): Result<NotificationTouchpoint, F8eError<AddTouchpointClientErrorCode>> {
    return f8eHttpClient.authenticated()
      .bodyResult<AddTouchpointResponse> {
        post("/api/accounts/${accountId.serverId}/touchpoints") {
          withDescription("Add notification touchpoint")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
          setRedactedBody(
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
      .logNetworkFailure { "Failed to add touchpoint" }
      .mapError { it.toF8eError<AddTouchpointClientErrorCode>() }
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
    accountId: AccountId,
    touchpointId: String,
    verificationCode: String,
  ): Result<Unit, F8eError<VerifyTouchpointClientErrorCode>> {
    return f8eHttpClient.authenticated()
      .catching {
        post("/api/accounts/${accountId.serverId}/touchpoints/$touchpointId/verify") {
          withDescription("Verify notification touchpoint")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
          setRedactedBody(VerifyTouchpointRequest(verificationCode = verificationCode))
        }
      }
      .logNetworkFailure { "Failed to verify touchpoint" }
      .mapError { it.toF8eError<VerifyTouchpointClientErrorCode>() }
      .mapUnit()
  }

  override suspend fun activateTouchpoint(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    touchpointId: String,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient.authenticated()
      .catching {
        post("/api/accounts/${accountId.serverId}/touchpoints/$touchpointId/activate") {
          withDescription("Activate notification touchpoint")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
          hwFactorProofOfPossession?.run(::withHardwareFactor)
          setRedactedBody(EmptyRequestBody)
        }
      }
      .logNetworkFailure { "Failed to activate touchpoint" }
      .mapUnit()
  }

  override suspend fun getTouchpoints(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
  ): Result<List<NotificationTouchpoint>, NetworkingError> {
    return f8eHttpClient.authenticated()
      .bodyResult<GetTouchpointsResponse> {
        get("/api/accounts/${accountId.serverId}/touchpoints") {
          withDescription("Get notification touchpoints")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
        }
      }
      .logNetworkFailure { "Failed to get touchpoints" }
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
                  logError { "Unable to validate phone number from server" }
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
  }

  override suspend fun getNotificationsPreferences(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
  ): Result<NotificationPreferences, NetworkingError> {
    return f8eHttpClient.authenticated()
      .bodyResult<NotificationsPreferencesRequest> {
        get("/api/accounts/${accountId.serverId}/notifications-preferences") {
          withDescription("Get notification preferences")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
        }
      }
      .logNetworkFailure { "Failed to get notification preferences" }
      .map { response ->
        NotificationPreferences(
          moneyMovement = response.moneyMovement
            .mapNotNull { NotificationChannel.valueOfOrNull(it) }.toSet(),
          accountSecurity = response.accountSecurity
            .mapNotNull { NotificationChannel.valueOfOrNull(it) }.toSet(),
          productMarketing = response.productMarketing
            .mapNotNull { NotificationChannel.valueOfOrNull(it) }.toSet()
        )
      }
  }

  override suspend fun updateNotificationsPreferences(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    preferences: NotificationPreferences,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, NetworkingError> {
    val prefRequest = NotificationsPreferencesRequest(
      moneyMovement = preferences.moneyMovement.map { it.name },
      accountSecurity = preferences.accountSecurity.map { it.name },
      productMarketing = preferences.productMarketing.map { it.name }
    )
    return f8eHttpClient.authenticated()
      .catching {
        put("/api/accounts/${accountId.serverId}/notifications-preferences") {
          withDescription("Set notification preferences")
          setRedactedBody(prefRequest)
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
          hwFactorProofOfPossession?.run(::withHardwareFactor)
        }
      }
      .logNetworkFailure { "Failed to update notification preferences" }
      .mapUnit()
  }
}

private typealias AddPhoneNumberRequest = F8ePhoneNumberTouchpoint
private typealias AddEmailRequest = F8eEmailTouchpoint

@Serializable
private data class AddTouchpointResponse(
  @SerialName("touchpoint_id")
  val touchpointId: String,
) : RedactedResponseBody

@Serializable
private data class VerifyTouchpointRequest(
  @SerialName("verification_code")
  val verificationCode: String,
) : RedactedRequestBody

@Serializable
private data class GetTouchpointsResponse(
  @SerialName("touchpoints")
  val touchpoints: List<F8eNotificationTouchpoint>,
) : RedactedResponseBody

@Serializable
private data class NotificationsPreferencesRequest(
  @SerialName("account_security")
  val accountSecurity: List<String>,
  @SerialName("money_movement")
  val moneyMovement: List<String>,
  @SerialName("product_marketing")
  val productMarketing: List<String>,
) : RedactedRequestBody, RedactedResponseBody

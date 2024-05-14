package build.wallet.f8e.notifications

import build.wallet.ktor.result.RedactedRequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface F8eNotificationTouchpoint : RedactedRequestBody {
  @Serializable
  @SerialName("Phone")
  data class F8ePhoneNumberTouchpoint(
    /** The id of the touchpoint. */
    @SerialName("id")
    val touchpointId: String,
    /** Phone number string in E.164 format */
    @SerialName("phone_number")
    val phoneNumber: String,
  ) : F8eNotificationTouchpoint

  @Serializable
  @SerialName("Email")
  data class F8eEmailTouchpoint(
    /** The id of the touchpoint. */
    @SerialName("id")
    val touchpointId: String,
    /** Email address as a string */
    @SerialName("email_address")
    val email: String,
  ) : F8eNotificationTouchpoint
}

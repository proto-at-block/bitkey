package build.wallet.f8e.onboarding.model

import build.wallet.ktor.result.RedactedRequestBody
import kotlinx.serialization.*

@Serializable
data class CreateAccountV2RequestBody(
  val auth: FullCreateAccountV2AuthKeys,
  @SerialName("is_test_account")
  val isTestAccount: Boolean?,
  val spend: FullCreateAccountV2SpendingKeys,
) : RedactedRequestBody

@Serializable
data class FullCreateAccountV2AuthKeys(
  /** The global app auth key, corresponds to [AppGlobalAuthPublicKey] */
  @SerialName("app_pub")
  val appGlobalAuthPublicKey: String,
  /** The hardware auth key. */
  @SerialName("hardware_pub")
  val hardwareAuthPublicKey: String,
  /**
   * The recovery app auth key, corresponds to [AppRecoveryAuthPublicKey]
   * Used when the account does not have access to  [AppGlobalAuthPublicKey].
   */
  @SerialName("recovery_pub")
  val recoveryAuthPublicKey: String?,
)

@Serializable
data class FullCreateAccountV2SpendingKeys(
  /** The app spending key, corresponds to [AppSpendingPublicKey] */
  @SerialName("app_pub")
  val app: String,
  /** The hardware spending key, corresponds to [HwSpendingPublicKey] */
  @SerialName("hardware_pub")
  val hardware: String,
  /**The bitcoin network these keys were created on. */
  val network: String,
)

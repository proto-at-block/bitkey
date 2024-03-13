package build.wallet.f8e.onboarding.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface CreateAccountResponseBody {
  @SerialName("account_id")
  val accountId: String
}

/**
 * Response body for the request to create a Full Account.
 * Includes spending keys.
 */
@Serializable
data class FullCreateAccountResponseBody(
  @SerialName("account_id")
  override val accountId: String,
  @SerialName("keyset_id")
  val keysetId: String,
  @SerialName("spending")
  val spending: String,
  @SerialName("spending_sig")
  val spendingSig: String,
) : CreateAccountResponseBody

/**
 * Response body for the request to create a Lite Account.
 * Does not include spending keys.
 */
@Serializable
data class LiteCreateAccountResponseBody(
  @SerialName("account_id")
  override val accountId: String,
) : CreateAccountResponseBody

package build.wallet.f8e.onboarding.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Interface for the auth keys sent to f8e, different for Full and Lite account
 * creation in that Lite accounts only include a recovery auth key, and Full
 * accounts include app, hardware, and recovery auth keys
 */
@Serializable
sealed interface CreateAccountAuthKeys

/**
 * Interface for the request body to create a new account, either Full or Lite.
 * Implemented by [FullCreateAccountRequestBody] and [LiteCreateAccountRequestBody]
 */
@Serializable
internal sealed interface CreateAccountRequestBody {
  @SerialName("auth")
  val auth: CreateAccountAuthKeys

  @SerialName("is_test_account")
  val isTestAccount: Boolean?
}

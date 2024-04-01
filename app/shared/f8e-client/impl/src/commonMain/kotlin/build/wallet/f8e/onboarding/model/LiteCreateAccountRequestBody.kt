package build.wallet.f8e.onboarding.model

import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.crypto.PublicKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for the request to create a Lite Account.
 * Does not include spending keys.
 */
@Serializable
internal data class LiteCreateAccountRequestBody(
  @SerialName("auth")
  override val auth: LiteCreateAccountAuthKeys,
  @SerialName("is_test_account")
  override val isTestAccount: Boolean?,
) : CreateAccountRequestBody {
  constructor(
    appRecoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
    isTestAccount: Boolean?,
  ) : this(
    auth = LiteCreateAccountAuthKeys(recovery = appRecoveryAuthKey),
    isTestAccount = isTestAccount
  )
}

@Serializable
data class LiteCreateAccountAuthKeys(
  /**
   * Represents the recovery app auth key, corresponds to [AppRecoveryAuthPublicKey]
   */
  val recovery: PublicKey<AppRecoveryAuthKey>,
) : CreateAccountAuthKeys

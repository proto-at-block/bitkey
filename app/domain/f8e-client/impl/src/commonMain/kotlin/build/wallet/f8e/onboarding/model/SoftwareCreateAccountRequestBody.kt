package build.wallet.f8e.onboarding.model

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.crypto.PublicKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for the request to create a Software Only Account.
 * Does not include spending keys.
 */
@Serializable
internal data class SoftwareCreateAccountRequestBody(
  @SerialName("auth")
  override val auth: SoftwareCreateAccountAuthKeys,
  @SerialName("is_test_account")
  override val isTestAccount: Boolean?,
) : CreateAccountRequestBody {
  constructor(
    appGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    appRecoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
    isTestAccount: Boolean?,
  ) : this(
    auth = SoftwareCreateAccountAuthKeys(
      app = appGlobalAuthKey,
      recovery = appRecoveryAuthKey
    ),
    isTestAccount = isTestAccount
  )
}

@Serializable
data class SoftwareCreateAccountAuthKeys(
  /**
   * Represents the global app auth key
   */
  val app: PublicKey<AppGlobalAuthKey>,
  /**
   * Represents the recovery app auth key
   */
  val recovery: PublicKey<AppRecoveryAuthKey>,
) : CreateAccountAuthKeys

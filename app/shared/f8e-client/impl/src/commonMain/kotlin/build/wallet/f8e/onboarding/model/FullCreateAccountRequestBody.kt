package build.wallet.f8e.onboarding.model

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.f8e.serialization.toJsonString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for the request to create a Full Account.
 * Includes spending keys.
 */
@Serializable
internal data class FullCreateAccountRequestBody(
  @SerialName("auth")
  override val auth: FullCreateAccountAuthKeys,
  @SerialName("is_test_account")
  override val isTestAccount: Boolean?,
  @SerialName("spending")
  val spending: FullCreateAccountSpendingKeys,
) : CreateAccountRequestBody {
  constructor(
    appKeyBundle: AppKeyBundle,
    hardwareKeyBundle: HwKeyBundle,
    network: BitcoinNetworkType,
    isTestAccount: Boolean?,
  ) : this(
    auth =
      FullCreateAccountAuthKeys(
        app = appKeyBundle.authKey,
        hardware = hardwareKeyBundle.authKey,
        recovery = appKeyBundle.recoveryAuthKey
      ),
    spending =
      FullCreateAccountSpendingKeys(
        app = appKeyBundle.spendingKey,
        hardware = hardwareKeyBundle.spendingKey,
        network = network
      ),
    isTestAccount = isTestAccount
  )
}

@Serializable
data class FullCreateAccountAuthKeys(
  /** The global app auth key, corresponds to [AppGlobalAuthPublicKey] */
  val app: String,
  /** The hardware auth key. */
  val hardware: String,
  /**
   * The recovery app auth key, corresponds to [AppRecoveryAuthPublicKey]
   * Used when the account does not have access to  [AppGlobalAuthPublicKey].
   */
  val recovery: String?,
) : CreateAccountAuthKeys {
  constructor(
    app: AppGlobalAuthPublicKey,
    hardware: HwAuthPublicKey,
    recovery: AppRecoveryAuthPublicKey,
  ) : this(
    app = app.pubKey.value,
    hardware = hardware.pubKey.value,
    recovery = recovery?.pubKey?.value
  )
}

@Serializable
data class FullCreateAccountSpendingKeys(
  /** The app spending key, corresponds to [AppSpendingPublicKey] */
  val app: String,
  /** The hardware spending key, corresponds to [HwSpendingPublicKey] */
  val hardware: String,
  /**The bitcoin network these keys were created on. */
  val network: String,
) {
  constructor(
    app: AppSpendingPublicKey,
    hardware: HwSpendingPublicKey,
    network: BitcoinNetworkType,
  ) : this(
    app = app.key.dpub,
    hardware = hardware.key.dpub,
    network = network.toJsonString()
  )
}

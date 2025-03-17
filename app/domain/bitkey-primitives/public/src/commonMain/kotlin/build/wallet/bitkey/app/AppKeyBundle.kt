package build.wallet.bitkey.app

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.crypto.PublicKey

/**
 * [KeyBundle] for app factor.
 */
data class AppKeyBundle(
  val localId: String,
  val spendingKey: AppSpendingPublicKey,
  val authKey: PublicKey<AppGlobalAuthKey>,
  val networkType: BitcoinNetworkType,
  val recoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
)

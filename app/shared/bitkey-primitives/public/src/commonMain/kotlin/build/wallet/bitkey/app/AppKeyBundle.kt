package build.wallet.bitkey.app

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.keybox.KeyBundle

/**
 * [KeyBundle] for app factor.
 */
data class AppKeyBundle(
  override val localId: String,
  override val spendingKey: AppSpendingPublicKey,
  override val authKey: AppGlobalAuthPublicKey,
  override val networkType: BitcoinNetworkType,
  val recoveryAuthKey: AppRecoveryAuthPublicKey,
) : KeyBundle

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
  /**
   * TODO(BKR-573): generate and backfill App Recovery Auth key for existing Full Account customers.
   */
  val recoveryAuthKey: AppRecoveryAuthPublicKey?,
) : KeyBundle

fun AppKeyBundle.requireRecoveryAuthKey(): AppRecoveryAuthPublicKey =
  requireNotNull(recoveryAuthKey) {
    "Expected an AppKeyBundle to have App Recovery Auth key."
  }

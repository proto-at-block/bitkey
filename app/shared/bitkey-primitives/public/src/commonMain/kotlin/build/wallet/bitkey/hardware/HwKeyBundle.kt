package build.wallet.bitkey.hardware

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.keybox.KeyBundle

/**
 * [KeyBundle] for app factor.
 */
data class HwKeyBundle(
  override val localId: String,
  override val spendingKey: HwSpendingPublicKey,
  override val authKey: HwAuthPublicKey,
  override val networkType: BitcoinNetworkType,
) : KeyBundle

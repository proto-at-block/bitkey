package build.wallet.bitkey.hardware

import build.wallet.bitcoin.BitcoinNetworkType

/**
 * [KeyBundle] for app factor.
 */
data class HwKeyBundle(
  val localId: String,
  val spendingKey: HwSpendingPublicKey,
  val authKey: HwAuthPublicKey,
  val networkType: BitcoinNetworkType,
)

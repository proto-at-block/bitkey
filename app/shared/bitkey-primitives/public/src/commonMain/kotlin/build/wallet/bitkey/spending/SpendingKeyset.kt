package build.wallet.bitkey.spending

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.hardware.HwSpendingPublicKey

/**
 * Represents a keyset for multi-sig segwit (BIP-84) 2-of-3 wallet.
 * Called a "Spending" keyset because it is used, along with its app private key
 * counterpart, to sign transactions in order to spend bitcoin.
 */
data class SpendingKeyset(
  val localId: String,
  val networkType: BitcoinNetworkType,
  val appKey: AppSpendingPublicKey,
  val hardwareKey: HwSpendingPublicKey,
  val f8eSpendingKeyset: F8eSpendingKeyset,
)

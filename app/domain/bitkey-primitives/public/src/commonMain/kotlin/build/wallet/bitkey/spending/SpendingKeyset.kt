package build.wallet.bitkey.spending

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.isPrivateWallet
import build.wallet.bitkey.hardware.HwSpendingPublicKey

/**
 * Represents a keyset for multi-sig segwit (BIP-84) 2-of-3 wallet.
 * Called a "Spending" keyset because it is used, along with its app private key
 * counterpart, to sign transactions in order to spend bitcoin.
 *
 * Can be either legacy (server sees transactions) or private (server blind via chaincode delegation).
 */
data class SpendingKeyset(
  val localId: String,
  val networkType: BitcoinNetworkType,
  val appKey: AppSpendingPublicKey,
  val hardwareKey: HwSpendingPublicKey,
  val f8eSpendingKeyset: F8eSpendingKeyset,
) {
  /**
   * Whether this is a private keyset (server-blind via chaincode delegation).
   */
  val isPrivateWallet = f8eSpendingKeyset.isPrivateWallet

  /**
   * Whether this is a legacy keyset.
   */
  val isLegacyWallet = !f8eSpendingKeyset.isPrivateWallet
}

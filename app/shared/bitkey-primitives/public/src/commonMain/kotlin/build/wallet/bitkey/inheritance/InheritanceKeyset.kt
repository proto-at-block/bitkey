package build.wallet.bitkey.inheritance

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import kotlinx.serialization.Serializable

/**
 * Key data that is encoded and shared with a beneficiary to execute
 * an inheritance transaction.
 */
@Serializable
data class InheritanceKeyset(
  val network: BitcoinNetworkType,
  val appSpendingPublicKey: AppSpendingPublicKey,
  val appSpendingPrivateKey: AppSpendingPrivateKey,
)

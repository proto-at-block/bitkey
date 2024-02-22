package build.wallet.bitkey.keybox

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.auth.AuthPublicKey
import build.wallet.bitkey.spending.SpendingPublicKey

/**
 * This is a collection of SigningKeys from the same Factor, but each related to a different Domain.
 * So our KeyBundles are a collection of 2 SigningKeys (Auth and Spending) from a single Factor.
 */
interface KeyBundle {
  val localId: String
  val spendingKey: SpendingPublicKey
  val authKey: AuthPublicKey
  val networkType: BitcoinNetworkType
}

package build.wallet.bitkey.keybox

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.crypto.PublicKey
import build.wallet.frost.ShareDetails

/**
 * A keybox is a collection of keysets.
 *
 * The [SoftwareKeybox] holds all information required to sign transactions, query transaction lists
 * (and by extension balance) for transactions it signs, and any auth/recovery keys.
 */
data class SoftwareKeybox(
  val id: String,
  val networkType: BitcoinNetworkType,
  val authKey: PublicKey<AppGlobalAuthKey>,
  val recoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
  val shareDetails: ShareDetails,
)

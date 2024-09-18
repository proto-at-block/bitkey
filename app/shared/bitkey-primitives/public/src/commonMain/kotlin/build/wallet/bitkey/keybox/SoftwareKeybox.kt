package build.wallet.bitkey.keybox

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.crypto.PublicKey

/**
 * A keybox is a collection of keysets.
 *
 * The [SoftwareKeybox] holds all information required to sign transactions, query transaction lists
 * (and by extension balance) for transactions it signs, and any auth/recovery keys.
 */
data class SoftwareKeybox(
  val id: String,
  val authKey: PublicKey<AppGlobalAuthKey>,
  val recoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
)

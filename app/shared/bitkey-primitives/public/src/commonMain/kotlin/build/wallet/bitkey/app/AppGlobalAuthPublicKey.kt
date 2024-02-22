package build.wallet.bitkey.app

import build.wallet.encrypt.Secp256k1PublicKey

/**
 * [AppAuthPublicKey] with "global"/"normal" authentication scope. Authorizes access to the majority
 * of the f8e endpoints.
 */
data class AppGlobalAuthPublicKey(
  override val pubKey: Secp256k1PublicKey,
) : AppAuthPublicKey

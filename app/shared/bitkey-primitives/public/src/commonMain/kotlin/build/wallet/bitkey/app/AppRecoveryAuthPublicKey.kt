package build.wallet.bitkey.app

import build.wallet.encrypt.Secp256k1PublicKey

/**
 * [AppAuthPublicKey] with "recovery" authentication scope. Mostly used to authorize Social Recovery
 * operations.
 */
data class AppRecoveryAuthPublicKey(
  override val pubKey: Secp256k1PublicKey,
) : AppAuthPublicKey

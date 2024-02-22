package build.wallet.bitkey.app

import build.wallet.encrypt.Secp256k1PrivateKey
import dev.zacsweers.redacted.annotations.Redacted

/**
 * [AppAuthPrivateKey] with "recovery" authentication scope. Mostly used to authorize Social Recovery
 * operations.
 */
@Redacted
data class AppRecoveryAuthPrivateKey(
  override val key: Secp256k1PrivateKey,
) : AppAuthPrivateKey

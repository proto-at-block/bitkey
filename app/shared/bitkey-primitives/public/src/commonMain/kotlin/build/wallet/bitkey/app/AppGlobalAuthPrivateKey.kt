package build.wallet.bitkey.app

import build.wallet.encrypt.Secp256k1PrivateKey
import dev.zacsweers.redacted.annotations.Redacted

/**
 * [AppAuthPrivateKey] with "global"/"normal" authentication scope. Authorizes access to the majority
 * of the f8e endpoints.
 */
@Redacted
data class AppGlobalAuthPrivateKey(
  override val key: Secp256k1PrivateKey,
) : AppAuthPrivateKey

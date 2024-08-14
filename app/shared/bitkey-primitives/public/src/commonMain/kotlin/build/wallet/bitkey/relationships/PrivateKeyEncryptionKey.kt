package build.wallet.bitkey.relationships

import build.wallet.crypto.SymmetricKey

data class PrivateKeyEncryptionKey(
  val key: SymmetricKey,
) : SymmetricKey by key

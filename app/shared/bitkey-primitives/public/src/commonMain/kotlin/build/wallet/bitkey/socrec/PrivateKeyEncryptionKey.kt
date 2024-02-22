package build.wallet.bitkey.socrec

import build.wallet.crypto.SymmetricKey

data class PrivateKeyEncryptionKey(
  val key: SymmetricKey,
) : SymmetricKey by key

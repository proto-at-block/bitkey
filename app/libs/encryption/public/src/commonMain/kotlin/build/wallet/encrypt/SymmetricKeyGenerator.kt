package build.wallet.encrypt

import build.wallet.crypto.SymmetricKey

interface SymmetricKeyGenerator {
  fun generate(): SymmetricKey
}

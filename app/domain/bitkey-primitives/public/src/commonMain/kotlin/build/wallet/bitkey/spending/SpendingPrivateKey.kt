package build.wallet.bitkey.spending

import build.wallet.bitcoin.keys.ExtendedPrivateKey
import dev.zacsweers.redacted.annotations.Redacted

@Redacted
interface SpendingPrivateKey {
  val key: ExtendedPrivateKey
}

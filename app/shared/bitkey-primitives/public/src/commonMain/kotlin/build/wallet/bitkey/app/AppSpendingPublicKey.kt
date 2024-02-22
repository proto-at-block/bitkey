package build.wallet.bitkey.app

import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.spending.SpendingPublicKey
import dev.zacsweers.redacted.annotations.Redacted

/**
 * Spending private key for the app factor.
 */
@Redacted
data class AppSpendingPublicKey(
  override val key: DescriptorPublicKey,
) : SpendingPublicKey {
  constructor(dpub: String) : this(DescriptorPublicKey(dpub))
}

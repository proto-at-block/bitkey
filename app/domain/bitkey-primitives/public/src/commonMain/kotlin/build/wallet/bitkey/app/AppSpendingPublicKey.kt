package build.wallet.bitkey.app

import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.spending.SpendingPublicKey
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.serialization.Serializable

/**
 * Spending private key for the app factor.
 */
@Redacted
@Serializable(with = AppSpendingPublicKeySerializer::class)
data class AppSpendingPublicKey(
  override val key: DescriptorPublicKey,
) : SpendingPublicKey {
  constructor(dpub: String) : this(DescriptorPublicKey(dpub))
}

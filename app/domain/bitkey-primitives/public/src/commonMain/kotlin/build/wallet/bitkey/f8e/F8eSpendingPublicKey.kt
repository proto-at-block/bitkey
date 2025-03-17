package build.wallet.bitkey.f8e

import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.spending.SpendingPublicKey

data class F8eSpendingPublicKey(
  override val key: DescriptorPublicKey,
) : SpendingPublicKey {
  constructor(dpub: String) : this(DescriptorPublicKey(dpub))
}

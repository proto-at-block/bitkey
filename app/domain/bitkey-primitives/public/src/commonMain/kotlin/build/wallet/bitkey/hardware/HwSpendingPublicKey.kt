package build.wallet.bitkey.hardware

import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.spending.SpendingPublicKey

data class HwSpendingPublicKey(
  override val key: DescriptorPublicKey,
) : SpendingPublicKey {
  constructor(dpub: String) : this(DescriptorPublicKey(dpub))
}

package build.wallet.bitkey.spending

import build.wallet.bitcoin.keys.DescriptorPublicKey

interface SpendingPublicKey {
  val key: DescriptorPublicKey
}

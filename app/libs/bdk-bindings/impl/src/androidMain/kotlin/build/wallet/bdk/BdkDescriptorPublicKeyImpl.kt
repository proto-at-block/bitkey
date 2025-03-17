package build.wallet.bdk

import build.wallet.bdk.bindings.BdkDescriptorPublicKey

internal class BdkDescriptorPublicKeyImpl(
  private val ffiBdkDescriptorPublicKey: FfiDescriptorPublicKey,
) : BdkDescriptorPublicKey {
  override fun raw(): String = ffiBdkDescriptorPublicKey.asString()
}

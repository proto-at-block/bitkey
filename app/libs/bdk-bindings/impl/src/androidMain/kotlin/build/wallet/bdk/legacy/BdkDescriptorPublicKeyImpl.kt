package build.wallet.bdk.legacy

import build.wallet.bdk.bindings.BdkDescriptorPublicKey

internal class BdkDescriptorPublicKeyImpl(
  private val ffiBdkDescriptorPublicKey: FfiDescriptorPublicKey,
) : BdkDescriptorPublicKey {
  override fun raw(): String = ffiBdkDescriptorPublicKey.asString()
}

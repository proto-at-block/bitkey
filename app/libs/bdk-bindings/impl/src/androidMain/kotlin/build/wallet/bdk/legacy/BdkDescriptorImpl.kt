package build.wallet.bdk.legacy

import build.wallet.bdk.bindings.BdkDescriptor

internal class BdkDescriptorImpl(
  private val ffiDescriptor: FfiDescriptor,
) : BdkDescriptor {
  override fun asStringPrivate(): String = ffiDescriptor.asStringPrivate()
}

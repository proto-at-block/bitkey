package build.wallet.bdk.legacy

import build.wallet.bdk.bindings.BdkDescriptorSecretKey
import build.wallet.bdk.bindings.BdkDescriptorSecretKeyFactory

class BdkDescriptorSecretKeyFactoryImpl : BdkDescriptorSecretKeyFactory {
  override fun fromString(secretKey: String): BdkDescriptorSecretKey {
    return BdkDescriptorSecretKeyImpl(FfiDescriptorSecretKey.fromString(secretKey))
  }
}

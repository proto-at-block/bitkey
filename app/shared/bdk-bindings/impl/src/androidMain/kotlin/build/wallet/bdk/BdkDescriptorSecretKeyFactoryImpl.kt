package build.wallet.bdk

import build.wallet.bdk.bindings.BdkDescriptorSecretKey
import build.wallet.bdk.bindings.BdkDescriptorSecretKeyFactory

class BdkDescriptorSecretKeyFactoryImpl : BdkDescriptorSecretKeyFactory {
  override fun fromString(secretKey: String): BdkDescriptorSecretKey {
    return BdkDescriptorSecretKeyImpl(FfiDescriptorSecretKey.fromString(secretKey))
  }
}

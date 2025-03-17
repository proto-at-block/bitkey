package build.wallet.bdk.bindings

interface BdkDescriptorSecretKeyFactory {
  fun fromString(secretKey: String): BdkDescriptorSecretKey
}

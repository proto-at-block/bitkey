package build.wallet.bdk.bindings

interface BdkDescriptorSecretKeyGenerator {
  fun generate(
    network: BdkNetwork,
    mnemonic: BdkMnemonic,
  ): BdkDescriptorSecretKey
}

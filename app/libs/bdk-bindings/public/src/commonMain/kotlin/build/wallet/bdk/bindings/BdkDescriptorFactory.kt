package build.wallet.bdk.bindings

interface BdkDescriptorFactory {
  fun bip84(
    secretsKey: BdkDescriptorSecretKey,
    keychain: BdkKeychainKind,
    network: BdkNetwork,
  ): BdkDescriptor
}

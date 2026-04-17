package build.wallet.nfc

import build.wallet.bdk.bindings.BdkDescriptorSecretKeyGenerator
import build.wallet.bdk.bindings.BdkMnemonicGenerator
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.wallet.SpendingWalletProvider
import build.wallet.bitcoin.wallet.SpendingWalletV2Provider
import build.wallet.di.AppScope
import build.wallet.di.W1
import build.wallet.di.W3
import build.wallet.encrypt.Secp256k1KeyGenerator
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.store.EncryptedKeyValueStoreFactory
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface FakeHardwareKeyStoreComponent {
  // Unqualified FakeHardwareKeyStore is auto-provided by @BitkeyInject on
  // CurrentFakeHardwareKeyStore, which dynamically delegates to @W1 or @W3
  // based on the account's hardwareType config.

  // W1 instance — uses existing store name to preserve existing dev installs
  @Provides
  fun provideW1FakeHardwareKeyStore(
    bdkMnemonicGenerator: BdkMnemonicGenerator,
    bdkDescriptorSecretKeyGenerator: BdkDescriptorSecretKeyGenerator,
    secp256k1KeyGenerator: Secp256k1KeyGenerator,
    encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
  ): @W1 FakeHardwareKeyStore =
    FakeHardwareKeyStoreImpl(
      bdkMnemonicGenerator = bdkMnemonicGenerator,
      bdkDescriptorSecretKeyGenerator = bdkDescriptorSecretKeyGenerator,
      secp256k1KeyGenerator = secp256k1KeyGenerator,
      encryptedKeyValueStoreFactory = encryptedKeyValueStoreFactory,
      storeName = FakeHardwareKeyStoreImpl.DEFAULT_STORE_NAME
    )

  // W3 instance — independent store with its own seed
  @Provides
  fun provideW3FakeHardwareKeyStore(
    bdkMnemonicGenerator: BdkMnemonicGenerator,
    bdkDescriptorSecretKeyGenerator: BdkDescriptorSecretKeyGenerator,
    secp256k1KeyGenerator: Secp256k1KeyGenerator,
    encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
  ): @W3 FakeHardwareKeyStore =
    FakeHardwareKeyStoreImpl(
      bdkMnemonicGenerator = bdkMnemonicGenerator,
      bdkDescriptorSecretKeyGenerator = bdkDescriptorSecretKeyGenerator,
      secp256k1KeyGenerator = secp256k1KeyGenerator,
      encryptedKeyValueStoreFactory = encryptedKeyValueStoreFactory,
      storeName = FakeHardwareKeyStoreImpl.W3_STORE_NAME
    )

  // Unqualified binding — backward compat
  @Provides
  fun provideFakeHardwareSpendingWalletProvider(
    @W1 w1: FakeHardwareSpendingWalletProvider,
  ): FakeHardwareSpendingWalletProvider = w1

  // W1 spending wallet provider
  @Provides
  fun provideW1FakeHardwareSpendingWalletProvider(
    spendingWalletProvider: SpendingWalletProvider,
    spendingWalletV2Provider: SpendingWalletV2Provider,
    bdk2FeatureFlag: Bdk2FeatureFlag,
    descriptorBuilder: BitcoinMultiSigDescriptorBuilder,
    @W1 fakeHardwareKeyStore: FakeHardwareKeyStore,
  ): @W1 FakeHardwareSpendingWalletProvider =
    FakeHardwareSpendingWalletProvider(
      spendingWalletProvider = spendingWalletProvider,
      spendingWalletV2Provider = spendingWalletV2Provider,
      bdk2FeatureFlag = bdk2FeatureFlag,
      descriptorBuilder = descriptorBuilder,
      fakeHardwareKeyStore = fakeHardwareKeyStore
    )

  // W3 spending wallet provider
  @Provides
  fun provideW3FakeHardwareSpendingWalletProvider(
    spendingWalletProvider: SpendingWalletProvider,
    spendingWalletV2Provider: SpendingWalletV2Provider,
    bdk2FeatureFlag: Bdk2FeatureFlag,
    descriptorBuilder: BitcoinMultiSigDescriptorBuilder,
    @W3 fakeHardwareKeyStore: FakeHardwareKeyStore,
  ): @W3 FakeHardwareSpendingWalletProvider =
    FakeHardwareSpendingWalletProvider(
      spendingWalletProvider = spendingWalletProvider,
      spendingWalletV2Provider = spendingWalletV2Provider,
      bdk2FeatureFlag = bdk2FeatureFlag,
      descriptorBuilder = descriptorBuilder,
      fakeHardwareKeyStore = fakeHardwareKeyStore
    )
}

package build.wallet.testing.ext

import build.wallet.bdk.bindings.BdkBlockchainFactory
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.f8e.F8eEnvironment
import build.wallet.feature.FeatureFlagValue
import build.wallet.nfc.FakeHardwareKeyStore
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import io.kotest.core.test.TestScope

suspend fun AppTester.enableChaincodeDelegation() {
  encryptedDescriptorBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
  chaincodeDelegationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
}

suspend fun TestScope.launchPrivateWalletApp(
  bdkBlockchainFactory: BdkBlockchainFactory? = null,
  f8eEnvironment: F8eEnvironment? = null,
  bitcoinNetworkType: BitcoinNetworkType? = null,
  cloudStoreAccountRepository: CloudStoreAccountRepository? = null,
  cloudKeyValueStore: CloudKeyValueStore? = null,
  hardwareSeed: FakeHardwareKeyStore.Seed? = null,
  isUsingSocRecFakes: Boolean = false,
  executeWorkers: Boolean = true,
): AppTester {
  val app = launchNewApp(
    bdkBlockchainFactory = bdkBlockchainFactory,
    f8eEnvironment = f8eEnvironment,
    bitcoinNetworkType = bitcoinNetworkType,
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    cloudKeyValueStore = cloudKeyValueStore,
    hardwareSeed = hardwareSeed,
    isUsingSocRecFakes = isUsingSocRecFakes,
    executeWorkers = executeWorkers
  )
  app.enableChaincodeDelegation()
  return app
}

package build.wallet.treasury

import build.wallet.bdk.BdkDescriptorFactoryImpl
import build.wallet.bdk.BdkDescriptorSecretKeyFactoryImpl
import build.wallet.bdk.bindings.BdkKeychainKind.EXTERNAL
import build.wallet.bdk.bindings.BdkKeychainKind.INTERNAL
import build.wallet.bitcoin.BitcoinNetworkType.REGTEST
import build.wallet.bitcoin.bdk.bdkNetwork
import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Spending
import build.wallet.bitcoin.wallet.SpendingWalletDescriptor
import build.wallet.money.BitcoinMoney
import build.wallet.testing.AppTester.Companion.launchNewApp
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.EnabledIf

class RegtestTreasuryWalletTests : FunSpec({
  val enableOnlyForRegtest: EnabledIf = { launchNewApp().initialBitcoinNetworkType == REGTEST }

  test("we can fund a regtest treasury").config(enabledIf = enableOnlyForRegtest) {
    val network = REGTEST
    val appTester = launchNewApp()
    // Set the keybox config to regtest because the syncer use its network. It'd be better
    // to construct the needed dependencies from scratch in the test instead of relying on
    // appTester, but instantiating the graph is far too complex to be maintainable.
    appTester.app.appComponent.debugOptionsService.setBitcoinNetworkType(network)
    val bdkDescriptorFactory = BdkDescriptorFactoryImpl()
    val xprv = appTester.app.appComponent.extendedKeyGenerator.generate(network).getOrThrow()
    val key = BdkDescriptorSecretKeyFactoryImpl().fromString(xprv.privateKey.xprv)
    val descriptor =
      SpendingWalletDescriptor(
        identifier = "temp-wallet",
        networkType = network,
        receivingDescriptor =
          Spending(
            bdkDescriptorFactory
              .bip84(key, EXTERNAL, network.bdkNetwork)
              .asStringPrivate()
          ),
        changeDescriptor =
          Spending(
            bdkDescriptorFactory
              .bip84(key, INTERNAL, network.bdkNetwork)
              .asStringPrivate()
          )
      )
    val destination =
      appTester.app.appComponent.spendingWalletProvider.getWallet(
        descriptor
      ).getOrThrow()

    appTester.treasuryWallet.fund(destination, BitcoinMoney.sats(10000))
  }
})

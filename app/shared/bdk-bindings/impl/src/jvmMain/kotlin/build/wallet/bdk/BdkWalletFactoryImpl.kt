package build.wallet.bdk

import build.wallet.bdk.bindings.BdkDatabaseConfig
import build.wallet.bdk.bindings.BdkNetwork
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bdk.bindings.BdkWallet
import build.wallet.bdk.bindings.BdkWalletFactory
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import org.bitcoindevkit.Descriptor

@BitkeyInject(AppScope::class)
class BdkWalletFactoryImpl : BdkWalletFactory {
  override fun walletBlocking(
    descriptor: String,
    changeDescriptor: String?,
    network: BdkNetwork,
    databaseConfig: BdkDatabaseConfig,
  ): BdkResult<BdkWallet> =
    runCatchingBdkError {
      BdkWalletImpl(
        ffiWallet =
          FfiWallet(
            descriptor = Descriptor(descriptor = descriptor, network = network.ffiNetwork),
            changeDescriptor =
              changeDescriptor?.let {
                Descriptor(
                  descriptor = it,
                  network = network.ffiNetwork
                )
              },
            network = network.ffiNetwork,
            databaseConfig = databaseConfig.ffiDatabaseConfig
          )
      )
    }
}

package build.wallet.bdk.bindings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BdkWalletFactory {
  fun walletBlocking(
    descriptor: String,
    changeDescriptor: String?,
    network: BdkNetwork,
    databaseConfig: BdkDatabaseConfig,
  ): BdkResult<BdkWallet>
}

suspend fun BdkWalletFactory.wallet(
  descriptor: String,
  changeDescriptor: String?,
  network: BdkNetwork,
  databaseConfig: BdkDatabaseConfig,
): BdkResult<BdkWallet> {
  return withContext(Dispatchers.BdkIO) {
    walletBlocking(descriptor, changeDescriptor, network, databaseConfig)
  }
}

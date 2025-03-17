package build.wallet.bdk.bindings

import build.wallet.bdk.bindings.BdkResult.Ok

class BdkWalletFactoryMock : BdkWalletFactory {
  override fun walletBlocking(
    descriptor: String,
    changeDescriptor: String?,
    network: BdkNetwork,
    databaseConfig: BdkDatabaseConfig,
  ): BdkResult<BdkWallet> {
    return Ok(BdkWalletMock())
  }
}

package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkDatabaseConfig
import build.wallet.bdk.bindings.BdkDatabaseConfig.Memory

class BdkDatabaseConfigProviderMock : BdkDatabaseConfigProvider {
  override suspend fun sqliteConfig(identifier: String): BdkDatabaseConfig = Memory
}

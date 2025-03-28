package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkDatabaseConfig

interface BdkDatabaseConfigProvider {
  /**
   * Creates a unique Bdk database configuration. Returned instance should be used for a specific
   * BDK wallet only. Attempting to reuse this config for a different wallet will result in
   * checksum error by BDK.
   *
   * @param identifier unique identifier for the wallet database configuration.
   */
  suspend fun sqliteConfig(identifier: String): BdkDatabaseConfig
}

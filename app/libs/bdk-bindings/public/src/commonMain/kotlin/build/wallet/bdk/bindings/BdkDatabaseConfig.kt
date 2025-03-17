package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L91
 */
sealed class BdkDatabaseConfig {
  data object Memory : BdkDatabaseConfig()

  data class Sqlite(
    val config: BdkSqliteDbConfiguration,
  ) : BdkDatabaseConfig()
}

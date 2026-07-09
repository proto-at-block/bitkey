package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L91
 */
// Kept as a sealed class for Swift interop: iOS sources match nested subclasses
// (e.g. `BdkDatabaseConfig.Memory`), whose Swift nesting is lost if this becomes an interface.
@Suppress("AbstractClassCanBeInterface")
sealed class BdkDatabaseConfig {
  data object Memory : BdkDatabaseConfig()

  data class Sqlite(
    val config: BdkSqliteDbConfiguration,
  ) : BdkDatabaseConfig()
}

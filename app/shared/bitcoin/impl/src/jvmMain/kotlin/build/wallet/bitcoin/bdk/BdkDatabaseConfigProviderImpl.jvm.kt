package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkDatabaseConfig
import build.wallet.bdk.bindings.BdkDatabaseConfig.Memory

/**
 * A temporary workaround to use in memory bdk database config in jvm tests.
 *
 * TODO(W-3639): add support to use Sqlite database config in JVM.
 */
actual fun bdkDatabaseConfigOverride(): BdkDatabaseConfig? = Memory

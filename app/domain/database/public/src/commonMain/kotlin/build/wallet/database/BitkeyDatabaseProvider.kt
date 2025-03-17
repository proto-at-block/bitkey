package build.wallet.database

import build.wallet.database.sqldelight.BitkeyDatabase
import build.wallet.database.sqldelight.BitkeyDebugDatabase

/**
 * Provide various database implementation for the application.
 */
interface BitkeyDatabaseProvider {
  /**
   * Provide the default database that is used for customer data.
   */
  suspend fun database(): BitkeyDatabase

  /**
   * Provide a database that is used for data related to non-customer debugging.
   */
  suspend fun debugDatabase(): BitkeyDebugDatabase
}

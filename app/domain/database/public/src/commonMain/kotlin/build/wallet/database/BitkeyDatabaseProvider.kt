package build.wallet.database

import build.wallet.database.sqldelight.BitkeyDatabase

/**
 * Provide various database implementation for the application.
 */
interface BitkeyDatabaseProvider {
  /**
   * Provide the default database that is used for customer data.
   */
  suspend fun database(): BitkeyDatabase
}

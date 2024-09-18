package build.wallet.sqldelight

interface DatabaseIntegrityChecker {
  /**
   * Ensures that the database and its associated private key are in a valid state. The only invalid
   * state today is if the [databaseEncryptionKey] is null but the database has already been created
   * and is expected to be encrypted.
   *
   * Purges the /database directory in its entirety if invalid.
   *
   * Returns true if valid, false if invalid.
   */
  suspend fun purgeDatabaseStateIfInvalid(databaseEncryptionKey: String?): Boolean
}

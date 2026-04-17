package build.wallet.sqldelight

import android.app.Application
import android.content.SharedPreferences
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.logging.logWarn
import build.wallet.platform.config.AppVariant
import build.wallet.platform.random.UuidGenerator
import build.wallet.store.EncryptedKeyValueStoreFactory
import com.github.michaelbull.result.getError
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Real Android implementation of the [SqlDriverFactory], uses [AndroidSqliteDriver].
 */

@BitkeyInject(AppScope::class)
class SqlDriverFactoryImpl(
  private val application: Application,
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
  private val uuidGenerator: UuidGenerator,
  private val appVariant: AppVariant,
  private val databaseIntegrityChecker: DatabaseIntegrityChecker,
) : SqlDriverFactory {
  private val openedDatabases = mutableSetOf<String>()

  override suspend fun createDriver(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ): SqlDriver {
    val driverCallback =
      object : AndroidSqliteDriver.Callback(dataBaseSchema) {
        override fun onOpen(db: SupportSQLiteDatabase) {
          super.onOpen(db)
          db.setForeignKeyConstraintsEnabled(true)
          markDatabaseOpened(dataBaseName)
        }
      }

    return if (appVariant == AppVariant.Development) {
      // Unencrypted db for development
      AndroidSqliteDriver(
        schema = dataBaseSchema,
        context = application,
        name = dataBaseName,
        callback = driverCallback
      )
    } else {
      createEncryptedDriver(
        dataBaseName = dataBaseName,
        dataBaseSchema = dataBaseSchema,
        driverCallback = driverCallback
      )
    }
  }

  private suspend fun createEncryptedDriver(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
    driverCallback: AndroidSqliteDriver.Callback,
  ): SqlDriver {
    val initialFileState = databaseFileState(dataBaseName)
    val initialHasDbKey = hasStoredDbKey()
    val bootstrapRecoveryStarted =
      ensureBootstrapRecoveryStarted(
        dataBaseName = dataBaseName,
        initialFileState = initialFileState,
        initialHasDbKey = initialHasDbKey
      )

    @Suppress("TooGenericExceptionCaught")
    return try {
      val encryptedFactory = createAndVerifyEncryptedFactory(dataBaseName, dataBaseSchema)
      openAndForceEncryptedDriver(dataBaseName, dataBaseSchema, encryptedFactory, driverCallback)
    } catch (error: Throwable) {
      val failureFileState = databaseFileState(dataBaseName)
      logDatabaseOpenFailure(
        dataBaseName = dataBaseName,
        error = error,
        initialFileState = initialFileState,
        failureFileState = failureFileState,
        initialHasDbKey = initialHasDbKey,
        failureHasDbKey = hasStoredDbKey()
      )

      if (!error.isRecoverableEncryptedDatabaseError() ||
        !shouldAttemptBootstrapRecovery(dataBaseName, bootstrapRecoveryStarted)
      ) {
        throw error
      }

      logWarn(tag = LOG_TAG) {
        "Detected invalid SQLCipher state for $dataBaseName before its first successful open. Resetting the failed local encrypted database and retrying once."
      }
      markBootstrapRecoveryAttempted(dataBaseName)
      resetEncryptedDatabaseState(dataBaseName)

      val encryptedFactory = createAndVerifyEncryptedFactory(dataBaseName, dataBaseSchema)
      return openAndForceEncryptedDriver(
        dataBaseName = dataBaseName,
        dataBaseSchema = dataBaseSchema,
        encryptedFactory = encryptedFactory,
        driverCallback = driverCallback
      )
    }
  }

  private fun openEncryptedDriver(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
    encryptedFactory: SupportOpenHelperFactory,
    driverCallback: AndroidSqliteDriver.Callback,
  ): AndroidSqliteDriver {
    return AndroidSqliteDriver(
      schema = dataBaseSchema,
      context = application,
      name = dataBaseName,
      factory = encryptedFactory,
      callback = driverCallback
    )
  }

  private fun openAndForceEncryptedDriver(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
    encryptedFactory: SupportOpenHelperFactory,
    driverCallback: AndroidSqliteDriver.Callback,
  ): AndroidSqliteDriver {
    val driver =
      openEncryptedDriver(
        dataBaseName = dataBaseName,
        dataBaseSchema = dataBaseSchema,
        encryptedFactory = encryptedFactory,
        driverCallback = driverCallback
      )

    @Suppress("TooGenericExceptionCaught")
    try {
      forceDatabaseOpen(driver)
      return driver
    } catch (error: Throwable) {
      try {
        driver.close()
      } catch (closeError: Exception) {
        error.addSuppressed(closeError)
      }
      throw error
    }
  }

  private suspend fun createAndVerifyEncryptedFactory(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ): SupportOpenHelperFactory {
    val dbKey = loadDbKey(encryptedKeyValueStoreFactory, databaseIntegrityChecker, uuidGenerator)
    System.loadLibrary("sqlcipher")
    val encryptedFactory = SupportOpenHelperFactory(dbKey.toByteArray(Charsets.UTF_8))

    // Run extra check for Team builds to ensure db is encrypted on device
    if (appVariant == AppVariant.Team) {
      verifyDatabaseEncrypted(encryptedFactory, dataBaseName, dataBaseSchema)
    }

    return encryptedFactory
  }

  /**
   * Check that we're actually encrypting the db. First open the encrypted version, for first-run
   * instances. Then attempt to open the db unencrypted. It should fail.
   */
  private fun verifyDatabaseEncrypted(
    encryptedFactory: SupportOpenHelperFactory,
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ) {
    val dbCallback =
      object : AndroidSqliteDriver.Callback(dataBaseSchema) {
        override fun onOpen(db: SupportSQLiteDatabase) {
          super.onOpen(db)
          db.setForeignKeyConstraintsEnabled(true)
          markDatabaseOpened(dataBaseName)
        }

        override fun onCorruption(db: SupportSQLiteDatabase) {
          // By default, if the db is corrupted, Android deletes the file.
          // https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/database/DefaultDatabaseErrorHandler.java#53
        }
      }

    val encryptedDriver =
      AndroidSqliteDriver(
        schema = dataBaseSchema,
        context = application,
        name = dataBaseName,
        factory = encryptedFactory,
        callback = dbCallback
      )

    @Suppress("TooGenericExceptionCaught")
    try {
      simpleQuery(encryptedDriver)
    } catch (e: Throwable) {
      try {
        encryptedDriver.close()
      } catch (closeError: Exception) {
        e.addSuppressed(closeError)
      }
      throw e
    }

    val connectAttempt =
      catchingResult {
        val clearDriver =
          AndroidSqliteDriver(
            schema = dataBaseSchema,
            context = application,
            name = dataBaseName,
            callback = dbCallback
          )
        simpleQuery(clearDriver)
      }
    if (connectAttempt.getError() == null) {
      throw DbNotEncryptedException("Database opened unencrypted. Check your linker settings.")
    }
  }

  /**
   * Basic query that forces the db file to be created
   */
  private fun simpleQuery(encryptedDriver: AndroidSqliteDriver) {
    forceDatabaseOpen(encryptedDriver)
    encryptedDriver.close()
  }

  private fun forceDatabaseOpen(driver: AndroidSqliteDriver) {
    driver.executeQuery(null, "PRAGMA user_version;", { cursor ->
      QueryResult.Value(1)
    }, 0, {})
  }

  private fun resetEncryptedDatabaseState(dataBaseName: String) {
    val dbFile = application.getDatabasePath(dataBaseName)
    val existedBefore = dbFile.exists()
    val deleted = application.deleteDatabase(dataBaseName)
    val existsAfterDelete = dbFile.exists()

    if ((existedBefore && !deleted) || existsAfterDelete) {
      throw FailedToDeleteDatabaseException(
        "Failed to delete $dataBaseName during SQLCipher recovery. " +
          "existedBefore=$existedBefore deleted=$deleted existsAfterDelete=$existsAfterDelete"
      )
    }
  }

  private fun Throwable.isRecoverableEncryptedDatabaseError(): Boolean {
    return generateSequence(this) { it.cause }
      .mapNotNull { it.message?.lowercase() }
      .any { message ->
        message.contains("file is not a database") ||
          message.contains("file is encrypted or is not a database") ||
          message.contains("code 26")
      }
  }

  private fun shouldAttemptBootstrapRecovery(dataBaseName: String): Boolean {
    return dataBaseName !in openedDatabases &&
      !hasDatabaseBeenOpened(dataBaseName) &&
      !hasBootstrapRecoveryAttempted(dataBaseName)
  }

  private fun shouldAttemptBootstrapRecovery(
    dataBaseName: String,
    bootstrapRecoveryStarted: Boolean,
  ): Boolean {
    return bootstrapRecoveryStarted && shouldAttemptBootstrapRecovery(dataBaseName)
  }

  private fun ensureBootstrapRecoveryStarted(
    dataBaseName: String,
    initialFileState: DatabaseFileState,
    initialHasDbKey: Boolean,
  ): Boolean {
    if (hasBootstrapRecoveryStarted(dataBaseName)) {
      return true
    }

    val isFreshInstallBootstrap = !initialFileState.exists && !initialHasDbKey
    if (!isFreshInstallBootstrap) {
      return false
    }

    updateBootstrapRecoveryPrefs("mark bootstrap start for $dataBaseName") {
      putBoolean(bootstrapStartedPrefKey(dataBaseName), true)
    }
    return true
  }

  private fun markDatabaseOpened(dataBaseName: String) {
    openedDatabases.add(dataBaseName)
    try {
      updateBootstrapRecoveryPrefs("mark database opened for $dataBaseName") {
        putBoolean(successfulOpenPrefKey(dataBaseName), true)
        putBoolean(bootstrapStartedPrefKey(dataBaseName), false)
      }
    } catch (e: FailedToPersistBootstrapRecoveryStateException) {
      logWarn(tag = LOG_TAG, throwable = e) {
        "Best-effort markDatabaseOpened failed for $dataBaseName; database is healthy, continuing."
      }
    }
  }

  private fun hasDatabaseBeenOpened(dataBaseName: String): Boolean {
    return bootstrapRecoveryPrefs().getBoolean(successfulOpenPrefKey(dataBaseName), false)
  }

  private fun markBootstrapRecoveryAttempted(dataBaseName: String) {
    updateBootstrapRecoveryPrefs("mark bootstrap recovery attempted for $dataBaseName") {
      putBoolean(recoveryAttemptedPrefKey(dataBaseName), true)
    }
  }

  private fun hasBootstrapRecoveryAttempted(dataBaseName: String): Boolean {
    return bootstrapRecoveryPrefs().getBoolean(recoveryAttemptedPrefKey(dataBaseName), false)
  }

  private fun hasBootstrapRecoveryStarted(dataBaseName: String): Boolean {
    return bootstrapRecoveryPrefs().getBoolean(bootstrapStartedPrefKey(dataBaseName), false)
  }

  private fun updateBootstrapRecoveryPrefs(
    operation: String,
    update: SharedPreferences.Editor.() -> Unit,
  ) {
    val editor = bootstrapRecoveryPrefs().edit()
    editor.update()
    if (!editor.commit()) {
      throw FailedToPersistBootstrapRecoveryStateException(
        "Failed to $operation in $BOOTSTRAP_RECOVERY_PREFS"
      )
    }
  }

  private fun bootstrapRecoveryPrefs() =
    application.getSharedPreferences(BOOTSTRAP_RECOVERY_PREFS, Application.MODE_PRIVATE)

  private fun bootstrapStartedPrefKey(dataBaseName: String) = "bootstrap-started:$dataBaseName"

  private fun successfulOpenPrefKey(dataBaseName: String) = "successful-open:$dataBaseName"

  private fun recoveryAttemptedPrefKey(dataBaseName: String) = "recovery-attempted:$dataBaseName"

  private suspend fun hasStoredDbKey(): Boolean {
    return encryptedKeyValueStoreFactory
      .getOrCreate(storeName = SQL_CIPHER_STORE_NAME)
      .getStringOrNull(DB_KEY) != null
  }

  private fun databaseFileState(dataBaseName: String): DatabaseFileState {
    val file = application.getDatabasePath(dataBaseName)
    return DatabaseFileState(
      exists = file.exists(),
      lengthBytes = file.takeIf { it.exists() }?.length()
    )
  }

  private fun logDatabaseOpenFailure(
    dataBaseName: String,
    error: Throwable,
    initialFileState: DatabaseFileState,
    failureFileState: DatabaseFileState,
    initialHasDbKey: Boolean,
    failureHasDbKey: Boolean,
  ) {
    logError(tag = LOG_TAG, throwable = error) {
      buildString {
        append("SQLCipher open failed for ")
        append(dataBaseName)
        append(". initialFile=")
        append(initialFileState)
        append(", failureFile=")
        append(failureFileState)
        append(", initialHasDbKey=")
        append(initialHasDbKey)
        append(", failureHasDbKey=")
        append(failureHasDbKey)
        append(", appVariant=")
        append(appVariant)
      }
    }
  }

  private data class DatabaseFileState(
    val exists: Boolean,
    val lengthBytes: Long?,
  ) {
    override fun toString(): String {
      return "exists=$exists,lengthBytes=${lengthBytes ?: "null"}"
    }
  }

  private companion object {
    const val LOG_TAG = "SqlDriverFactory"
    const val SQL_CIPHER_STORE_NAME = "SqlCipherStore"
    const val DB_KEY = "db-key"
    const val BOOTSTRAP_RECOVERY_PREFS = "sqlcipher-bootstrap-recovery"
  }
}

private class FailedToDeleteDatabaseException(
  message: String,
) : Exception(message)

private class FailedToPersistBootstrapRecoveryStateException(
  message: String,
) : Exception(message)

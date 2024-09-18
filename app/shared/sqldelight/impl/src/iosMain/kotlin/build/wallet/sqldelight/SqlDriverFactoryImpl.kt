package build.wallet.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import build.wallet.catchingResult
import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppVariant
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.random.UuidGenerator
import build.wallet.store.EncryptedKeyValueStoreFactory
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.createDatabaseManager
import com.github.michaelbull.result.getError

/**
 * Real iOS implementation of the [SqlDriverFactory], uses [NativeSqliteDriver].
 */
actual class SqlDriverFactoryImpl actual constructor(
  platformContext: PlatformContext,
  fileDirectoryProvider: FileDirectoryProvider,
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
  private val uuidGenerator: UuidGenerator,
  private val appVariant: AppVariant,
  private val databaseIntegrityChecker: DatabaseIntegrityChecker,
) : SqlDriverFactory {
  override fun createDriver(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ): SqlDriver {
    val dbKey = loadDbKey(encryptedKeyValueStoreFactory, databaseIntegrityChecker, uuidGenerator)

    // Run extra check for Team builds to ensure db is encrypted on device
    if (appVariant == AppVariant.Team) {
      verifyDatabaseEncrypted(dataBaseName, dataBaseSchema, dbKey)
    }

    return NativeSqliteDriver(
      if (appVariant == AppVariant.Development) {
        // Unencrypted db for development
        createClearDatabaseConfig(dataBaseName, dataBaseSchema)
      } else {
        createEncryptedDatabaseConfig(dataBaseName, dataBaseSchema, dbKey)
      }
    ).also {
      /*
      We explicitly disable [foreignKeyConstraints] in the SqlDriver configuration above
      and instead turn on the foreign key constraints here below.

      We do not want the foreign keys check to be run on DB migrations because it causes
      failures when tables referenced by foreign keys are altered during the migration.
      So, this avoids that by turning on the foreign key constraints here, after the driver
      has been created and the DB has been set up, and during migrations, we finish every
      migration with "PRAGMA foreign_key_check;"
       */
      it.execute(identifier = null, sql = "PRAGMA foreign_keys=ON", parameters = 0)
    }
  }

  private fun createEncryptedDatabaseConfig(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
    dbKey: String,
  ) = createClearDatabaseConfig(dataBaseName, dataBaseSchema).copy(
    encryptionConfig = DatabaseConfiguration.Encryption(dbKey),
    // https://github.com/sqlcipher/sqlcipher/issues/255
    lifecycleConfig =
      DatabaseConfiguration.Lifecycle(onCreateConnection = { conn ->
        conn.rawExecSql("PRAGMA cipher_plaintext_header_size=32")
      })
  )

  private fun createClearDatabaseConfig(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ) = DatabaseConfiguration(
    name = dataBaseName,
    version = dataBaseSchema.version.toInt(),
    create = { connection ->
      wrapConnection(connection) { dataBaseSchema.create(it) }
    },
    upgrade = { connection, oldVersion, newVersion ->
      wrapConnection(connection) {
        dataBaseSchema.migrate(
          driver = it,
          oldVersion = oldVersion.toLong(),
          newVersion = newVersion.toLong()
        )
      }
    },
    journalMode = JournalMode.WAL,
    extendedConfig =
      DatabaseConfiguration.Extended(
        foreignKeyConstraints = false
      )
  )

  /**
   * Check that we're actually encrypting the db. On iOS, sqlcipher is a drop-in replacement for sqlite.
   * To get encrypted db's, you link to the custom sqlite implementation. However, it is *very* easy
   * to change config and link to the system sqlite. You won't know it's not encrypted. This check
   * runs with non-public builds to verify that the build config didn't break linking.
   */
  private fun verifyDatabaseEncrypted(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
    dbKey: String,
  ) {
    // If our first time, must open db encrypted to created it
    createDatabaseManager(createEncryptedDatabaseConfig(dataBaseName, dataBaseSchema, dbKey))
      .createMultiThreadedConnection().close()

    val connectAttempt =
      catchingResult {
        createDatabaseManager(createClearDatabaseConfig(dataBaseName, dataBaseSchema))
          .createMultiThreadedConnection().close()
      }
    if (connectAttempt.getError() == null) {
      throw DbNotEncryptedException("Database opened unencrypted. Check your linker settings.")
    }
  }
}

package build.wallet.sqldelight

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import build.wallet.catching
import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppVariant
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.random.UuidGenerator
import build.wallet.store.EncryptedKeyValueStoreFactory
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Real Android implementation of the [SqlDriverFactory], uses [AndroidSqliteDriver].
 */
actual class SqlDriverFactoryImpl actual constructor(
  private val platformContext: PlatformContext,
  @Suppress("UnusedPrivateProperty")
  private val fileDirectoryProvider: FileDirectoryProvider,
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
  private val uuidGenerator: UuidGenerator,
  @Suppress("UnusedPrivateProperty")
  private val appVariant: AppVariant,
) : SqlDriverFactory {
  override fun createDriver(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ): SqlDriver {
    val driverCallback =
      object : AndroidSqliteDriver.Callback(dataBaseSchema) {
        override fun onOpen(db: SupportSQLiteDatabase) {
          super.onOpen(db)
          db.setForeignKeyConstraintsEnabled(true)
        }
      }

    return if (appVariant == AppVariant.Development) {
      // Unencrypted db for development
      AndroidSqliteDriver(
        schema = dataBaseSchema,
        context = platformContext.appContext,
        name = dataBaseName,
        callback = driverCallback
      )
    } else {
      val encryptedFactory = createAndVerifyEncryptedFactory(dataBaseName, dataBaseSchema)
      // Encrypted db for team and customer
      AndroidSqliteDriver(
        schema = dataBaseSchema,
        context = platformContext.appContext,
        name = dataBaseName,
        factory = encryptedFactory,
        callback = driverCallback
      )
    }
  }

  private fun createAndVerifyEncryptedFactory(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ): SupportOpenHelperFactory {
    val dbKey = loadDbKey(encryptedKeyValueStoreFactory, uuidGenerator)
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
        }

        override fun onCorruption(db: SupportSQLiteDatabase) {
          // By default, if the db is corrupted, Android deletes the file.
          // https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/database/DefaultDatabaseErrorHandler.java#53
        }
      }

    val encryptedDriver =
      AndroidSqliteDriver(
        schema = dataBaseSchema,
        context = platformContext.appContext,
        name = dataBaseName,
        factory = encryptedFactory,
        callback = dbCallback
      )

    simpleQuery(encryptedDriver)

    val connectAttempt =
      Result.catching {
        val clearDriver =
          AndroidSqliteDriver(
            schema = dataBaseSchema,
            context = platformContext.appContext,
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
    encryptedDriver.executeQuery(null, "PRAGMA user_version;", { cursor ->
      QueryResult.Value(1)
    }, 0, {})
    encryptedDriver.close()
  }
}

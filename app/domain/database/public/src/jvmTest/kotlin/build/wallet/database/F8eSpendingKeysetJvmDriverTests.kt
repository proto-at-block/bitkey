package build.wallet.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.F8eSpendingKeysetPrivateWalletMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.HwSpendingPublicKeyMock
import build.wallet.f8e.F8eEnvironment
import build.wallet.sqldelight.SqlDriverFactory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.Properties

/**
 * Verifies that the JVM SQLDelight driver can serialize and deserialize [build.wallet.bitkey.f8e.F8eSpendingKeyset]
 * JSON via the column adapters wired in [build.wallet.database.BitkeyDatabaseProviderImpl].
 */
class F8eSpendingKeysetJvmDriverTests : FunSpec({

  val sqlDriverFactory = JvmInMemoryDriverFactory()

  listOf(
    "private wallet" to F8eSpendingKeysetPrivateWalletMock,
    "standard wallet" to F8eSpendingKeysetMock
  ).forEach { (label, serverKey) ->
    test("round trip F8eSpendingKeyset ($label) through JVM driver") {
      val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriverFactory)
      val database = databaseProvider.database()

      val suffix = label.replace(" ", "-")
      val accountId = FullAccountId("jvm-f8e-account-$suffix")
      val keyboxId = "jvm-f8e-keybox-$suffix"
      val keysetId = "jvm-f8e-keyset-$suffix"

      database.fullAccountQueries.insertFullAccount(accountId)
      database.keyboxQueries.insertKeybox(
        id = keyboxId,
        accountId = accountId,
        networkType = BitcoinNetworkType.SIGNET,
        fakeHardware = false,
        f8eEnvironment = F8eEnvironment.Development,
        isTestAccount = false,
        isUsingSocRecFakes = false,
        delayNotifyDuration = null,
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("hw-signature"),
        canUseKeyboxKeysets = true
      )

      database.spendingKeysetQueries.insertKeyset(
        id = keysetId,
        keyboxId = keyboxId,
        appKey = AppSpendingPublicKeyMock,
        hardwareKey = HwSpendingPublicKeyMock,
        serverKey = serverKey,
        isActive = true
      )

      val stored =
        database.spendingKeysetQueries.keysetById(keysetId).executeAsOne()

      stored.serverKey shouldBe serverKey
    }
  }
})

private class JvmInMemoryDriverFactory : SqlDriverFactory {
  override suspend fun createDriver(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ): SqlDriver {
    return JdbcSqliteDriver(
      JdbcSqliteDriver.IN_MEMORY,
      Properties().apply { put("foreign_keys", "true") }
    ).also { driver ->
      dataBaseSchema.create(driver)
    }
  }
}

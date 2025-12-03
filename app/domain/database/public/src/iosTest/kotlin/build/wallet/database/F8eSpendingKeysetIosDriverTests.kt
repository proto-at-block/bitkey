package build.wallet.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.inMemoryDriver
import bitkey.account.HardwareType
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

/**
 * Verifies that the native iOS SQLDelight driver can serialize and deserialize [build.wallet.bitkey.f8e.F8eSpendingKeyset]
 * JSON via the column adapters wired in [build.wallet.database.BitkeyDatabaseProviderImpl].
 */
class F8eSpendingKeysetIosDriverTests : FunSpec({

  val sqlDriverFactory = NativeInMemoryDriverFactory()

  listOf(
    "private wallet" to F8eSpendingKeysetPrivateWalletMock,
    "standard wallet" to F8eSpendingKeysetMock
  ).forEach { (label, serverKey) ->
    test("round trip F8eSpendingKeyset ($label) through native driver") {
      val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriverFactory)
      val database = databaseProvider.database()

      val suffix = label.replace(" ", "-")
      val accountId = FullAccountId("ios-f8e-account-$suffix")
      val keyboxId = "ios-f8e-keybox-$suffix"
      val keysetId = "ios-f8e-keyset-$suffix"

      database.fullAccountQueries.insertFullAccount(accountId)
      database.keyboxQueries.insertKeybox(
        id = keyboxId,
        accountId = accountId,
        networkType = BitcoinNetworkType.SIGNET,
        fakeHardware = false,
        hardwareType = HardwareType.W1,
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

private class NativeInMemoryDriverFactory : SqlDriverFactory {
  override suspend fun createDriver(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ): SqlDriver {
    return inMemoryDriver(dataBaseSchema)
      .also { driver ->
        driver.execute(identifier = null, sql = "PRAGMA foreign_keys=ON", parameters = 0)
      }
  }
}

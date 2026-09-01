package build.wallet.money.display

import app.cash.turbine.test
import build.wallet.coroutines.createBackgroundScope
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe

class BitcoinDisplayPreferenceRepositoryImplTests : FunSpec({
  lateinit var bitcoinDisplayPreferenceDao: BitcoinDisplayPreferenceDao

  suspend fun TestScope.repository(): BitcoinDisplayPreferenceRepositoryImpl {
    val sqlDriver = inMemorySqlDriver()
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    bitcoinDisplayPreferenceDao = BitcoinDisplayPreferenceDaoImpl(databaseProvider)
    return BitcoinDisplayPreferenceRepositoryImpl(
      appScope = createBackgroundScope(),
      bitcoinDisplayPreferenceDao = bitcoinDisplayPreferenceDao
    )
  }

  test("bitcoin display defaults to Bitcoin and returns dao value") {
    val repository = repository()
    repository.bitcoinDisplayUnit.test {
      awaitItem().shouldBe(BitcoinDisplayUnit.Bitcoin)
      bitcoinDisplayPreferenceDao.setBitcoinDisplayPreference(BitcoinDisplayUnit.Satoshi)
      awaitItem().shouldBe(BitcoinDisplayUnit.Satoshi)
    }
  }

  test("set bitcoin display calls dao") {
    val repository = repository()
    repository.setBitcoinDisplayUnit(BitcoinDisplayUnit.Satoshi)
    bitcoinDisplayPreferenceDao.bitcoinDisplayPreference().test {
      awaitItem().shouldBe(BitcoinDisplayUnit.Satoshi)
    }
  }

  test("clear calls dao") {
    val repository = repository()
    repository.clear()
    bitcoinDisplayPreferenceDao.bitcoinDisplayPreference().test {
      awaitItem().shouldBe(null)
    }
  }
})

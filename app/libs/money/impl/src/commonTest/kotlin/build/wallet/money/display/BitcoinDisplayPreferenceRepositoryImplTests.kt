package build.wallet.money.display

import app.cash.turbine.test
import build.wallet.coroutines.createBackgroundScope
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.DefaultBitcoinDisplayUnitFeatureFlag
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe

class BitcoinDisplayPreferenceRepositoryImplTests : FunSpec({
  lateinit var bitcoinDisplayPreferenceDao: BitcoinDisplayPreferenceDao
  val featureFlagDao = FeatureFlagDaoFake()

  suspend fun TestScope.repository(
    defaultUnit: String = "SATOSHI",
  ): BitcoinDisplayPreferenceRepositoryImpl {
    val sqlDriver = inMemorySqlDriver()
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    bitcoinDisplayPreferenceDao = BitcoinDisplayPreferenceDaoImpl(databaseProvider)
    val flag = DefaultBitcoinDisplayUnitFeatureFlag(featureFlagDao)
    flag.setFlagValue(FeatureFlagValue.StringFlag(defaultUnit))
    return BitcoinDisplayPreferenceRepositoryImpl(
      appScope = createBackgroundScope(),
      bitcoinDisplayPreferenceDao = bitcoinDisplayPreferenceDao,
      defaultBitcoinDisplayUnitFeatureFlag = flag
    )
  }

  test("bitcoin display defaults to Satoshi and returns dao value") {
    val repository = repository()
    repository.bitcoinDisplayUnit.test {
      awaitItem().shouldBe(BitcoinDisplayUnit.Satoshi)
      bitcoinDisplayPreferenceDao.setBitcoinDisplayPreference(BitcoinDisplayUnit.Bitcoin)
      awaitItem().shouldBe(BitcoinDisplayUnit.Bitcoin)
    }
  }

  test("bitcoin display defaults to Bitcoin when flag set to BITCOIN") {
    val repository = repository(defaultUnit = "BITCOIN")
    repository.bitcoinDisplayUnit.test {
      // stateIn seed is Satoshi; combine resolves to Bitcoin from the flag.
      // Depending on timing the first Turbine item may be either value.
      val first = awaitItem()
      if (first != BitcoinDisplayUnit.Bitcoin) {
        awaitItem().shouldBe(BitcoinDisplayUnit.Bitcoin)
      }
      bitcoinDisplayPreferenceDao.setBitcoinDisplayPreference(BitcoinDisplayUnit.Satoshi)
      awaitItem().shouldBe(BitcoinDisplayUnit.Satoshi)
    }
  }

  test("bitcoin display defaults to Satoshi for unrecognized flag value") {
    val repository = repository(defaultUnit = "INVALID")
    repository.bitcoinDisplayUnit.test {
      awaitItem().shouldBe(BitcoinDisplayUnit.Satoshi)
    }
  }

  test("set bitcoin display calls dao") {
    val repository = repository()
    repository.setBitcoinDisplayUnit(BitcoinDisplayUnit.Bitcoin)
    bitcoinDisplayPreferenceDao.bitcoinDisplayPreference().test {
      awaitItem().shouldBe(BitcoinDisplayUnit.Bitcoin)
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

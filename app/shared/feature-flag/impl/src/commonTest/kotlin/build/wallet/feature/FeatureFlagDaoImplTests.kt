package build.wallet.feature

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FeatureFlagDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  val flagId = "flag-id"

  lateinit var dao: FeatureFlagDao

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = FeatureFlagDaoImpl(databaseProvider)
  }

  test("getFlag and setFlag for BooleanFlag") {
    dao.getFlag(flagId, kClass = BooleanFlag::class)
      .shouldBe(Ok(null))

    dao.setFlag(BooleanFlag(value = true), flagId)

    dao.getFlag(flagId, kClass = BooleanFlag::class)
      .shouldBe(Ok(BooleanFlag(value = true)))
  }
})

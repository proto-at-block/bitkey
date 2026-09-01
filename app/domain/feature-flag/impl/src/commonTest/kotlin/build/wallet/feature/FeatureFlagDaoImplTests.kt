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

  test("getFlags returns all persisted flag types") {
    dao.setFlag(BooleanFlag(value = true), "boolean-flag")
    dao.setFlag(FeatureFlagValue.DoubleFlag(value = 2.1), "double-flag")
    dao.setFlag(FeatureFlagValue.StringFlag(value = "value"), "string-flag")

    dao.getFlags().shouldBe(
      Ok(
        mapOf(
          "boolean-flag" to BooleanFlag(value = true),
          "double-flag" to FeatureFlagValue.DoubleFlag(value = 2.1),
          "string-flag" to FeatureFlagValue.StringFlag(value = "value")
        )
      )
    )
  }

  test("setFlag replaces a persisted value when the flag type changes") {
    dao.setFlag(BooleanFlag(value = true), flagId)
    dao.setFlag(FeatureFlagValue.DoubleFlag(value = 2.1), flagId)

    dao.getFlag(flagId, BooleanFlag::class).shouldBe(Ok(null))
    dao.getFlags().shouldBe(
      Ok(mapOf(flagId to FeatureFlagValue.DoubleFlag(value = 2.1)))
    )

    dao.setFlag(FeatureFlagValue.StringFlag(value = "value"), flagId)

    dao.getFlag(flagId, FeatureFlagValue.DoubleFlag::class).shouldBe(Ok(null))
    dao.getFlags().shouldBe(
      Ok(mapOf(flagId to FeatureFlagValue.StringFlag(value = "value")))
    )

    dao.setFlag(BooleanFlag(value = false), flagId)

    dao.getFlag(flagId, FeatureFlagValue.StringFlag::class).shouldBe(Ok(null))
    dao.getFlags().shouldBe(
      Ok(mapOf(flagId to BooleanFlag(value = false)))
    )
  }

  test("getFlagOverridden and setFlagOverridden") {
    // Should be false when flag is not in the database.
    dao.getFlagOverridden(flagId)
      .shouldBe(Ok(false))

    dao.setFlagOverridden(flagId, true)
      .shouldBe(Ok(Unit))

    // Should be true when flag is set to true.
    dao.getFlagOverridden(flagId)
      .shouldBe(Ok(true))

    dao.setFlagOverridden(flagId, false)
      .shouldBe(Ok(Unit))

    // Should be false when flag is set to false.
    dao.getFlagOverridden(flagId)
      .shouldBe(Ok(false))
  }
})

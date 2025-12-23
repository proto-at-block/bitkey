package build.wallet.feature.flags

import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.platform.config.AppVariant.Alpha
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Emergency
import build.wallet.platform.config.AppVariant.Team
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AgeRangeVerificationFeatureFlagTests : FunSpec({
  val featureFlagDao = FeatureFlagDaoFake()

  beforeTest {
    featureFlagDao.reset()
  }

  test("defaults to true for Development variant") {
    val flag = AgeRangeVerificationFeatureFlag(featureFlagDao, Development)
    flag.defaultFlagValue.value.shouldBe(true)
  }

  test("defaults to true for Team variant") {
    val flag = AgeRangeVerificationFeatureFlag(featureFlagDao, Team)
    flag.defaultFlagValue.value.shouldBe(true)
  }

  test("defaults to true for Alpha variant") {
    val flag = AgeRangeVerificationFeatureFlag(featureFlagDao, Alpha)
    flag.defaultFlagValue.value.shouldBe(true)
  }

  test("defaults to false for Customer variant") {
    val flag = AgeRangeVerificationFeatureFlag(featureFlagDao, Customer)
    flag.defaultFlagValue.value.shouldBe(false)
  }

  test("defaults to false for Emergency variant") {
    val flag = AgeRangeVerificationFeatureFlag(featureFlagDao, Emergency)
    flag.defaultFlagValue.value.shouldBe(false)
  }
})

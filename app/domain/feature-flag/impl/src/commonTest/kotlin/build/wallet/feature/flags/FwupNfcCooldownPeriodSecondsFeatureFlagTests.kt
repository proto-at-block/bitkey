package build.wallet.feature.flags

import build.wallet.feature.FeatureFlagDaoFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FwupNfcCooldownPeriodSecondsFeatureFlagTests : FunSpec({
  val featureFlagDao = FeatureFlagDaoFake()

  beforeTest {
    featureFlagDao.reset()
  }

  test("defaults to 8 seconds") {
    val flag = FwupNfcCooldownPeriodSecondsFeatureFlag(featureFlagDao)
    flag.defaultFlagValue.value.shouldBe(8.0)
  }
})

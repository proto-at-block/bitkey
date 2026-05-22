package build.wallet.feature.flags

import build.wallet.feature.FeatureFlagDaoFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WipeOldW1DeviceFeatureFlagTests : FunSpec({
  val featureFlagDao = FeatureFlagDaoFake()

  beforeTest {
    featureFlagDao.reset()
  }

  test("defaults to false") {
    val flag = WipeOldW1DeviceFeatureFlag(featureFlagDao)
    flag.defaultFlagValue.value.shouldBe(false)
  }
})

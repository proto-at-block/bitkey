package build.wallet.feature.flags

import build.wallet.feature.FeatureFlagDaoFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OnboardingCanUseKeyboxKeysetsFeatureFlagTests : FunSpec({
  val featureFlagDao = FeatureFlagDaoFake()

  beforeTest {
    featureFlagDao.reset()
  }

  test("defaults to true") {
    val flag = OnboardingCanUseKeyboxKeysetsFeatureFlag(featureFlagDao)
    flag.defaultFlagValue.value.shouldBe(true)
  }
})

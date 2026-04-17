package build.wallet.feature.flags

import build.wallet.feature.FeatureFlagDaoFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlagTests : FunSpec({
  val featureFlagDao = FeatureFlagDaoFake()

  beforeTest {
    featureFlagDao.reset()
  }

  test("defaults to 500 milliseconds") {
    val flag = FwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag(featureFlagDao)
    flag.defaultFlagValue.value.shouldBe(500.0)
  }
})

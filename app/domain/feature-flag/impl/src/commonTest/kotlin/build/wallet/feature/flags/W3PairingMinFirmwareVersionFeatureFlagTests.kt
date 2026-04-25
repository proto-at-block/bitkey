package build.wallet.feature.flags

import build.wallet.feature.FeatureFlagDaoFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class W3PairingMinFirmwareVersionFeatureFlagTests : FunSpec({
  val featureFlagDao = FeatureFlagDaoFake()

  beforeTest {
    featureFlagDao.reset()
  }

  test("defaults to 1.2.0") {
    val flag = W3PairingMinFirmwareVersionFeatureFlag(featureFlagDao)
    flag.defaultFlagValue.value.shouldBe("1.2.0")
  }
})

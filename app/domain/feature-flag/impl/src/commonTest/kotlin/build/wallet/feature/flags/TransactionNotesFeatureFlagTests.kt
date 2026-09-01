package build.wallet.feature.flags

import build.wallet.feature.FeatureFlagDaoFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TransactionNotesFeatureFlagTests : FunSpec({
  val featureFlagDao = FeatureFlagDaoFake()

  beforeTest {
    featureFlagDao.reset()
  }

  test("defaults to disabled") {
    val flag = TransactionNotesFeatureFlag(featureFlagDao)

    flag.defaultFlagValue.value.shouldBe(false)
  }
})

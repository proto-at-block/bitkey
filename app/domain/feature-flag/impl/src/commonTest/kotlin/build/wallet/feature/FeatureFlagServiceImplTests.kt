package build.wallet.feature

import app.cash.turbine.test
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagValue.DoubleFlag
import build.wallet.feature.FeatureFlagValue.StringFlag
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch

class FeatureFlagServiceImplTests : FunSpec({
  val featureFlagDao = FeatureFlagDaoFake()
  val featureFlagSyncer = FeatureFlagSyncerMock(turbines::create)
  val stringFlag = StringFlagMobileTestFeatureFlag(featureFlagDao = featureFlagDao)
  val doubleFlag = DoubleMobileTestFeatureFlag(featureFlagDao = featureFlagDao)
  val service = FeatureFlagServiceImpl(
    featureFlags = listOf(stringFlag, doubleFlag),
    featureFlagSyncer = featureFlagSyncer
  )

  beforeTest {
    featureFlagDao.reset()
  }

  test("service returns feature flags") {
    service.getFeatureFlags().shouldContainExactly(stringFlag, doubleFlag)
  }

  test("feature flags are not initialized by default") {
    service.flagsInitialized.value.shouldBeFalse()
  }

  test("feature flags are marked as initialized after sync is kicked off") {
    service.flagsInitialized.test {
      awaitItem().shouldBeFalse()

      createBackgroundScope().launch {
        service.executeWork()
      }

      awaitItem().shouldBeTrue()
      featureFlagSyncer.initializeSyncLoopCalls.awaitItem()
      featureFlagSyncer.syncCalls.awaitItem()
    }
  }

  test("resetting all flags sets default values and kicks off remote sync") {
    // Set flag values to non-default values
    stringFlag.setFlagValue(StringFlag("new value"))
    doubleFlag.setFlagValue(DoubleFlag(value = 2.1))

    // Kick off sync
    createBackgroundScope().launch {
      service.executeWork()
    }
    featureFlagSyncer.initializeSyncLoopCalls.awaitItem()
    featureFlagSyncer.syncCalls.awaitItem()

    service.flagsInitialized.test {
      // Wait for flags to be initialized
      awaitUntil { it }
    }

    // Reset flags
    service.resetFlags().shouldBeOk()

    // Flags are set to default values
    stringFlag.flagValue().value.value.shouldBe("")
    doubleFlag.flagValue().value.value.shouldBe(0.0)
    featureFlagSyncer.syncCalls.awaitItem()
  }
})

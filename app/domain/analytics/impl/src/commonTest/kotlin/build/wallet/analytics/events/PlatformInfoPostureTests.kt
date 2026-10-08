package build.wallet.analytics.events

import build.wallet.analytics.v1.DeviceLockState.DEVICE_LOCK_STATE_DISABLED
import build.wallet.analytics.v1.DeviceLockState.DEVICE_LOCK_STATE_ENABLED
import build.wallet.analytics.v1.DeviceLockState.DEVICE_LOCK_STATE_UNSPECIFIED
import build.wallet.analytics.v1.EmulatorState.EMULATOR_STATE_DETECTED
import build.wallet.analytics.v1.EmulatorState.EMULATOR_STATE_NOT_DETECTED
import build.wallet.analytics.v1.StrongboxAvailability.STRONGBOX_AVAILABILITY_NOT_SUPPORTED
import build.wallet.analytics.v1.StrongboxAvailability.STRONGBOX_AVAILABILITY_SUPPORTED
import build.wallet.platform.device.DeviceLockState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PlatformInfoPostureTests : FunSpec({
  test("map device lock state") {
    analyticsDeviceLockState(DeviceLockState.Enabled) shouldBe DEVICE_LOCK_STATE_ENABLED
    analyticsDeviceLockState(DeviceLockState.Disabled) shouldBe DEVICE_LOCK_STATE_DISABLED
    analyticsDeviceLockState(DeviceLockState.Unknown) shouldBe DEVICE_LOCK_STATE_UNSPECIFIED
  }

  test("map emulator state") {
    emulatorState(true) shouldBe EMULATOR_STATE_DETECTED
    emulatorState(false) shouldBe EMULATOR_STATE_NOT_DETECTED
  }

  test("map Android StrongBox availability") {
    androidStrongboxAvailability(
      sdkInt = 28,
      hasStrongboxFeature = true
    ) shouldBe STRONGBOX_AVAILABILITY_SUPPORTED
    androidStrongboxAvailability(
      sdkInt = 27,
      hasStrongboxFeature = true
    ) shouldBe STRONGBOX_AVAILABILITY_NOT_SUPPORTED
    androidStrongboxAvailability(
      sdkInt = 28,
      hasStrongboxFeature = false
    ) shouldBe STRONGBOX_AVAILABILITY_NOT_SUPPORTED
  }
})

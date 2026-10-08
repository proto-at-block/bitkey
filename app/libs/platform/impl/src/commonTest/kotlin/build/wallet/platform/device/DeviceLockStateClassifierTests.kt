package build.wallet.platform.device

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DeviceLockStateClassifierTests : FunSpec({
  test("map Android secure state") {
    deviceLockStateFromIsDeviceSecure(true) shouldBe DeviceLockState.Enabled
    deviceLockStateFromIsDeviceSecure(false) shouldBe DeviceLockState.Disabled
    deviceLockStateFromIsDeviceSecure(null) shouldBe DeviceLockState.Unknown
  }

  test("map successful iOS policy evaluation to enabled") {
    deviceLockStateFromPolicyEvaluation(
      canEvaluate = true,
      errorDomain = null,
      errorCode = null,
      passcodeNotSetErrorDomain = "LAErrorDomain",
      passcodeNotSetErrorCode = 1L
    ) shouldBe DeviceLockState.Enabled
  }

  test("map iOS passcode not set error to disabled") {
    deviceLockStateFromPolicyEvaluation(
      canEvaluate = false,
      errorDomain = "LAErrorDomain",
      errorCode = 1L,
      passcodeNotSetErrorDomain = "LAErrorDomain",
      passcodeNotSetErrorCode = 1L
    ) shouldBe DeviceLockState.Disabled
  }

  test("map all other iOS policy evaluation errors to unknown") {
    deviceLockStateFromPolicyEvaluation(
      canEvaluate = false,
      errorDomain = "OtherDomain",
      errorCode = 1L,
      passcodeNotSetErrorDomain = "LAErrorDomain",
      passcodeNotSetErrorCode = 1L
    ) shouldBe DeviceLockState.Unknown

    deviceLockStateFromPolicyEvaluation(
      canEvaluate = false,
      errorDomain = "LAErrorDomain",
      errorCode = 2L,
      passcodeNotSetErrorDomain = "LAErrorDomain",
      passcodeNotSetErrorCode = 1L
    ) shouldBe DeviceLockState.Unknown

    deviceLockStateFromPolicyEvaluation(
      canEvaluate = false,
      errorDomain = null,
      errorCode = null,
      passcodeNotSetErrorDomain = "LAErrorDomain",
      passcodeNotSetErrorCode = 1L
    ) shouldBe DeviceLockState.Unknown
  }
})

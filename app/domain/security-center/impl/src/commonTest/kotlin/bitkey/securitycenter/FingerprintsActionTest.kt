package bitkey.securitycenter

import build.wallet.firmware.FirmwareDeviceInfoMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly

class FingerprintsActionTest : FunSpec({

  test("with zero fingerprints, no fingerprint task, and active HW - should return empty list") {
    val fingerprintsAction = FingerprintsAction(
      firmwareDeviceInfo = FirmwareDeviceInfoMock,
      fingerprintCount = 0,
      fingerprintResetReady = false
    )

    fingerprintsAction.getRecommendations().shouldBeEmpty()
  }

  test("with one fingerprint - should recommend adding more fingerprints") {
    val fingerprintsAction = FingerprintsAction(
      firmwareDeviceInfo = FirmwareDeviceInfoMock,
      fingerprintCount = 1,
      fingerprintResetReady = false
    )

    fingerprintsAction.getRecommendations()
      .shouldContainExactly(SecurityActionRecommendation.ADD_FINGERPRINTS)
  }

  test("with multiple fingerprints - should return empty list") {
    val fingerprintsAction = FingerprintsAction(
      firmwareDeviceInfo = FirmwareDeviceInfoMock,
      fingerprintCount = 2,
      fingerprintResetReady = false
    )

    fingerprintsAction.getRecommendations().shouldBeEmpty()

    val fingerprintsActionWithMore = FingerprintsAction(
      firmwareDeviceInfo = FirmwareDeviceInfoMock,
      fingerprintCount = 5,
      fingerprintResetReady = false
    )

    fingerprintsActionWithMore.getRecommendations().shouldBeEmpty()
  }

  test("with fingerprint reset ready - should recommend completing reset") {
    val fingerprintsAction = FingerprintsAction(
      firmwareDeviceInfo = FirmwareDeviceInfoMock,
      fingerprintCount = 1,
      fingerprintResetReady = true
    )

    fingerprintsAction.getRecommendations()
      .shouldContainExactly(
        SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET,
        SecurityActionRecommendation.ADD_FINGERPRINTS
      )
  }
})

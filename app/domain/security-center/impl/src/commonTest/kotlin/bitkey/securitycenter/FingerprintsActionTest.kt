package bitkey.securitycenter

import build.wallet.firmware.FirmwareDeviceInfoMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly

class FingerprintsActionTest : FunSpec({

  test("with zero fingerprints and active HW - should recommend adding fingerprints") {
    val fingerprintsAction = FingerprintsAction(
      firmwareDeviceInfo = FirmwareDeviceInfoMock,
      fingerprintCount = 0,
      fingerprintResetReady = false,
      isAppKeyProvisioned = true,
      isFingerprintResetEnabled = true
    )

    fingerprintsAction.getRecommendations()
      .shouldContainExactly(SecurityActionRecommendation.ADD_FINGERPRINTS)
  }

  test("with one fingerprint - should recommend adding more fingerprints") {
    val fingerprintsAction = FingerprintsAction(
      firmwareDeviceInfo = FirmwareDeviceInfoMock,
      fingerprintCount = 1,
      fingerprintResetReady = false,
      isAppKeyProvisioned = true,
      isFingerprintResetEnabled = true
    )

    fingerprintsAction.getRecommendations()
      .shouldContainExactly(SecurityActionRecommendation.ADD_FINGERPRINTS)
  }

  test("with multiple fingerprints - should return empty list") {
    val fingerprintsAction = FingerprintsAction(
      firmwareDeviceInfo = FirmwareDeviceInfoMock,
      fingerprintCount = 2,
      fingerprintResetReady = false,
      isAppKeyProvisioned = true,
      isFingerprintResetEnabled = true
    )

    fingerprintsAction.getRecommendations().shouldBeEmpty()

    val fingerprintsActionWithMore = FingerprintsAction(
      firmwareDeviceInfo = FirmwareDeviceInfoMock,
      fingerprintCount = 5,
      fingerprintResetReady = false,
      isAppKeyProvisioned = true,
      isFingerprintResetEnabled = true
    )

    fingerprintsActionWithMore.getRecommendations().shouldBeEmpty()
  }

  test("with fingerprint reset ready - should recommend completing reset") {
    val fingerprintsAction = FingerprintsAction(
      firmwareDeviceInfo = FirmwareDeviceInfoMock,
      fingerprintCount = 1,
      fingerprintResetReady = true,
      isAppKeyProvisioned = true,
      isFingerprintResetEnabled = true
    )

    fingerprintsAction.getRecommendations()
      .shouldContainExactly(
        SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET,
        SecurityActionRecommendation.ADD_FINGERPRINTS
      )
  }

  test("with app key not provisioned - should recommend provisioning") {
    val fingerprintsAction = FingerprintsAction(
      firmwareDeviceInfo = FirmwareDeviceInfoMock,
      fingerprintCount = 2,
      fingerprintResetReady = false,
      isAppKeyProvisioned = false,
      isFingerprintResetEnabled = true
    )

    fingerprintsAction.getRecommendations()
      .shouldContainExactly(SecurityActionRecommendation.PROVISION_APP_KEY_TO_HARDWARE)
  }

  test("with app key not provisioned but fingerprint reset disabled - should not recommend provisioning") {
    val fingerprintsAction = FingerprintsAction(
      firmwareDeviceInfo = FirmwareDeviceInfoMock,
      fingerprintCount = 2,
      fingerprintResetReady = false,
      isAppKeyProvisioned = false,
      isFingerprintResetEnabled = false
    )

    fingerprintsAction.getRecommendations().shouldBeEmpty()
  }

  test("with fingerprint reset disabled and app key not provisioned - should not recommend provisioning") {
    val fingerprintsAction = FingerprintsAction(
      firmwareDeviceInfo = FirmwareDeviceInfoMock,
      fingerprintCount = 2,
      fingerprintResetReady = false,
      isAppKeyProvisioned = false,
      isFingerprintResetEnabled = false
    )

    fingerprintsAction.getRecommendations().shouldBeEmpty()
  }

  test("with fingerprint reset enabled and app key not provisioned - should recommend provisioning") {
    val fingerprintsAction = FingerprintsAction(
      firmwareDeviceInfo = FirmwareDeviceInfoMock,
      fingerprintCount = 2,
      fingerprintResetReady = false,
      isAppKeyProvisioned = false,
      isFingerprintResetEnabled = true
    )

    fingerprintsAction.getRecommendations()
      .shouldContainExactly(SecurityActionRecommendation.PROVISION_APP_KEY_TO_HARDWARE)
  }
})

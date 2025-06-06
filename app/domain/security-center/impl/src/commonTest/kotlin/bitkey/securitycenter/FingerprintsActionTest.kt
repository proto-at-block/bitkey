package bitkey.securitycenter

import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.home.GettingStartedTask
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly

class FingerprintsActionTest : FunSpec({
  test("with zero fingerprints, no fingerprint task, and inactive HW - should recommend adding fingerprints") {
    val fingerprintsAction = FingerprintsAction(
      gettingStartedTasks = emptyList(),
      fingerprintCount = 0,
      firmwareDeviceInfo = null
    )

    fingerprintsAction.getRecommendations()
      .shouldContainExactly(SecurityActionRecommendation.ADD_FINGERPRINTS)
  }

  test("with zero fingerprints, no fingerprint task, and active HW - should return empty list") {
    val fingerprintsAction = FingerprintsAction(
      gettingStartedTasks = emptyList(),
      fingerprintCount = 0,
      firmwareDeviceInfo = FirmwareDeviceInfoMock
    )

    fingerprintsAction.getRecommendations().shouldBeEmpty()
  }

  test("with zero fingerprints and incomplete additional fingerprint task - should recommend adding fingerprints") {
    val additionalFingerprintTask = GettingStartedTask(
      id = GettingStartedTask.TaskId.AddAdditionalFingerprint,
      state = GettingStartedTask.TaskState.Incomplete
    )

    val fingerprintsAction = FingerprintsAction(
      gettingStartedTasks = listOf(additionalFingerprintTask),
      fingerprintCount = 0,
      firmwareDeviceInfo = FirmwareDeviceInfoMock
    )

    fingerprintsAction.getRecommendations()
      .shouldContainExactly(SecurityActionRecommendation.ADD_FINGERPRINTS)
  }

  test("with zero fingerprints and completed additional fingerprint task - should return empty list") {
    val additionalFingerprintTask = GettingStartedTask(
      id = GettingStartedTask.TaskId.AddAdditionalFingerprint,
      state = GettingStartedTask.TaskState.Complete
    )

    val fingerprintsAction = FingerprintsAction(
      gettingStartedTasks = listOf(additionalFingerprintTask),
      fingerprintCount = 0,
      firmwareDeviceInfo = FirmwareDeviceInfoMock
    )

    fingerprintsAction.getRecommendations().shouldBeEmpty()
  }

  test("with one fingerprint - should recommend adding more fingerprints") {
    val fingerprintsAction = FingerprintsAction(
      gettingStartedTasks = emptyList(),
      fingerprintCount = 1,
      firmwareDeviceInfo = FirmwareDeviceInfoMock
    )

    fingerprintsAction.getRecommendations()
      .shouldContainExactly(SecurityActionRecommendation.ADD_FINGERPRINTS)
  }

  test("with multiple fingerprints - should return empty list") {
    val fingerprintsAction = FingerprintsAction(
      gettingStartedTasks = emptyList(),
      fingerprintCount = 2,
      firmwareDeviceInfo = FirmwareDeviceInfoMock
    )

    fingerprintsAction.getRecommendations().shouldBeEmpty()

    val fingerprintsActionWithMore = FingerprintsAction(
      gettingStartedTasks = emptyList(),
      fingerprintCount = 5,
      firmwareDeviceInfo = FirmwareDeviceInfoMock
    )

    fingerprintsActionWithMore.getRecommendations().shouldBeEmpty()
  }
})

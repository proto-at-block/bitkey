package build.wallet.statemachine.fwup

import build.wallet.firmware.McuRole
import build.wallet.statemachine.fwup.FwupNfcBodyModel.Status.InProgress
import build.wallet.statemachine.fwup.FwupNfcBodyModel.Status.LostConnection
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FwupNfcBodyModelTests : FunSpec({

  // InProgress Status Text Tests

  test("InProgress text shows 'Updating...' for W1 single MCU") {
    val status = InProgress(
      currentMcuRole = McuRole.CORE,
      mcuIndex = 0,
      totalMcus = 1,
      fwupProgress = 50f
    )
    status.text.shouldBe("Updating...")
  }

  test("InProgress text shows '(1/2)' for W3 first MCU (CORE)") {
    val status = InProgress(
      currentMcuRole = McuRole.CORE,
      mcuIndex = 0,
      totalMcus = 2,
      fwupProgress = 50f
    )
    status.text.shouldBe("Updating (1/2)...")
  }

  test("InProgress text shows '(2/2)' for W3 second MCU (UXC)") {
    val status = InProgress(
      currentMcuRole = McuRole.UXC,
      mcuIndex = 1,
      totalMcus = 2,
      fwupProgress = 75f
    )
    status.text.shouldBe("Updating (2/2)...")
  }

  test("InProgress progressText shows percentage") {
    val status = InProgress(fwupProgress = 45.6f)
    status.progressText.shouldBe("46%")
  }

  test("InProgress progressPercentage is normalized 0-1") {
    val status = InProgress(fwupProgress = 50f)
    status.progressPercentage.shouldBe(0.5f)
  }

  // LostConnection Status Text Tests

  test("LostConnection text for W1 single MCU") {
    val status = LostConnection(
      currentMcuRole = McuRole.CORE,
      mcuIndex = 0,
      totalMcus = 1,
      fwupProgress = 30f
    )
    status.text.shouldBe("Device no longer detected,\nhold device to phone")
  }

  test("LostConnection text shows '(1/2)' for W3 first MCU") {
    val status = LostConnection(
      currentMcuRole = McuRole.CORE,
      mcuIndex = 0,
      totalMcus = 2,
      fwupProgress = 30f
    )
    status.text.shouldBe("Lost connection during update (1/2),\nhold device to phone")
  }

  test("LostConnection text shows '(2/2)' for W3 second MCU") {
    val status = LostConnection(
      currentMcuRole = McuRole.UXC,
      mcuIndex = 1,
      totalMcus = 2,
      fwupProgress = 60f
    )
    status.text.shouldBe("Lost connection during update (2/2),\nhold device to phone")
  }

  test("LostConnection progressPercentage is normalized 0-1") {
    val status = LostConnection(fwupProgress = 75f)
    status.progressPercentage.shouldBe(0.75f)
  }
})

package build.wallet.statemachine.core

import build.wallet.nfc.NfcException
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NfcErrorMessageTests : FunSpec({
  test("pairing firmware too old shows firmware update copy") {
    NfcErrorMessage.fromException(
      NfcException.PairingFirmwareTooOld(
        minimumVersion = "1.0.101",
        currentVersion = "1.0.100"
      )
    ).shouldBe(
      NfcErrorMessage(
        title = "Firmware update required",
        description = "This Bitkey can't be paired until it's updated to firmware version 1.0.101 or later."
      )
    )
  }
})

package build.wallet.statemachine.data.robots

import app.cash.turbine.ReceiveTurbine
import build.wallet.statemachine.data.nfc.NfcCommandData
import build.wallet.statemachine.data.nfc.NfcCommandData.ExecutingNfcCommandData
import build.wallet.statemachine.data.nfc.NfcCommandData.NfcCommandExecutedData
import io.kotest.matchers.types.shouldBeTypeOf

/**
 * Successfully completes NFC command.
 */
suspend inline fun ReceiveTurbine<NfcCommandData>.completeNfcCommandRobot() {
  awaitItem()
    .shouldBeTypeOf<ExecutingNfcCommandData>()

  awaitItem()
    .shouldBeTypeOf<NfcCommandExecutedData>()
    .proceed()
}

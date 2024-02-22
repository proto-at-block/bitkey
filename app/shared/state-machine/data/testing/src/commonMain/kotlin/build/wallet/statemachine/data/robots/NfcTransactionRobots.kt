package build.wallet.statemachine.data.robots

import app.cash.turbine.ReceiveTurbine
import build.wallet.nfc.transaction.NfcTransaction
import io.kotest.matchers.types.shouldBeTypeOf

/**
 * Successfully completes NFC transaction.
 */
suspend inline fun <T> ReceiveTurbine<NfcTransaction<T>>.completeNfcTransactionRobot(response: T) {
  awaitItem()
    .shouldBeTypeOf<NfcTransaction<T>>()
    .onSuccess(response)
}

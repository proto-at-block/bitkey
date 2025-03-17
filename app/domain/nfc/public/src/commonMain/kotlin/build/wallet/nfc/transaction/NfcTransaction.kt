package build.wallet.nfc.transaction

import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands

/**
 * A transaction is a single unit of work that can be performed within a particular session.
 *
 * They (optionally) created by state machines and included on the [NfcSessionUIStateMachineProps]
 * that is passed to the [NfcSessionUIStateMachine] that handles all NFC transactions.
 */
interface NfcTransaction<T> {
  /** Whether or not the transaction requires the device to first be unlocked */
  val needsAuthentication: Boolean

  /** Whether or not the hardware should be locked when the transaction completes */
  val shouldLock: Boolean

  suspend fun session(
    session: NfcSession,
    commands: NfcCommands,
  ): T

  suspend fun onSuccess(response: T)

  fun onCancel()
}

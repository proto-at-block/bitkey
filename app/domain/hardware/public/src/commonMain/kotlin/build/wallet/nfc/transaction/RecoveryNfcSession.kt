package build.wallet.nfc.transaction

import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands

/**
 * Abstraction over NFC session types for recovery completion taps.
 *
 * W1 hardware uses [Standard] with [NfcTransaction], while W3 hardware uses [Confirmable]
 * with [HardwareInteraction]-returning sessions that support two-tap confirmation.
 */
sealed interface RecoveryNfcSession {
  /** Standard NFC transaction (W1). UI uses NfcSessionUIStateMachine. */
  data class Standard<T>(val transaction: NfcTransaction<T>) : RecoveryNfcSession

  /** Confirmable NFC session (W3, two-tap). UI uses NfcConfirmableSessionUiStateMachine. */
  class Confirmable<T>(
    val session: suspend (NfcSession, NfcCommands) -> HardwareInteraction<T>,
    val onSuccess: suspend (T) -> Unit,
    val onCancel: () -> Unit,
  ) : RecoveryNfcSession
}

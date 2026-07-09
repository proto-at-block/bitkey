package bitkey.privilegedactions

import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import build.wallet.coroutines.flow.ConfirmationFlow
import com.github.michaelbull.result.Result

/**
 * Service for querying account-scoped hardware-verification privileged actions.
 */
interface HardwareVerificationPrivilegedActionService {
  /**
   * Fetch the current pending VerifyHardwareSerial privileged action, if one exists.
   */
  suspend fun getPendingHardwareVerificationAction(): Result<PrivilegedActionInstance?, PrivilegedActionError>

  /**
   * Poll for the resolution of the pending VerifyHardwareSerial privileged action.
   *
   * Emits [build.wallet.coroutines.flow.ConfirmationState.Pending] while a
   * VerifyHardwareSerial action is still pending on the server (or while an
   * unexpected error occurs), and a terminal
   * [build.wallet.coroutines.flow.ConfirmationState.Complete] once no pending
   * action remains.
   *
   * IMPORTANT: a terminal emission does NOT mean the hardware was successfully
   * verified. The server removes the priv-action on any of: Confirm (keyset
   * promoted to Verified), Cancel, or attempt exhaustion. The keyset's
   * `attested_hardware_serial` only transitions to Verified on the Confirm
   * path; the Cancel and exhaustion paths leave it Pending and a fresh
   * VerifyHardwareSerial will be required.
   *
   * Callers should therefore treat terminal emissions as "the OOBA flow is
   * over, re-attempt the underlying operation" — the next signing/sweep
   * attempt will either succeed against the now-verified keyset or surface
   * the hardware-attestation error and start a new OOBA.
   */
  fun pollPendingHardwareVerificationAction(): ConfirmationFlow<Unit>
}

package build.wallet.nfc.interceptors

import bitkey.account.HardwareType
import bitkey.recovery.RecoveryStatusService
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.catchingResult
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.logging.NFC_TAG
import build.wallet.logging.logWarn
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcException.FeatureNotSupported
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSession.RequirePairedHardware
import build.wallet.nfc.haptics.NfcHaptics
import build.wallet.nfc.platform.NfcCommands
import build.wallet.recovery.Recovery
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Sets the message to "Success" upon a successful transaction.
 */
internal fun iosMessages() =
  NfcTransactionInterceptor { next ->
    { session, commands ->
      session.message = "Connected"
      next(session, commands).also {
        session.message = "Success"
      }
    }
  }

/**
 * Logs the start of an NFC session.
 */
internal fun sessionLogger() =
  NfcTransactionInterceptor { next ->
    { session, commands ->
      catchingResult { next(session, commands) }
        .onFailure { logWarn(tag = NFC_TAG, throwable = it) { "NFC Session Error" } }
        .getOrThrow()
    }
  }

/**
 * Vibrates the phone software upon a successful transaction,
 * and vibrates more violently upon a failed transaction.
 */
internal fun haptics(nfcHaptics: NfcHaptics) =
  NfcTransactionInterceptor { next ->
    { session, commands ->
      session.parameters.onTagConnectedObservers += { nfcHaptics.vibrateConnection() }
      catchingResult { next(session, commands) }
        .onSuccess { nfcHaptics.vibrateSuccess() }
        .onFailure { nfcHaptics.vibrateFailure() }
        .getOrThrow()
    }
  }

/**
 * Adds a timeout to the NFC session.
 *
 * @param timeout The timeout to use. (defaults to 60 seconds)
 */
internal fun timeoutSession(
  @Suppress("UNUSED_PARAMETER") timeout: Duration = 60.seconds,
) = NfcTransactionInterceptor { next ->
  { session, commands ->
    // iOS both does its own timeout *and* blocks, despite claiming to be suspend
    // [W-5082]: Disabled due to toxic reaction with integration tests!
    // withTimeoutThrowing(timeout) {
    next(session, commands)
    // }
  }
}

/**
 * Shows a confirmation screen on W3 after a successful transaction, when
 * [NfcSession.Parameters.showDeviceConfirmation] is true.
 * Best-effort: older firmware may not support this command, so errors are ignored.
 */
internal fun showConfirmation() =
  NfcTransactionInterceptor { next ->
    { session, commands ->
      val result = next(session, commands)
      if (
        session.parameters.showDeviceConfirmation &&
        session.parameters.hardwareType == HardwareType.W3
      ) {
        runCatching {
          commands.showConfirmationScreen(
            session = session,
            lockOnDismiss = session.parameters.shouldLock
          )
        }
          .onFailure {
            logWarn(tag = NFC_TAG, throwable = it) { "Failed to show device confirmation screen" }
          }
      }
      result
    }
  }

/**
 * Locks the device after any transaction that wasn't cancelled or invalidated.
 */
internal fun lockDevice() =
  NfcTransactionInterceptor { next ->
    { session, commands ->
      catchingResult { next(session, commands) }
        .onFailure {
          // An NfcException indicates the session is almost certainly invalidated
          if (it is NfcException) return@onFailure
          // Hello Future Us.
          // If this is throwing and impacting a successful transaction, put it in a finally.
          // It'll be fine.
          maybeLockDevice(session, commands)
        }.onSuccess { maybeLockDevice(session, commands) }
        .getOrThrow()
    }
  }

private suspend fun maybeLockDevice(
  session: NfcSession,
  commands: NfcCommands,
) {
  // Don't lock on W3 since W3 taps require confirmation, and the user can lock W3 themselves.
  if (session.parameters.shouldLock && session.parameters.hardwareType != HardwareType.W3) {
    commands.lockDevice(session)
  }
}

/**
 * An interceptor that validates the tapped hardware is paired with the current account.
 *
 * The verification strategy differs by hardware type:
 * - **W1**: Signs a random challenge with the hardware auth key and delegates signature
 *   verification to the callback in [RequirePairedHardware.Required].
 * - **W3**: Compares the serial number reported by [getDeviceInfo] against the serial
 *   stored in [FirmwareDeviceInfoDao]. The Delay+Notify guard is handled separately by
 *   [rejectDuringHardwareDelayNotify].
 *
 * @param firmwareDeviceInfoDao DAO used for the W3 serial comparison path.
 */
internal fun validateHardwareIsPaired(firmwareDeviceInfoDao: FirmwareDeviceInfoDao) =
  NfcTransactionInterceptor { next ->
    { session, commands ->
      val requiresPairedHardware = session.parameters.requirePairedHardware
      if (requiresPairedHardware is RequirePairedHardware.Required) {
        when (session.parameters.hardwareType) {
          HardwareType.W3 -> {
            // Compare serial from the tapped device against the stored paired serial.
            // This runs before collectFirmwareTelemetry, so the DAO still holds the
            // previously-paired device's serial (not the just-tapped device's).
            val expectedSerial = firmwareDeviceInfoDao.getDeviceInfo().get()?.serial
            val actualSerial = commands.getDeviceInfo(session).serial
            if (expectedSerial == null || actualSerial != expectedSerial) {
              throw NfcException.UnpairedHardwareError()
            }
          }
          HardwareType.W1 -> {
            // W1 / unknown: sign a random challenge and verify the signature.
            val challenge = requiresPairedHardware.challenge
            val signature = try {
              commands.signChallenge(session, challenge)
            } catch (e: FeatureNotSupported) {
              throw NfcException.UnpairedHardwareError(cause = e)
            }

            val challengeSuccessful =
              requiresPairedHardware.checkHardwareIsPaired(signature, challenge)
            if (!challengeSuccessful) {
              throw NfcException.UnpairedHardwareError()
            }
          }
          null -> {
            // no-op
          }
        }
      }

      next(session, commands)
    }
  }

/**
 * Rejects W3 NFC taps during a hardware-factor Delay+Notify waiting period by throwing
 * [NfcException.HardwareReplacementPendingError].
 *
 * This is a separate interceptor from [validateHardwareIsPaired] so that the D+N gate can be
 * independently bypassed for specific flows (e.g. lost-hardware cancellation PoP) without
 * affecting the general pairing check. When [NfcSession.Parameters.skipLostHardwareCheck] is
 * true, this interceptor is a no-op.
 */
internal fun rejectDuringHardwareDelayNotify(
  recoveryStatusService: RecoveryStatusService,
  clock: Clock = Clock.System,
) = NfcTransactionInterceptor { next ->
  { session, commands ->
    if (
      !session.parameters.skipLostHardwareCheck &&
      session.parameters.hardwareType == HardwareType.W3 &&
      isHardwareDelayNotifyPending(recoveryStatusService, clock)
    ) {
      throw NfcException.HardwareReplacementPendingError()
    }
    next(session, commands)
  }
}

/**
 * Returns true if there is an active hardware-factor Delay+Notify waiting period.
 *
 * This is scoped to [PhysicalFactor.Hardware] only. Lost-app recovery also uses
 * [Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery] but requires
 * paired-hardware NFC taps (e.g. for cancellation), so that factor must not be blocked.
 *
 * Reading [RecoveryStatusService.status] is free — it is an in-memory [StateFlow].
 */
internal fun isHardwareDelayNotifyPending(
  recoveryStatusService: RecoveryStatusService,
  clock: Clock,
): Boolean {
  val recovery = recoveryStatusService.status.value
  return recovery is Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery &&
    recovery.factorToRecover == PhysicalFactor.Hardware &&
    clock.now() < recovery.serverRecovery.delayEndTime
}

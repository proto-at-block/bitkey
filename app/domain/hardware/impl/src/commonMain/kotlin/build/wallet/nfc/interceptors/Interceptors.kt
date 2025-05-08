package build.wallet.nfc.interceptors

import build.wallet.catchingResult
import build.wallet.logging.*
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSession.RequirePairedHardware
import build.wallet.nfc.haptics.NfcHaptics
import build.wallet.nfc.platform.NfcCommands
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
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
  if (session.parameters.shouldLock) {
    commands.lockDevice(session)
  }
}

/**
 * An interceptor that asks the hardware to sign a random challenge and delegates the verification
 * of said challenge to the callback provided in the session parameters.
 */
internal fun validateHardwareIsPaired() =
  NfcTransactionInterceptor { next ->
    { session, commands ->
      val requiresPairedHardware = session.parameters.requirePairedHardware
      if (requiresPairedHardware is RequirePairedHardware.Required) {
        // Generate a 32-byte random challenge
        val challenge = requiresPairedHardware.challenge
        val signature = commands.signChallenge(session, challenge)

        // Delegate back to the nfc session to confirm this hardware is paired; we do this
        // to keep the interceptor relatively simple.
        val challengeSuccessful = requiresPairedHardware.checkHardwareIsPaired(signature, challenge)
        if (!challengeSuccessful) {
          throw NfcException.UnpairedHardwareError()
        }
      }

      next(session, commands)
    }
  }

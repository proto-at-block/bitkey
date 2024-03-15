package build.wallet.nfc.transaction

import build.wallet.firmware.FirmwareCertType
import build.wallet.firmware.HardwareAttestation
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands

class StartFingerprintEnrollmentTransactionProviderImpl(
  private val hardwareAttestation: HardwareAttestation,
) : StartFingerprintEnrollmentTransactionProvider {
  override fun invoke(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    isHardwareFake: Boolean,
  ) = object : NfcTransaction<Boolean> {
    override val isHardwareFake = isHardwareFake
    override val needsAuthentication = false
    override val shouldLock = false

    override suspend fun session(
      session: NfcSession,
      commands: NfcCommands,
    ): Boolean {
      // Hardware attestation occurs before doing anything else.
      if (!isHardwareFake) {
        attestAndRecordSerial(session, commands)
      }

      return commands.startFingerprintEnrollment(session)
    }

    override fun onCancel() = onCancel()

    override suspend fun onSuccess(response: Boolean) = onSuccess()
  }

  @Suppress("ThrowsCount")
  private suspend fun attestAndRecordSerial(
    session: NfcSession,
    commands: NfcCommands,
  ) {
    // Don't put these calls in the runCatching below, because if NFC flakes, we don't want to
    // propagate that as InauthenticHardware
    val identityCert = commands.getCert(session, FirmwareCertType.IDENTITY)
    val batchCert = commands.getCert(session, FirmwareCertType.BATCH)

    // TODO(W-6318): Make these exceptions again.

    // NOTE: Do not remove '[hardware_attestation_failure]' from the message. We alert
    // on this string in Datadog.
    val serial =
      Result.runCatching {
        hardwareAttestation.verifyCertChain(
          identityCert = identityCert,
          batchCert = batchCert
        )
      }.getOrElse {
        log(LogLevel.Warn) { "[hardware_attestation_failure] Failed to verify cert chain" }
        return
      }

    val challenge =
      Result.runCatching {
        hardwareAttestation.generateChallenge()
      }.getOrElse {
        log(LogLevel.Warn) { "[hardware_attestation_failure] Failed to generate challenge for $serial " }
        return
      }

    Result.runCatching {
      require(
        commands.signVerifyAttestationChallenge(
          session,
          identityCert,
          challenge
        )
      )
    }.getOrElse {
      // TODO(W-6045): Don't look at the message string.
      if (it.cause?.message?.contains("signature invalid") == true) {
        log(LogLevel.Warn) { "[hardware_attestation_failure] Failed to verify challenge for $serial " }
        return
      } else {
        log(LogLevel.Warn) { "[hardware_attestation_failure] NFC flaked or firmware does not support attestation; allowing anyway... for now! Serial: $serial" }
        return
      }
    }

    log { "Hardware attestation successful: $serial" }
  }
}

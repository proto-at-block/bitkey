package build.wallet.nfc.transaction

import build.wallet.firmware.FirmwareCertType
import build.wallet.firmware.HardwareAttestation
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
    val identityCert = commands.getCert(session, FirmwareCertType.IDENTITY)

    val serial =
      Result.runCatching {
        hardwareAttestation.verifyCertChain(
          identityCert = identityCert,
          batchCert = commands.getCert(session, FirmwareCertType.BATCH)
        )
      }.getOrElse {
        throw NfcException.InauthenticHardware(
          message = "Failed to verify cert chain",
          cause = it
        )
      }

    val challenge =
      Result.runCatching {
        hardwareAttestation.generateChallenge()
      }.getOrElse {
        throw NfcException.InauthenticHardware(
          message = "Failed to generate challenge for $serial",
          cause = it
        )
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
      throw NfcException.InauthenticHardware(
        message = "Failed to verify challenge for $serial",
        cause = it
      )
    }

    log { "Hardware attestation successful: $serial" }
  }
}

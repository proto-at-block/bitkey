package build.wallet.nfc.transaction

import bitkey.account.AccountConfigService
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.catchingResult
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.csek.SekGenerator
import build.wallet.cloud.backup.csek.Ssek
import build.wallet.cloud.backup.csek.SsekDao
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FingerprintEnrollmentStatus.*
import build.wallet.firmware.FirmwareCertType
import build.wallet.firmware.HardwareAttestation
import build.wallet.logging.logDebug
import build.wallet.logging.logWarn
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.signChallenge
import build.wallet.nfc.transaction.PairingTransactionResponse.*
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.getOrThrow

@BitkeyInject(AppScope::class)
class PairingTransactionProviderImpl(
  private val sekGenerator: SekGenerator,
  private val csekDao: CsekDao,
  private val ssekDao: SsekDao,
  private val uuidGenerator: UuidGenerator,
  private val appInstallationDao: AppInstallationDao,
  private val hardwareAttestation: HardwareAttestation,
  private val accountConfigService: AccountConfigService,
) : PairingTransactionProvider {
  override operator fun invoke(
    appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    onSuccess: (PairingTransactionResponse) -> Unit,
    onCancel: () -> Unit,
  ) = object : NfcTransaction<PairingTransactionResponse> {
    private lateinit var unsealedCsek: Csek
    private lateinit var unsealedSsek: Ssek

    override val needsAuthentication = false
    override val shouldLock = true

    override suspend fun session(
      session: NfcSession,
      commands: NfcCommands,
    ) = when (commands.getFingerprintEnrollmentStatus(session).status) {
      COMPLETE -> {
        val bitcoinNetwork = accountConfigService.activeOrDefaultConfig().value.bitcoinNetworkType
        unsealedCsek = sekGenerator.generate()
        unsealedSsek = sekGenerator.generate()

        FingerprintEnrolled(
          appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
            commands.signChallenge(session, appGlobalAuthPublicKey.value)
          ),
          keyBundle = HwKeyBundle(
            localId = uuidGenerator.random(),
            spendingKey = commands.getInitialSpendingKey(session, bitcoinNetwork),
            authKey = commands.getAuthenticationKey(session),
            networkType = bitcoinNetwork
          ),
          sealedCsek = commands.sealData(session, unsealedCsek.key.raw),
          sealedSsek = commands.sealData(session, unsealedSsek.key.raw),
          serial = commands.getDeviceInfo(session).serial
        )
      }

      NOT_IN_PROGRESS -> {
        // If the fingerprint enrollment was not in progress, we need to run
        // the command to start enrollment and then we'll let the customer
        // know they need to start enrollment from the beginning.

        // Hardware attestation occurs before doing anything else.
        if (!session.parameters.isHardwareFake) {
          attestAndRecordSerial(session, commands)
        }

        commands.startFingerprintEnrollment(session)
        FingerprintEnrollmentStarted
      }

      INCOMPLETE -> FingerprintNotEnrolled
      UNSPECIFIED -> error("Unexpected fingerprint enrollment state")
    }

    override fun onCancel() = onCancel()

    override suspend fun onSuccess(response: PairingTransactionResponse) {
      when (response) {
        is FingerprintEnrolled -> {
          csekDao.set(key = response.sealedCsek, value = unsealedCsek).getOrThrow()
          ssekDao.set(key = response.sealedSsek, value = unsealedSsek).getOrThrow()

          val serialNumber = response.serial
          appInstallationDao.updateAppInstallationHardwareSerialNumber(serialNumber)
          response
        }
        else -> response
      }.also(onSuccess)
    }
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
    val serial = catchingResult {
      hardwareAttestation.verifyCertChain(
        identityCert = identityCert,
        batchCert = batchCert
      )
    }.getOrElse {
      logWarn { "[hardware_attestation_failure] Failed to verify cert chain" }
      return
    }

    val challenge = catchingResult {
      hardwareAttestation.generateChallenge()
    }.getOrElse {
      logWarn { "[hardware_attestation_failure] Failed to generate challenge for $serial " }
      return
    }

    catchingResult {
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
        logWarn { "[hardware_attestation_failure] Failed to verify challenge for $serial " }
        return
      } else {
        logWarn {
          "[hardware_attestation_failure] NFC flaked or firmware does not support attestation; allowing anyway... for now! Serial: $serial"
        }
        return
      }
    }

    logDebug { "Hardware attestation successful: $serial" }
  }
}

package build.wallet.nfc.transaction

import bitkey.account.AccountConfigService
import bitkey.account.HardwareType
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.catchingResult
import build.wallet.cloud.backup.csek.*
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.firmware.*
import build.wallet.firmware.EnrolledFingerprints.Companion.FIRST_FINGERPRINT_INDEX
import build.wallet.firmware.FingerprintEnrollmentStatus.*
import build.wallet.fwup.semverToInt
import build.wallet.logging.logDebug
import build.wallet.logging.logWarn
import build.wallet.nfc.HardwareProvisionedAppKeyStatusDao
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.sealSymmetricKey
import build.wallet.nfc.platform.signChallenge
import build.wallet.nfc.platform.verifyHardwareType
import build.wallet.nfc.transaction.PairingTransactionResponse.*
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.getOrThrow
import okio.ByteString.Companion.decodeHex

@BitkeyInject(AppScope::class)
class PairingTransactionProviderImpl(
  private val sekGenerator: SekGenerator,
  private val csekDao: CsekDao,
  private val ssekDao: SsekDao,
  private val uuidGenerator: UuidGenerator,
  private val appInstallationDao: AppInstallationDao,
  private val hardwareAttestation: HardwareAttestation,
  private val accountConfigService: AccountConfigService,
  private val fingerprintResetMinFirmwareVersionFeatureFlag:
    FingerprintResetMinFirmwareVersionFeatureFlag,
  private val hardwareProvisionedAppKeyStatusDao: HardwareProvisionedAppKeyStatusDao,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
) : PairingTransactionProvider {
  override operator fun invoke(
    appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    shouldLockHardware: Boolean,
    expectedHardwareType: HardwareType?,
    skipAppInstallationUpdate: Boolean,
    onSuccess: (PairingTransactionResponse) -> Unit,
    onCancel: () -> Unit,
  ) = object : NfcTransaction<PairingTransactionResponse> {
    private lateinit var unsealedCsek: Csek
    private lateinit var unsealedSsek: Ssek
    private var capturedDeviceInfo: FirmwareDeviceInfo? = null

    override val needsAuthentication = false
    override val shouldLock = false

    override suspend fun session(
      session: NfcSession,
      commands: NfcCommands,
    ): PairingTransactionResponse {
      // Verify hardware type FIRST, before any other commands, to fail fast if
      // the wrong device is tapped (e.g., tapping W1 during W3 upgrade flow).
      if (expectedHardwareType != null) {
        commands.verifyHardwareType(session, expectedHardwareType)
      }

      return when (commands.getFingerprintEnrollmentStatus(session).status) {
        COMPLETE -> {
          val bitcoinNetwork = accountConfigService.activeOrDefaultConfig().value.bitcoinNetworkType
          unsealedCsek = sekGenerator.generate()
          unsealedSsek = sekGenerator.generate()

          val hwAuthKey = commands.getAuthenticationKey(session)
          val deviceInfo = commands.getDeviceInfo(session)
          capturedDeviceInfo = deviceInfo

          FingerprintEnrolled(
            appGlobalAuthKeyHwSignature = when (deviceInfo.hardwareType()) {
              HardwareType.W1 -> AppGlobalAuthKeyHwSignature(
                commands.signChallenge(session, appGlobalAuthPublicKey.value)
              )
              // W3: signature is obtained later via verifyKeysAndBuildDescriptor.
              // A placeholder is used here and replaced after verifyKeysAndBuildDescriptor completes.
              HardwareType.W3 -> AppGlobalAuthKeyHwSignature(AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER)
            },
            keyBundle = HwKeyBundle(
              localId = uuidGenerator.random(),
              spendingKey = commands.getInitialSpendingKey(session, bitcoinNetwork),
              authKey = hwAuthKey,
              networkType = bitcoinNetwork
            ),
            sealedCsek = commands.sealSymmetricKey(session, unsealedCsek.key),
            sealedSsek = commands.sealSymmetricKey(session, unsealedSsek.key),
            serial = deviceInfo.serial,
            hardwareType = deviceInfo.hardwareType()
          ).also {
            // W3: app auth key is provisioned during verifyKeysAndBuildDescriptor, not pairing.
            if (deviceInfo.hardwareType() != HardwareType.W3) {
              val minFirmwareVersion =
                fingerprintResetMinFirmwareVersionFeatureFlag.flagValue().value.value
              val currentVersionInt = semverToInt(deviceInfo.version)
              val minVersionInt = semverToInt(minFirmwareVersion)
              if (currentVersionInt >= minVersionInt) {
                commands.provisionAppAuthKey(session, appGlobalAuthPublicKey.value.decodeHex())
                hardwareProvisionedAppKeyStatusDao.recordProvisionedKey(
                  hwAuthPubKey = hwAuthKey,
                  appAuthPubKey = appGlobalAuthPublicKey
                ).getOrThrow()
              }
            }

            // On successful enrollment: W3 shows confirmation (locks on dismiss only
            // when shouldLockHardware), W1 always locks.
            // Non-completion outcomes never reach this branch.
            when (deviceInfo.hardwareType()) {
              HardwareType.W3 -> runCatching {
                commands.showConfirmationScreen(session, lockOnDismiss = shouldLockHardware)
              }.onFailure {
                logWarn(throwable = it) { "Failed to show device confirmation screen" }
              }
              HardwareType.W1 -> runCatching {
                commands.lockDevice(session)
              }.onFailure {
                logWarn(throwable = it) { "Failed to lock W1 device after enrollment" }
              }
            }
          }
        }

        NOT_IN_PROGRESS -> {
          // If the fingerprint enrollment was not in progress, we need to run
          // the command to start enrollment and then we'll let the customer
          // know they need to start enrollment from the beginning.

          // Hardware attestation occurs before doing anything else.
          if (!session.parameters.isHardwareFake) {
            attestAndRecordSerial(session, commands)
          }

          // Get device info to detect hardware type for flow selection
          val deviceInfo = commands.getDeviceInfo(session)

          commands.startFingerprintEnrollment(
            session = session,
            fingerprintHandle = FingerprintHandle(
              index = FIRST_FINGERPRINT_INDEX,
              label = FingerprintHandle.defaultLabel(FIRST_FINGERPRINT_INDEX)
            )
          )
          FingerprintEnrollmentStarted(hardwareType = deviceInfo.hardwareType())
        }

        INCOMPLETE -> {
          // Get device info to detect hardware type for flow selection
          val deviceInfo = commands.getDeviceInfo(session)
          FingerprintNotEnrolled(hardwareType = deviceInfo.hardwareType())
        }
        UNSPECIFIED -> error("Unexpected fingerprint enrollment state")
      }
    }

    override fun onCancel() = onCancel()

    override suspend fun onSuccess(response: PairingTransactionResponse) {
      when (response) {
        is FingerprintEnrolled -> {
          csekDao.set(key = response.sealedCsek, value = unsealedCsek).getOrThrow()
          ssekDao.set(key = response.sealedSsek, value = unsealedSsek).getOrThrow()

          // Skipped during W3 upgrade — must not overwrite W1 identity
          if (!skipAppInstallationUpdate) {
            appInstallationDao.updateAppInstallationHardwareSerialNumber(response.serial)
            capturedDeviceInfo?.let {
              firmwareDeviceInfoDao.setDeviceInfo(it).getOrThrow()
            }
          }
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

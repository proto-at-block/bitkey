package build.wallet.nfc.transaction

import build.wallet.account.analytics.AppInstallationDao
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.csek.CsekGenerator
import build.wallet.firmware.FingerprintEnrollmentStatus.COMPLETE
import build.wallet.firmware.FingerprintEnrollmentStatus.INCOMPLETE
import build.wallet.firmware.FingerprintEnrollmentStatus.NOT_IN_PROGRESS
import build.wallet.firmware.FingerprintEnrollmentStatus.UNSPECIFIED
import build.wallet.logging.log
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrollmentRestarted
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintNotEnrolled
import build.wallet.platform.random.Uuid
import com.github.michaelbull.result.getOrThrow

class PairingTransactionProviderImpl(
  private val csekGenerator: CsekGenerator,
  private val csekDao: CsekDao,
  private val uuid: Uuid,
  private val appInstallationDao: AppInstallationDao,
) : PairingTransactionProvider {
  override operator fun invoke(
    networkType: BitcoinNetworkType,
    onSuccess: (PairingTransactionResponse) -> Unit,
    onCancel: () -> Unit,
    isHardwareFake: Boolean,
  ) = object : NfcTransaction<PairingTransactionResponse> {
    private lateinit var unsealedCsek: Csek

    override val isHardwareFake = isHardwareFake
    override val needsAuthentication = false
    override val shouldLock = true

    override suspend fun session(
      session: NfcSession,
      commands: NfcCommands,
    ) = when (commands.getFingerprintEnrollmentStatus(session)) {
      COMPLETE -> {
        unsealedCsek = csekGenerator.generate()

        FingerprintEnrolled(
          keyBundle =
            HwKeyBundle(
              localId = uuid.random(),
              spendingKey = commands.getInitialSpendingKey(session, networkType),
              authKey = commands.getAuthenticationKey(session),
              networkType = networkType
            ),
          sealedCsek = commands.sealKey(session, unsealedCsek),
          serial = commands.getDeviceInfo(session).serial
        )
      }

      NOT_IN_PROGRESS -> {
        // If the fingerprint enrollment was not in progress, we need to run
        // the command to start enrollment and then we'll let the customer
        // know they need to start enrollment from the beginning.
        commands.startFingerprintEnrollment(session)
        FingerprintEnrollmentRestarted
      }

      INCOMPLETE -> FingerprintNotEnrolled
      UNSPECIFIED -> error("Unexpected fingerprint enrollment state")
    }

    override fun onCancel() = onCancel()

    override suspend fun onSuccess(response: PairingTransactionResponse) {
      when (response) {
        is FingerprintEnrolled -> {
          csekDao.set(key = response.sealedCsek, value = unsealedCsek).getOrThrow()

          val serialNumber = response.serial
          appInstallationDao.updateAppInstallationHardwareSerialNumber(serialNumber)
          log { "Hardware serial number from activation: $serialNumber" }

          response
        }
        else -> response
      }.also(onSuccess)
    }
  }
}

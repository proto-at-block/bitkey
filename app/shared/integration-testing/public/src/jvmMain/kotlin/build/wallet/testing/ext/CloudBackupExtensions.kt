package build.wallet.testing.ext

import bitkey.account.HardwareType
import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.csek.Sek
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.NfcSession.RequirePairedHardware.NotRequired
import build.wallet.nfc.platform.unsealSymmetricKey
import build.wallet.testing.AppTester
import com.github.michaelbull.result.getOrThrow
import io.kotest.matchers.nulls.shouldNotBeNull

/**
 * Delete real cloud backups from fake, local cloud accounts.
 */
suspend fun AppTester.deleteBackupsFromFakeCloud(accountId: AccountId) {
  CloudStoreAccountFake.cloudStoreAccountFakes.forEach { fakeCloudAccount ->
    cloudBackupService.clear(accountId, fakeCloudAccount, clearRemoteOnly = true)
  }
}

/**
 * Read cloud backup for the app's current or provided cloud store account.
 */
suspend fun AppTester.readCloudBackup(cloudStoreAccount: CloudStoreAccount? = null): CloudBackup? {
  val cloudAccount =
    cloudStoreAccount
      ?: cloudStoreAccountRepository.currentAccount(cloudServiceProvider()).getOrThrow()
      ?: return null
  return cloudBackupService.readActiveBackup(cloudAccount).getOrThrow()
}

/**
 * Read the current cloud backup using [readCloudBackup] then decrypts the cloud backup to retrieve
 * its [FullAccountKeys].
 */
suspend fun AppTester.decryptCloudBackupKeys(
  cloudStoreAccount: CloudStoreAccount? = null,
): FullAccountKeys {
  val cloudBackup = readCloudBackup(cloudStoreAccount)
    .shouldNotBeNull()

  val fullAccountFields = when (cloudBackup) {
    is CloudBackupV2 -> cloudBackup.fullAccountFields
    is CloudBackupV3 -> cloudBackup.fullAccountFields
    else -> error("Unsupported cloud backup type: ${cloudBackup::class.simpleName}")
  }.shouldNotBeNull()

  val decryptedSsek = Sek(
    when (fullAccountFields.hardwareType) {
      HardwareType.W1 ->
        fakeNfcCommands.unsealSymmetricKey(
          session = fakeSessionFor(fullAccountFields.hardwareType),
          sealedData = fullAccountFields.sealedHwEncryptionKey
        )
      HardwareType.W3 ->
        fakeW3NfcCommands.unsealSymmetricKey(
          session = fakeSessionFor(fullAccountFields.hardwareType),
          sealedData = fullAccountFields.sealedHwEncryptionKey
        )
    }
  )
  csekDao.set(fullAccountFields.sealedHwEncryptionKey, decryptedSsek)

  return cloudBackupRestorer.decryptCloudBackup(cloudBackup).getOrThrow()
}

private fun fakeSessionFor(
  hardwareType: HardwareType,
): NfcSession =
  NfcSessionFake(
    parameters = NfcSession.Parameters(
      isHardwareFake = true,
      hardwareType = hardwareType,
      needsAuthentication = NfcSessionFake.FakeParameters.needsAuthentication,
      shouldLock = NfcSessionFake.FakeParameters.shouldLock,
      skipFirmwareTelemetry = NfcSessionFake.FakeParameters.skipFirmwareTelemetry,
      asyncNfcSigning = NfcSessionFake.FakeParameters.asyncNfcSigning,
      nfcFlowName = NfcSessionFake.FakeParameters.nfcFlowName,
      requirePairedHardware = NotRequired,
      maxNfcRetryAttempts = NfcSessionFake.FakeParameters.maxNfcRetryAttempts,
      onTagConnected = {},
      onTagDisconnected = {}
    )
  )

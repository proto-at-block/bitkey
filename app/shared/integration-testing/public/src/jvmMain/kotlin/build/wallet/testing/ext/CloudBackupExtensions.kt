package build.wallet.testing.ext

import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.csek.Sek
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.platform.unsealSymmetricKey
import build.wallet.testing.AppTester
import com.github.michaelbull.result.getOrThrow
import io.kotest.matchers.nulls.shouldNotBeNull

/**
 * Delete real cloud backups from fake, local cloud accounts.
 */
suspend fun AppTester.deleteBackupsFromFakeCloud(accountId: AccountId) {
  CloudStoreAccountFake.cloudStoreAccountFakes.forEach { fakeCloudAccount ->
    cloudBackupRepository.clear(accountId, fakeCloudAccount, clearRemoteOnly = true)
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
  return cloudBackupRepository.readActiveBackup(cloudAccount).getOrThrow()
}

/**
 * Read the current cloud backup using [readCloudBackup] then decrypts the cloud backup to retrieve
 * its [FullAccountKeys].
 */
suspend fun AppTester.decryptCloudBackupKeys(): FullAccountKeys {
  val cloudBackup = readCloudBackup()
    .shouldNotBeNull()

  val fullAccountFields = when (cloudBackup) {
    is CloudBackupV2 -> cloudBackup.fullAccountFields
    is CloudBackupV3 -> cloudBackup.fullAccountFields
    else -> error("Unsupported cloud backup type: ${cloudBackup::class.simpleName}")
  }.shouldNotBeNull()

  val decryptedSsek = Sek(
    fakeNfcCommands.unsealSymmetricKey(
      session = NfcSessionFake(),
      sealedData = fullAccountFields.sealedHwEncryptionKey
    )
  )
  csekDao.set(fullAccountFields.sealedHwEncryptionKey, decryptedSsek)

  return cloudBackupRestorer.decryptCloudBackup(cloudBackup).getOrThrow()
}

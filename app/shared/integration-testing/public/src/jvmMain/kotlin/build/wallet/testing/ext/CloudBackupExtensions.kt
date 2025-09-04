package build.wallet.testing.ext

import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.csek.Sek
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.nfc.NfcSessionFake
import build.wallet.testing.AppTester
import com.github.michaelbull.result.getOrThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Delete real cloud backups from fake, local cloud accounts.
 */
suspend fun AppTester.deleteBackupsFromFakeCloud() {
  CloudStoreAccountFake.cloudStoreAccountFakes.forEach { fakeCloudAccount ->
    cloudBackupRepository.clear(fakeCloudAccount, clearRemoteOnly = true)
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
  return cloudBackupRepository.readBackup(cloudAccount).getOrThrow()
}

/**
 * Read the current cloud backup using [readCloudBackup] then decrypts the cloud backup to retrieve
 * its [FullAccountKeys].
 */
suspend fun AppTester.decryptCloudBackupKeys(): FullAccountKeys {
  val cloudBackup = readCloudBackup()
    .shouldNotBeNull()
    .shouldBeInstanceOf<CloudBackupV2>()
  val fullAccountFields = cloudBackup.fullAccountFields.shouldNotBeNull()

  val decryptedSsek = Sek(
    SymmetricKeyImpl(
      fakeNfcCommands.unsealData(
        session = NfcSessionFake(),
        sealedData = fullAccountFields.sealedHwEncryptionKey
      )
    )
  )
  csekDao.set(fullAccountFields.sealedHwEncryptionKey, decryptedSsek)
  return cloudBackupV2Restorer.decryptCloudBackup(cloudBackup).getOrThrow()
}

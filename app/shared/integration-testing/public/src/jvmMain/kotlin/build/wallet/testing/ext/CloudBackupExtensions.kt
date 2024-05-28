package build.wallet.testing.ext

import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.testing.AppTester
import com.github.michaelbull.result.getOrThrow

/**
 * Delete real cloud backups from fake, local cloud accounts.
 */
suspend fun AppTester.deleteBackupsFromFakeCloud() {
  CloudStoreAccountFake.cloudStoreAccountFakes.forEach { fakeCloudAccount ->
    app.cloudBackupRepository.clear(fakeCloudAccount, clearRemoteOnly = true)
  }
}

/**
 * Read cloud backup for the app's current or provided cloud store account.
 */
suspend fun AppTester.readCloudBackup(cloudStoreAccount: CloudStoreAccount? = null): CloudBackup? {
  val cloudAccount =
    cloudStoreAccount
      ?: app.cloudStoreAccountRepository.currentAccount(cloudServiceProvider()).getOrThrow()
      ?: return null
  return app.cloudBackupRepository.readBackup(cloudAccount).getOrThrow()
}

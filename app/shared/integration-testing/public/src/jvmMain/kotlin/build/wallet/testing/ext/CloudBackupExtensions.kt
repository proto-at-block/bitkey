package build.wallet.testing.ext

import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.testing.AppTester

/**
 * Delete real cloud backups from fake, local cloud accounts.
 */
suspend fun AppTester.deleteBackupsFromFakeCloud() {
  CloudStoreAccountFake.cloudStoreAccountFakes.forEach { fakeCloudAccount ->
    app.cloudBackupRepository.clear(fakeCloudAccount, clearRemoteOnly = true)
  }
}

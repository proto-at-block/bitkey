package build.wallet.debug.cloud

import build.wallet.cloud.backup.CloudBackupStoreKeys
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class CloudBackupViewerImpl(
  cloudStoreAccountRepository: CloudStoreAccountRepository,
  cloudKeyValueStore: CloudKeyValueStore,
  cloudBackupStoreKeys: CloudBackupStoreKeys,
) : CloudBackupViewer by SingleStoreCloudBackupViewer(
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    cloudKeyValueStore = cloudKeyValueStore,
    cloudBackupStoreKeys = cloudBackupStoreKeys
  )

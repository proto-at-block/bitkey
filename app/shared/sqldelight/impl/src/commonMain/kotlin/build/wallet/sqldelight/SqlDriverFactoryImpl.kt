package build.wallet.sqldelight

import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppVariant
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.random.UuidGenerator
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.getOrPutString
import kotlinx.coroutines.runBlocking

expect class SqlDriverFactoryImpl(
  platformContext: PlatformContext,
  fileDirectoryProvider: FileDirectoryProvider,
  encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
  uuidGenerator: UuidGenerator,
  appVariant: AppVariant,
) : SqlDriverFactory

// TODO(W-5766): remove runBlocking
@Suppress("ForbiddenMethodCall")
internal fun loadDbKey(
  encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
  uuidGenerator: UuidGenerator,
): String =
  runBlocking {
    encryptedKeyValueStoreFactory
      // Changing these values is a breaking change
      // These should only be changed with a migration plan otherwise data will be lost
      .getOrCreate(storeName = "SqlCipherStore")
      .getOrPutString("db-key") {
        uuidGenerator.random()
      }
  }

internal class DbNotEncryptedException(message: String?) : Exception(message)

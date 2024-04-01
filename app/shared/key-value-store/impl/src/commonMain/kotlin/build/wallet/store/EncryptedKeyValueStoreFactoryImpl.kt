package build.wallet.store

import build.wallet.platform.PlatformContext
import build.wallet.platform.data.FileManager
import com.russhwolf.settings.coroutines.SuspendSettings

expect class EncryptedKeyValueStoreFactoryImpl(
  platformContext: PlatformContext,
  fileManager: FileManager,
) : EncryptedKeyValueStoreFactory {
  override suspend fun getOrCreate(storeName: String): SuspendSettings
}

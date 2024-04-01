package build.wallet.store

import build.wallet.platform.PlatformContext
import build.wallet.platform.data.FileManager
import com.github.michaelbull.result.getOrThrow
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.coroutines.SuspendSettings
import com.russhwolf.settings.coroutines.toSuspendSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties

actual class KeyValueStoreFactoryImpl actual constructor(
  platformContext: PlatformContext,
  private val fileManager: FileManager,
) : KeyValueStoreFactory {
  private val settings = mutableMapOf<String, SuspendSettings>()
  private val lock = Mutex()

  actual override suspend fun getOrCreate(storeName: String): SuspendSettings {
    return lock.withLock {
      settings.getOrPut(storeName) {
        create(storeName)
      }
    }
  }

  private suspend fun create(storeName: String): SuspendSettings {
    val fileName = "$storeName.properties"
    return PropertiesSettings(loadProperties(fileName)) { saveProperties(fileName, it) }
      .toSuspendSettings()
  }

  private fun saveProperties(
    fileName: String,
    properties: Properties,
  ) {
    val buffer = ByteArrayOutputStream()
    properties.store(buffer, null)
    @Suppress("ForbiddenMethodCall")
    (
      runBlocking {
        fileManager.writeFile(buffer.toByteArray(), fileName)
      }
    )
  }

  private suspend fun loadProperties(fileName: String): Properties {
    val properties = Properties()
    if (fileManager.fileExists(fileName)) {
      val buffer = fileManager.readFileAsBytes(fileName).result.getOrThrow()
      withContext(Dispatchers.IO) {
        properties.load(ByteArrayInputStream(buffer))
      }
    }
    return properties
  }
}

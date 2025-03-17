@file:Suppress("ForbiddenImport")

package build.wallet.store

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.data.FileManager
import com.github.michaelbull.result.getOrThrow
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.coroutines.SuspendSettings
import com.russhwolf.settings.coroutines.toSuspendSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * JVM implementation of [EncryptedKeyValueStoreFactory] baked by Java's [Properties]. Not actually
 * encrypted since we are using JVM for development purposes only, we don't actually ship it.
 */

@BitkeyInject(AppScope::class)
class EncryptedKeyValueStoreFactoryImpl(
  private val fileManager: FileManager,
) : EncryptedKeyValueStoreFactory {
  private val settings = mutableMapOf<String, SuspendSettings>()
  private val lock = Mutex()

  override suspend fun getOrCreate(storeName: String): SuspendSettings {
    return lock.withLock {
      settings.getOrPut(storeName) {
        create(storeName)
      }
    }
  }

  private suspend fun create(storeName: String): SuspendSettings {
    val fileName = "$storeName.properties"
    return PropertiesSettings(loadProperties(fileName)) { saveProperties(fileName, it) }
      .toSuspendSettings(Dispatchers.IO)
  }

  private fun saveProperties(
    fileName: String,
    properties: Properties,
  ) {
    val buffer = ByteArrayOutputStream()
    properties.store(buffer, null)
    @Suppress("ForbiddenMethodCall")
    runBlocking {
      fileManager.writeFile(buffer.toByteArray(), fileName)
    }
  }

  private suspend fun loadProperties(fileName: String): Properties {
    val properties = Properties()
    if (fileManager.fileExists(fileName)) {
      val buffer = fileManager.readFileAsBytes(fileName).result.getOrThrow()
      properties.load(ByteArrayInputStream(buffer))
    }
    return properties
  }
}

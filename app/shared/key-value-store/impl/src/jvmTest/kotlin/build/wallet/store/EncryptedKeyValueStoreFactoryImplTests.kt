package build.wallet.store

import build.wallet.platform.PlatformContext
import build.wallet.platform.data.FileDirectoryProviderImpl
import build.wallet.platform.data.FileManagerImpl
import com.russhwolf.settings.ExperimentalSettingsApi
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalSettingsApi::class)
class EncryptedKeyValueStoreFactoryImplTests : FunSpec({
  test("save and load properties") {
    val directoryProvider = FileDirectoryProviderImpl(PlatformContext())
    val storeFactory =
      EncryptedKeyValueStoreFactoryImpl(PlatformContext(), FileManagerImpl(directoryProvider))

    val dir = directoryProvider.filesDir()
    File(dir).deleteRecursively()
    Files.createDirectories(Path.of(dir))

    val store = storeFactory.getOrCreate("test")
    store.putString("hello", "world")
    store.getStringOrNull("hello").shouldBe("world")

    val newStore = storeFactory.getOrCreate("test")
    newStore.getStringOrNull("hello").shouldBe("world")
  }

  test("same instance of settings is created") {
    val directoryProvider = FileDirectoryProviderImpl(PlatformContext())
    val storeFactory =
      EncryptedKeyValueStoreFactoryImpl(PlatformContext(), FileManagerImpl(directoryProvider))

    storeFactory.getOrCreate("foo").shouldBeSameInstanceAs(storeFactory.getOrCreate("foo"))
    storeFactory.getOrCreate("foo").shouldNotBe(storeFactory.getOrCreate("bar"))
  }
})

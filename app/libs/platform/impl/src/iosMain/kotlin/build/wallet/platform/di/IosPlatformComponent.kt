package build.wallet.platform.di

import build.wallet.di.AppScope
import build.wallet.di.SingleIn
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVersion
import build.wallet.platform.config.DeviceOs
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.data.FileManager
import me.tatarka.inject.annotations.Provides
import platform.Foundation.NSBundle
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface IosPlatformComponent {
  @Provides
  fun deviceOs(): DeviceOs = DeviceOs.iOS

  @Provides
  fun appId(): AppId = AppId(NSBundle.mainBundle.bundleIdentifier!!)

  @Provides
  @SingleIn(AppScope::class)
  fun appVersion(): AppVersion {
    val info = NSBundle.mainBundle.infoDictionary
    val version = info?.get("CFBundleShortVersionString")
    val build = info?.get("CFBundleVersion")
    return AppVersion("$version.$build")
  }

  @Provides
  fun provideFileManager(
    fileDirectoryProvider: FileDirectoryProvider,
    fileManagerProvider: (FileDirectoryProvider) -> FileManager,
  ): FileManager = fileManagerProvider(fileDirectoryProvider)
}

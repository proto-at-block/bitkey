package build.wallet.platform.di

import build.wallet.di.AppScope
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVersion
import build.wallet.platform.config.DeviceOs
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface JvmPlatformComponent {
  @Provides
  fun provideDeviceOs(): DeviceOs = DeviceOs.Other

  @Provides
  fun provideAppId(): AppId = AppId(value = "build.wallet.jvm")

  @Provides
  fun provideAppVersion(): AppVersion = AppVersion("N/A")

  @Provides
  fun provideAppVariant(): AppVariant = AppVariant.Development
}

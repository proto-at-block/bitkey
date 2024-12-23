package build.wallet.platform.di

import android.app.Application
import android.content.res.Resources
import build.wallet.di.AppScope
import build.wallet.platform.config.DeviceOs
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface AndroidPlatformComponent {
  @Provides
  fun deviceOs(): DeviceOs = DeviceOs.Android

  @Provides
  fun provideResources(application: Application): Resources = application.resources
}

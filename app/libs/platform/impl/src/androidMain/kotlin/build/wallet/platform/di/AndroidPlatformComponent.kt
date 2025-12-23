package build.wallet.platform.di

import android.app.Application
import android.content.res.Resources
import build.wallet.di.AppScope
import build.wallet.platform.config.DeviceOs
import com.google.android.play.agesignals.AgeSignalsManager
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface AndroidPlatformComponent {
  @Provides
  fun deviceOs(): DeviceOs = DeviceOs.Android

  @Provides
  fun provideResources(application: Application): Resources = application.resources

  @Provides
  fun provideAgeSignalsManager(application: Application): AgeSignalsManager =
    AgeSignalsManagerFactory.create(application)
}

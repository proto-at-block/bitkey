package build.wallet.platform.di

import android.app.Application
import android.app.NotificationManager
import android.content.ClipboardManager
import android.hardware.SensorManager
import android.os.Vibrator
import android.telephony.TelephonyManager
import build.wallet.di.AppScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface AndroidSystemServicesComponent {
  @Provides
  fun provideClipboardManager(application: Application): ClipboardManager {
    return application.getSystemService(ClipboardManager::class.java)
  }

  @Provides
  fun provideVibrator(application: Application): Vibrator? {
    return application.getSystemService(Vibrator::class.java)
  }

  @Provides
  fun provideNotificationManager(application: Application): NotificationManager {
    return application.getSystemService(NotificationManager::class.java)
  }

  @Provides
  fun provideTelephonyManager(application: Application): TelephonyManager {
    return application.getSystemService(TelephonyManager::class.java)
  }

  @Provides
  fun provideSensorManager(application: Application): SensorManager {
    return application.getSystemService(SensorManager::class.java)
  }
}

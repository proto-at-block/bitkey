package build.wallet.di

import android.app.Application
import androidx.lifecycle.LifecycleObserver
import bitkey.bugsnag.BugsnagContext
import build.wallet.analytics.events.EventTracker
import build.wallet.debug.StrictModeEnabler
import build.wallet.inappsecurity.BiometricPreference
import build.wallet.logging.LoggerInitializer
import build.wallet.notifications.DeviceTokenManager
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVersion
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.notifications.NotificationChannelRepository
import build.wallet.platform.sensor.Accelerometer
import kotlinx.coroutines.CoroutineScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent

/**
 * Component bound to [AppScope] to be used by the Android app. Tied to Android app's [Application] instance.
 */
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class AndroidAppComponent(
  @get:Provides val appId: AppId,
  @get:Provides val appVariant: AppVariant,
  @get:Provides val appVersion: AppVersion,
  @get:Provides val application: Application,
) : AndroidActivityComponent.Factory {
  /**
   * Dependencies that are used by the Android app code.
   */
  abstract val appCoroutineScope: CoroutineScope
  abstract val appLifecycleObserver: LifecycleObserver
  abstract val biometricPreference: BiometricPreference
  abstract val bugsnagContext: BugsnagContext
  abstract val deviceTokenManager: DeviceTokenManager
  abstract val eventTracker: EventTracker
  abstract val loggingInitializer: LoggerInitializer
  abstract val notificationChannelRepository: NotificationChannelRepository
  abstract val strictModeEnabler: StrictModeEnabler
  abstract val deviceInfoProvider: DeviceInfoProvider
  abstract val accelerometer: Accelerometer
}

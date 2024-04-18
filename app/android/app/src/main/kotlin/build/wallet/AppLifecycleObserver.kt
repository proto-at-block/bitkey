package build.wallet

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import build.wallet.analytics.events.AppSessionManager

class AppLifecycleObserver(
  private val appSessionManager: AppSessionManager,
) : DefaultLifecycleObserver {
  // Application entered foreground
  override fun onStart(owner: LifecycleOwner) {
    super.onStart(owner)

    appSessionManager.appDidEnterForeground()
  }

  // Application entered background
  override fun onStop(owner: LifecycleOwner) {
    super.onStop(owner)

    appSessionManager.appDidEnterBackground()
  }
}

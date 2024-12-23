package build.wallet.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.app.AppSessionManager

// TODO: move to :platform:impl once AppSessionManager is moved to :platform

@BitkeyInject(AppScope::class, boundTypes = [LifecycleObserver::class])
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

package build.wallet

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import build.wallet.analytics.events.SessionIdProvider

class AppLifecycleObserver(
  private val sessionIdProvider: SessionIdProvider,
) : DefaultLifecycleObserver {
  // Application entered foreground
  override fun onStart(owner: LifecycleOwner) {
    super.onStart(owner)

    sessionIdProvider.applicationDidEnterForeground()
  }

  // Application entered background
  override fun onStop(owner: LifecycleOwner) {
    super.onStop(owner)

    sessionIdProvider.applicationDidEnterBackground()
  }
}

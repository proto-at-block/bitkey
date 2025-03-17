package build.wallet.platform.thread

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class MainThreadDetectorImpl : MainThreadDetector {
  override fun isMainThread(): Boolean = build.wallet.platform.thread.isMainThread()
}

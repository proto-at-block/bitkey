package build.wallet.platform.thread

class MainThreadDetectorImpl : MainThreadDetector {
  override fun isMainThread(): Boolean = build.wallet.platform.thread.isMainThread()
}

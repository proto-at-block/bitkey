package build.wallet.platform.thread

interface MainThreadDetector {
  /**
   * Indicates whether the current thread is the main thread.
   */
  fun isMainThread(): Boolean
}

/**
 * Indicates whether the current thread is the main thread.
 */
expect fun isMainThread(): Boolean

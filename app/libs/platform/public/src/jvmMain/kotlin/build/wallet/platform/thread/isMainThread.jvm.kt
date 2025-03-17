package build.wallet.platform.thread

actual fun isMainThread(): Boolean = Thread.currentThread().name == "main"

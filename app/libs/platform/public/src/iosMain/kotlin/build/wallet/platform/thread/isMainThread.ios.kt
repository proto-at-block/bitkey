package build.wallet.platform.thread

import platform.Foundation.NSThread

actual fun isMainThread(): Boolean = NSThread.isMainThread

package build.wallet.platform.thread

import android.os.Looper

actual fun isMainThread(): Boolean = Looper.getMainLooper().isCurrentThread

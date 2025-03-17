package build.wallet.ui.system

import android.os.Build

actual fun isBlurSupported(): Boolean {
  return Build.VERSION.SDK_INT > 30
}

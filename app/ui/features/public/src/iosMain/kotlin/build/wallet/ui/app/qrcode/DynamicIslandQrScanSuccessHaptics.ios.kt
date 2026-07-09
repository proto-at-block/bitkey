package build.wallet.ui.app.qrcode

import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType.UINotificationFeedbackTypeSuccess
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal actual fun performDynamicIslandQrScanSuccessHaptic() {
  dispatch_async(queue = dispatch_get_main_queue()) {
    UINotificationFeedbackGenerator()
      .notificationOccurred(UINotificationFeedbackTypeSuccess)
  }
}

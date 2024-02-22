package build.wallet.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import build.wallet.BitkeyApplication
import build.wallet.analytics.v1.Action

class NotificationDismissBroadcastReceiver : BroadcastReceiver() {
  override fun onReceive(
    context: Context?,
    intent: Intent?,
  ) {
    (context?.applicationContext as BitkeyApplication)
      .appComponent
      .eventTracker
      .track(Action.ACTION_APP_PUSH_NOTIFICATION_DISMISS)
  }
}

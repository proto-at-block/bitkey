package build.wallet.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import build.wallet.BitkeyApplication
import build.wallet.analytics.v1.Action
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationDismissBroadcastReceiver : BroadcastReceiver() {
  override fun onReceive(
    context: Context,
    intent: Intent,
  ) {
    CoroutineScope(Dispatchers.Default).launch {
      (context.applicationContext as BitkeyApplication)
        .appComponent
        .await()
        .eventTracker
        .track(Action.ACTION_APP_PUSH_NOTIFICATION_DISMISS)
    }
  }
}

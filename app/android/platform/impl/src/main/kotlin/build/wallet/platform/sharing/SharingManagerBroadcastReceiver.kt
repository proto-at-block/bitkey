package build.wallet.platform.sharing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Broadcast receiver for sharing manager. Triggered when a user selects a share action.
 * We use this to know when to advance the trusted contact invitation flow.
 */
class SharingManagerBroadcastReceiver : BroadcastReceiver() {
  override fun onReceive(
    context: Context,
    intent: Intent,
  ) {
    sharingManager?.completed()
    sharingManager = null
  }

  companion object {
    private var sharingManager: SharingManager? = null

    fun setManager(manager: SharingManager?) {
      this.sharingManager = manager
    }
  }
}

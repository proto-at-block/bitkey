package build.wallet.platform.sharing

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import build.wallet.platform.data.MimeType

class SharingManagerImpl(
  private val activity: Activity,
) : SharingManager {
  var completion: ((Boolean) -> Unit)? = null

  override fun shareText(
    text: String,
    title: String,
    completion: ((Boolean) -> Unit)?,
  ) {
    // Set the sharing manager to this instance so we can call the completion callback
    // upon the user selecting a share action
    SharingManagerBroadcastReceiver.setManager(this)

    this.completion = completion
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = MimeType.TEXT_PLAIN.name
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_TEXT, text)
      }

    val receiver = Intent(activity, SharingManagerBroadcastReceiver::class.java)

    val pendingIntent =
      PendingIntent.getBroadcast(
        activity,
        0,
        receiver,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
      )
    val chooser = Intent.createChooser(intent, title, pendingIntent.intentSender)

    activity.startActivity(chooser)
  }

  override fun completed() {
    completion?.invoke(true)
  }
}

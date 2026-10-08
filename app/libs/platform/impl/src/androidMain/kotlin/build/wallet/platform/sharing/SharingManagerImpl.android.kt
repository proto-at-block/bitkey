package build.wallet.platform.sharing

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import build.wallet.catchingResult
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.onFailure
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.io.File

@BitkeyInject(ActivityScope::class)
class SharingManagerImpl(
  private val activity: Activity,
) : SharingManager {
  override fun shareText(
    text: String,
    title: String,
    completion: ((Boolean) -> Unit)?,
  ) {
    shareData(
      data = text.encodeUtf8(),
      mimeType = MimeType.TEXT_PLAIN,
      title = title,
      completion = completion
    )
  }

  override fun shareData(
    data: ByteString,
    mimeType: MimeType,
    title: String,
    completion: ((Boolean) -> Unit)?,
  ) {
    catchingResult {
      // Park the callback itself, not this manager. The chooser's selection broadcast can arrive
      // after the activity is gone, and this receiver's field is static, so anything reachable from
      // here outlives the activity — holding the activity-scoped manager leaked it.
      //
      // Registered before the chooser is launched so a selection can't be missed, and replaces any
      // previous share's callback: only the most recent chooser can still be on screen.
      SharingManagerBroadcastReceiver.setPendingCompletion(completion)
      clearPendingCompletionWhenActivityIsDestroyed()

      val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType.name
        putExtra(Intent.EXTRA_TITLE, title)
        when (mimeType) {
          MimeType.TEXT_PLAIN -> {
            putExtra(Intent.EXTRA_TEXT, data.utf8())
          }
          MimeType.PDF, MimeType.CSV -> {
            val dataFile = File(activity.filesDir, "$title.${mimeType.ext}").apply {
              writeBytes(data.toByteArray())
            }
            val dataUri = FileProvider
              .getUriForFile(activity, "${activity.packageName}.provider", dataFile)
            putExtra(Intent.EXTRA_STREAM, dataUri)
            // Temporarily allow external apps to read the file while sharing the file.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
          // Some of the remaining Mime types actually are eligible to share, but
          // we don't have a practical use case for them yet.
          else -> throw IllegalArgumentException("Unsupported mimeType: $mimeType")
        }
      }

      val receiver = Intent(activity, SharingManagerBroadcastReceiver::class.java)

      val pendingIntent = PendingIntent.getBroadcast(
        activity,
        0,
        receiver,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
      )
      val chooser = Intent.createChooser(intent, title, pendingIntent.intentSender)

      activity.startActivity(chooser)
    }.onFailure {
      // The chooser never opened, so no selection broadcast is coming. Drop the callback rather
      // than leaving it parked for the next share to inherit.
      SharingManagerBroadcastReceiver.setPendingCompletion(null)
    }.logFailure { "Error sharing a file." }
  }

  override fun completed() {
    SharingManagerBroadcastReceiver.takePendingCompletion()?.invoke(true)
  }

  /**
   * Drops the pending completion if the activity is destroyed before the chooser reports back.
   *
   * The callback itself holds no `Activity`, but callers are free to capture their own state — a
   * state machine's Compose setter, for instance — which can reach the activity indirectly. Since a
   * dismissed chooser sends no broadcast, without this the callback would sit in the receiver's
   * static field for the rest of the process.
   *
   * The observer is added per share and removes itself once it fires, so repeated shares don't
   * accumulate observers.
   */
  private fun clearPendingCompletionWhenActivityIsDestroyed() {
    val lifecycleOwner = activity as? LifecycleOwner ?: return

    lifecycleOwner.lifecycle.addObserver(
      object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
          SharingManagerBroadcastReceiver.setPendingCompletion(null)
          owner.lifecycle.removeObserver(this)
        }
      }
    )
  }
}

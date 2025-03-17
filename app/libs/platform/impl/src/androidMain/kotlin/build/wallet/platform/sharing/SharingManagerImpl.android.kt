package build.wallet.platform.sharing

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.core.content.FileProvider
import build.wallet.catchingResult
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.platform.data.MimeType
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.io.File

@BitkeyInject(ActivityScope::class)
class SharingManagerImpl(
  private val activity: Activity,
) : SharingManager {
  private var completion: ((Boolean) -> Unit)? = null

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
      // Set the sharing manager to this instance so we can call the completion callback
      // upon the user selecting a share action
      SharingManagerBroadcastReceiver.setManager(this)

      this.completion = completion
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
    }.logFailure { "Error sharing a file." }
  }

  override fun completed() {
    completion?.invoke(true)
  }
}

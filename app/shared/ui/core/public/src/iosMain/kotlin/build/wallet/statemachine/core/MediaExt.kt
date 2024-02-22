package build.wallet.statemachine.core

import build.wallet.platform.data.MimeType
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSError
import platform.Foundation.NSProgress
import platform.Foundation.NSURL
import kotlin.coroutines.resume

fun nativeMedia(
  name: String,
  mimeType: MimeType,
  loadUrl: ((NSURL?, NSError?) -> Unit) -> NSProgress,
): SystemUIModel.MediaPickerModel.Media {
  return SystemUIModel.MediaPickerModel.Media(
    name = name,
    mimeType = mimeType,
    data = {
      suspendCancellableCoroutine { continuation ->
        val progress =
          loadUrl { url, _ ->
            // TODO[W-5828]: Recognize and propagate `NSError` we receive.
            val source =
              url?.path?.toPath()?.let {
                FileSystem.SYSTEM.source(it)
              }
            continuation.resume(source)
          }
        continuation.invokeOnCancellation {
          progress.cancel()
        }
      }
    }
  )
}

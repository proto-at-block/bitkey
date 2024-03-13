package build.wallet.testing

import build.wallet.platform.data.MimeType
import build.wallet.platform.sharing.SharingManager
import kotlinx.coroutines.flow.MutableStateFlow
import okio.ByteString

class SharingManagerFake : SharingManager {
  data class SharedText(
    val text: String,
    val title: String,
  )

  val lastSharedText = MutableStateFlow<SharedText?>(null)

  override fun shareText(
    text: String,
    title: String,
    completion: ((Boolean) -> Unit)?,
  ) {
    lastSharedText.value = SharedText(text, title)
    completion?.invoke(true)
  }

  data class SharedData(
    val data: ByteArray,
    val mimeType: String,
    val title: String,
  )

  val lastSharedData = MutableStateFlow<SharedData?>(null)

  override fun shareData(
    data: ByteString,
    mimeType: MimeType,
    title: String,
    completion: ((Boolean) -> Unit)?,
  ) {
    lastSharedData.value = SharedData(data.toByteArray(), mimeType.name, title)
    completion?.invoke(true)
  }

  override fun completed() {
    // no-op
  }
}

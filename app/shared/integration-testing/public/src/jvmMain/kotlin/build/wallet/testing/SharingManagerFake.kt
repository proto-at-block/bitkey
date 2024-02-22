package build.wallet.testing

import build.wallet.platform.sharing.SharingManager
import kotlinx.coroutines.flow.MutableStateFlow

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

  override fun completed() {
    // no-op
  }
}

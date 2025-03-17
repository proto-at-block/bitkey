package build.wallet.platform.sharing

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.platform.data.MimeType
import okio.ByteString

class SharingManagerMock(
  turbine: (String) -> Turbine<Any>,
) : SharingManager {
  val sharedTextCalls = turbine("share text calls")
  var completed = false

  data class SharedText(
    val text: String,
    val title: String,
  )

  data class SharedData(
    val data: ByteString,
    val mimeType: MimeType,
    val title: String,
  )

  override fun shareText(
    text: String,
    title: String,
    completion: ((Boolean) -> Unit)?,
  ) {
    sharedTextCalls += SharedText(text = text, title = title)
  }

  val sharedDataCalls = turbine("share data calls")

  override fun shareData(
    data: ByteString,
    mimeType: MimeType,
    title: String,
    completion: ((Boolean) -> Unit)?,
  ) {
    sharedDataCalls += SharedData(data = data, mimeType = mimeType, title = title)
  }

  override fun completed() {
    completed = true
  }
}

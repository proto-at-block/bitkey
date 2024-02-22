package build.wallet.platform.sharing

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign

class SharingManagerMock(
  turbine: (String) -> Turbine<Any>,
) : SharingManager {
  val sharedTextCalls = turbine("share text calls")
  var completed = false

  data class SharedText(
    val text: String,
    val title: String,
  )

  override fun shareText(
    text: String,
    title: String,
    completion: ((Boolean) -> Unit)?,
  ) {
    sharedTextCalls += SharedText(text = text, title = title)
  }

  override fun completed() {
    completed = true
  }
}

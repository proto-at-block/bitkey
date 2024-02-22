package build.wallet.platform.clipboard

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.platform.clipboard.ClipItem.PlainText

data class ClipboardMock(
  var plainTextItemToReturn: PlainText? = null,
) : Clipboard {
  val copiedItems = Turbine<ClipItem>()

  override fun setItem(item: ClipItem) {
    copiedItems += item
    if (item is PlainText) {
      plainTextItemToReturn = item
    }
  }

  override fun getPlainTextItemAndroid(): PlainText? {
    return plainTextItemToReturn
  }

  override fun getPlainTextItem(): PlainText? {
    return plainTextItemToReturn
  }
}

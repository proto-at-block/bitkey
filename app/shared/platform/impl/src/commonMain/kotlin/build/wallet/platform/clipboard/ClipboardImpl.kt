package build.wallet.platform.clipboard

import build.wallet.platform.PlatformContext
import build.wallet.platform.clipboard.ClipItem.PlainText

expect class ClipboardImpl constructor(platformContext: PlatformContext) : Clipboard {
  override fun setItem(item: ClipItem)

  override fun getPlainTextItem(): PlainText?

  override fun getPlainTextItemAndroid(): PlainText?
}

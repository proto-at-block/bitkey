package build.wallet.platform.clipboard

import build.wallet.platform.PlatformContext
import build.wallet.platform.clipboard.ClipItem.PlainText

actual class ClipboardImpl actual constructor(
  platformContext: PlatformContext,
) : Clipboard {
  override fun setItem(item: ClipItem) {
    // noop
  }

  override fun getPlainTextItemAndroid(): PlainText? {
    // noop
    return null
  }

  override fun getPlainTextItem(): PlainText? {
    // noop
    return null
  }
}

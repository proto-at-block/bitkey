package build.wallet.platform.clipboard

import build.wallet.platform.PlatformContext
import build.wallet.platform.clipboard.ClipItem.PlainText

actual class ClipboardImpl actual constructor(
  platformContext: PlatformContext,
) : Clipboard {
  actual override fun setItem(item: ClipItem) {
    // noop
  }

  actual override fun getPlainTextItemAndroid(): PlainText? {
    // noop
    return null
  }

  actual override fun getPlainTextItem(): PlainText? {
    // noop
    return null
  }
}

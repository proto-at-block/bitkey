package build.wallet.platform.clipboard

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.clipboard.ClipItem.PlainText
import platform.UIKit.UIPasteboard

@BitkeyInject(AppScope::class)
class ClipboardImpl : Clipboard {
  override fun setItem(item: ClipItem) {
    val pasteboard = UIPasteboard.generalPasteboard
    when (item) {
      is PlainText -> pasteboard.string = item.data
    }
  }

  override fun getPlainTextItem(): PlainText? {
    val pasteboard = UIPasteboard.generalPasteboard
    return pasteboard.string?.let {
      PlainText(it)
    }
  }

  override fun getPlainTextItemAndroid(): PlainText? {
    // no-op
    return null
  }
}

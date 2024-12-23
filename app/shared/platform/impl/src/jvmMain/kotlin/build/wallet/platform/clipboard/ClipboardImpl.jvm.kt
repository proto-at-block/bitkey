package build.wallet.platform.clipboard

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.clipboard.ClipItem.PlainText

@BitkeyInject(AppScope::class)
class ClipboardImpl : Clipboard {
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

package build.wallet.platform.clipboard

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.clipboard.ClipItem.PlainText

@BitkeyInject(AppScope::class)
class ClipboardImpl(
  private val application: Application,
  private val clipboardManager: ClipboardManager,
) : Clipboard {
  override fun setItem(item: ClipItem) {
    val clipData =
      when (item) {
        is PlainText -> ClipData.newPlainText(null, item.data)
      }

    clipboardManager.setPrimaryClip(clipData)
  }

  override fun getPlainTextItem(): PlainText? {
    return clipboardManager.primaryClip?.getItemAt(0)?.let {
      PlainText(it.coerceToText(application).toString())
    }
  }

  override fun getPlainTextItemAndroid(): PlainText? {
    return getPlainTextItem()
  }
}

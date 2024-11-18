package build.wallet.platform.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import build.wallet.platform.PlatformContext
import build.wallet.platform.clipboard.ClipItem.PlainText

actual class ClipboardImpl actual constructor(
  private val platformContext: PlatformContext,
) : Clipboard {
  actual override fun setItem(item: ClipItem) {
    val clipData =
      when (item) {
        is PlainText -> ClipData.newPlainText(null, item.data)
      }

    clipboardManager().setPrimaryClip(clipData)
  }

  actual override fun getPlainTextItem(): PlainText? {
    return clipboardManager().primaryClip?.getItemAt(0)?.let {
      PlainText(it.coerceToText(platformContext.appContext).toString())
    }
  }

  actual override fun getPlainTextItemAndroid(): PlainText? {
    return getPlainTextItem()
  }

  private fun clipboardManager(): ClipboardManager {
    return platformContext.appContext.getSystemService(
      Context.CLIPBOARD_SERVICE
    ) as ClipboardManager
  }
}

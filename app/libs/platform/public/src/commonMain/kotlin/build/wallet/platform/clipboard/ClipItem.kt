package build.wallet.platform.clipboard

/**
 * Describes a clipboard item that can be set in platoforms [Clipboard].
 */
sealed class ClipItem {
  /**
   * Clipboard item holding plain text as [data].
   */
  data class PlainText(val data: String) : ClipItem()
}

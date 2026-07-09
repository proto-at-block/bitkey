package build.wallet.platform.clipboard

/**
 * Describes a clipboard item that can be set in platoforms [Clipboard].
 */
sealed interface ClipItem {
  /**
   * Clipboard item holding plain text as [data].
   */
  data class PlainText(val data: String) : ClipItem
}

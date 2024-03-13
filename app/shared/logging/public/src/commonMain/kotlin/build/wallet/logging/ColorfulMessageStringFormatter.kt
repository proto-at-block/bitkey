package build.wallet.logging

import co.touchlab.kermit.DefaultFormatter
import co.touchlab.kermit.Message
import co.touchlab.kermit.MessageStringFormatter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.Tag

/**
 * A [MessageStringFormatter] based on [XcodeSeverityWriter] that adds colored emoji in place
 * of the severity level.
 */
internal object ColorfulMessageStringFormatter : MessageStringFormatter {
  private val defaultFormatter = DefaultFormatter

  override fun formatMessage(
    severity: Severity?,
    tag: Tag?,
    message: Message,
  ): String = "${emojiPrefix(severity)} ${defaultFormatter.formatMessage(null, tag, message)}"

  private fun emojiPrefix(severity: Severity?): String =
    when (severity) {
      Severity.Verbose -> "⚪️"
      Severity.Debug -> "🔵"
      Severity.Info -> "🟢"
      Severity.Warn -> "🟡"
      Severity.Error -> "🔴"
      Severity.Assert -> "🟤️"
      null -> ""
    }
}

package build.wallet.ui.components.forms

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * A [TextField] [VisualTransformation] to apply 4 character hyphen separated
 * string like `xxxx-xxxx-xxxx`.
 */
internal object InviteCodeTransformation : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    val originalText = text.text
    val formattedCode = originalText
      .chunked(4)
      .joinToString("-")
    return TransformedText(
      AnnotatedString(formattedCode),
      InviteCodeOffsetMapping(originalText)
    )
  }
}

internal class InviteCodeOffsetMapping(
  private val originalText: String,
) : OffsetMapping {
  override fun originalToTransformed(offset: Int): Int {
    val dashesBefore = if (offset <= 4) 0 else (offset - 1) / 4
    return offset + dashesBefore
  }

  override fun transformedToOriginal(offset: Int): Int {
    val dashesBefore = if (offset <= 4) 0 else (offset) / 5
    return (offset - dashesBefore).coerceIn(0, originalText.length)
  }
}

package build.wallet.statemachine.core

/**
 * Wrapper for text models that want to allow either regular
 * or attributed / annotated strings.
 */
sealed interface LabelModel {
  val string: String

  /**
   * Model for a regular string
   */
  data class StringModel(override val string: String) : LabelModel

  data class CalloutModel(override val string: String) : LabelModel

  data class LinkSubstringModel internal constructor(
    override val string: String,
    val underline: Boolean,
    val bold: Boolean,
    val color: Color,
    val linkedSubstrings: List<LinkSubstring>,
  ) : LabelModel {
    data class LinkSubstring(
      val range: IntRange,
      val onClick: () -> Unit,
    )

    @Suppress("unused") // Used in iOS
    fun markdownString(): String {
      val mdStringBuilder = StringBuilder()
      return if (linkedSubstrings.isEmpty()) {
        string
      } else {
        if (linkedSubstrings[0].range.first > 0) {
          mdStringBuilder.append(string.substring(0, linkedSubstrings[0].range.first))
        }
        for (sub in linkedSubstrings.indices) {
          val theRange = linkedSubstrings[sub].range

          mdStringBuilder.append("[${string.substring(theRange)}](ls:$sub)")

          if (linkedSubstrings.size > sub + 1) {
            mdStringBuilder.append(
              string.substring(theRange.last + 1, linkedSubstrings[sub + 1].range.first)
            )
          }
        }
        val endIndex = linkedSubstrings.last().range.last + 1
        if (string.length > endIndex) {
          mdStringBuilder.append(string.substring(endIndex))
        }

        mdStringBuilder.toString()
      }
    }

    companion object {
      fun from(
        string: String,
        substringToOnClick: Map<String, () -> Unit>,
        underline: Boolean,
        bold: Boolean,
        color: Color = Color.PRIMARY,
      ): LinkSubstringModel {
        return LinkSubstringModel(
          string = string,
          linkedSubstrings =
            substringToOnClick.entries.map { entry ->
              val indexOfSubstring = string.indexOf(entry.key)
              LinkSubstring(
                range = indexOfSubstring..<indexOfSubstring + entry.key.length,
                onClick = entry.value
              )
            },
          underline = underline,
          bold = bold,
          color = color
        )
      }
    }
  }

  /**
   * Model for a string that should have a different treatment in a substring.
   *
   * It should be implemented as [AnnotatedString] on Android
   * and [AttributedString] on iOS
   */
  data class StringWithStyledSubstringModel(
    override val string: String,
    val styledSubstrings: List<StyledSubstring>,
  ) : LabelModel {
    sealed interface SubstringStyle {
      data object BoldStyle : SubstringStyle

      data class ColorStyle(val color: Color) : SubstringStyle

      data class FontFeatureStyle(val fontFeatureSettings: String) : SubstringStyle
    }

    data class StyledSubstring(
      val range: IntRange,
      val style: SubstringStyle,
    )

    companion object {
      fun from(
        string: String,
        substringToColor: Map<String, Color>,
      ): StringWithStyledSubstringModel {
        return StringWithStyledSubstringModel(
          string = string,
          styledSubstrings =
            substringToColor.entries.map { entry ->
              val indexOfSubstring = string.indexOf(entry.key)
              StyledSubstring(
                range = indexOfSubstring..<indexOfSubstring + entry.key.length,
                style = SubstringStyle.ColorStyle(entry.value)
              )
            }
        )
      }

      fun from(
        string: String,
        boldedSubstrings: List<String>,
      ): StringWithStyledSubstringModel {
        return StringWithStyledSubstringModel(
          string = string,
          styledSubstrings =
            boldedSubstrings.map { boldedSubstring ->
              val indexOfSubstring = string.indexOf(boldedSubstring)
              StyledSubstring(
                range = indexOfSubstring..<indexOfSubstring + boldedSubstring.length,
                style = SubstringStyle.BoldStyle
              )
            }
        )
      }
    }
  }

  /**
   * The color to be used for a styled substring.
   */
  enum class Color {
    GREEN,
    BLUE,
    ON60,
    PRIMARY,

    /**
     * When selected, the color of the styled substring prefers that of the remaining text. This can be
     * useful if you want to have a substring link that matches its color with the rest of the string
     * instead of using a different color.
     */
    UNSPECIFIED,
  }
}

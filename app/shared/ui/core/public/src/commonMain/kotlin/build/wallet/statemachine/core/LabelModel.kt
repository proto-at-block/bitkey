package build.wallet.statemachine.core

/**
 * Wrapper for text models that want to allow either regular
 * or attributed / annotated strings.
 */
sealed interface LabelModel {
  /**
   * Model for a regular string
   */
  data class StringModel(val string: String) : LabelModel

  /**
   * Model for a string that should have a different treatment in a substring.
   *
   * It should be implemented as [AnnotatedString] on Android
   * and [AttributedString] on iOS
   */
  data class StringWithStyledSubstringModel internal constructor(
    val string: String,
    val styledSubstrings: List<StyledSubstring>,
  ) : LabelModel {
    enum class Color {
      GREEN,
      BLUE,
      ON60,
    }

    sealed interface SubstringStyle {
      data object BoldStyle : SubstringStyle

      data class ColorStyle(val color: Color) : SubstringStyle
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
}

@file:Suppress("TooManyFunctions")

package build.wallet.ui.compose

import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel

private val camelCaseBoundaryRegex = "(?<=[a-z0-9])(?=[A-Z])".toRegex()
private val invalidTagCharacterRegex = "[^a-z0-9\\s_-]+".toRegex()
private val tagSeparatorRegex = "[\\s_]+".toRegex()
private val repeatedHyphenRegex = "-+".toRegex()

/**
 * Uses [fallbackTag] when [testTag] is null or blank.
 */
fun resolveTestTag(
  testTag: String?,
  fallbackTag: String,
): String = testTag?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackTag

fun buttonTestTag(text: String): String =
  "${normalizeTestTagValue(text, fallback = "action")}-button"

fun listItemTestTag(title: String): String =
  "${normalizeTestTagValue(title, fallback = "item")}-list-item"

fun switchTestTag(descriptor: String = "toggle"): String =
  "switch-${normalizeTestTagValue(descriptor, fallback = "toggle")}"

fun textFieldTestTag(placeholderText: String): String =
  "text-field-${normalizeTestTagValue(placeholderText, fallback = "input")}"

fun datePickerTestTag(valueRepresentation: String): String =
  "date-picker-${normalizeTestTagValue(valueRepresentation, fallback = "value")}"

fun itemPickerTestTag(selectedOptionTitle: String): String =
  "item-picker-${normalizeTestTagValue(selectedOptionTitle, fallback = "selection")}"

fun itemPickerOptionTestTag(optionTitle: String): String =
  "item-picker-option-${normalizeTestTagValue(optionTitle, fallback = "option")}"

fun iconButtonTestTag(
  text: String?,
  iconDescriptor: String,
): String {
  val descriptor = text?.takeIf { it.isNotBlank() } ?: iconDescriptor
  return "icon-button-${normalizeTestTagValue(descriptor, fallback = "icon")}"
}

fun IconModel.testTagDescriptor(): String =
  when (val iconImage = iconImage) {
    is IconImage.LocalImage -> iconImage.icon.name
    is IconImage.DrawableResourceImage -> "drawable-resource"
    is IconImage.UrlImage -> iconImage.fallbackIcon.name
    IconImage.LoadingBadge -> "loading-badge"
  }

fun normalizeTestTagValue(
  value: String,
  fallback: String,
): String {
  val normalized =
    value
      .trim()
      .replace(camelCaseBoundaryRegex, " ")
      .lowercase()
      .replace(invalidTagCharacterRegex, "")
      .replace(tagSeparatorRegex, "-")
      .replace(repeatedHyphenRegex, "-")
      .trim('-')

  return normalized.ifEmpty { fallback }
}

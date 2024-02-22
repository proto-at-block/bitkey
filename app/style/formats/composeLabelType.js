/**
 * This custom format creates a dictionary of Compose UI Colors
 * for light and dark mode based on the design tokens.
*/
module.exports = function({ dictionary }) {
  return `// DO NOT EDIT.
// This file is generated from design tokens in the wallet/style folder.

package build.wallet.ui.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import build.wallet.ui.typography.font.robotoMonoFontFamily

enum class LabelType {\n` +
  Object.keys(dictionary.properties.font.styles)
    .map(style => {
      return "  " + toPascalCase(style)
    })
    .reduce((text, value) => text + ',\n' + value)
    + ',\n' +
`}

fun LabelType.style(baseStyle: TextStyle) =\n  when (this) {\n` +
  Object.keys(dictionary.properties.font.styles)
  .map(style => {
    return "    " + labelToStyleDefinition(style, dictionary)
  }).join("\n") + '\n' +
`  }
`
}

const toPascalCase = str => (str.match(/[a-zA-Z0-9]+/g) || [])
  .map(w => `${w.charAt(0).toUpperCase()}${w.slice(1)}`)
  .join('');

const labelToStyleDefinition = (style, dictionary) => {
  switch(dictionary.properties.font.styles[style].name.value) {
    case "RobotoMono-Regular": 
    return `LabelType.` + toPascalCase(style) + ` ->
      baseStyle.copy(
        fontFamily = robotoMonoFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = ` + dictionary.properties.font.styles[style].size.value + `.sp,
        lineHeight = ` + dictionary.properties.font.styles[style].lineHeight.value + `.sp,
        letterSpacing = (` + dictionary.properties.font.styles[style].kerning.value + `).sp,
      )`

    default: 
    return `LabelType.` + toPascalCase(style) + ` ->
      baseStyle.copy(
        fontWeight = FontWeight.W` + interNameToFontWeight(dictionary.properties.font.styles[style].name.value) + `,
        fontSize = ` + dictionary.properties.font.styles[style].size.value + `.sp,
        lineHeight = ` + dictionary.properties.font.styles[style].lineHeight.value + `.sp,
        letterSpacing = (` + dictionary.properties.font.styles[style].kerning.value + `).sp,
      )`
  }
}

const interNameToFontWeight = str => {
  switch(str) {
    case "Inter-Regular": return 400;
    case "Inter-Medium": return 500;
    case "Inter-SemiBold": return 600;
    case "Inter-Bold": return 700;
    case "RobotoMono-Regular": return 400;
    default: return 400;
  }
}

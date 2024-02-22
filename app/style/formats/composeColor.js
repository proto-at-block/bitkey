/**
 * This custom format creates a dictionary of Compose UI Colors
 * for light and dark mode based on the design tokens.
*/
module.exports = function({ dictionary }) {
  return `// DO NOT EDIT.
// This file is generated from design tokens in the wallet/style folder.

package build.wallet.ui.tokens

import androidx.compose.ui.graphics.Color

interface StyleDictionaryColors {
${Object.keys(dictionary.properties.color).map(color => {
  return `  // ${dictionary.properties.color[color].light.comment}
  val ${color}: Color`
}).join(`\n\n`)}
}

val lightStyleDictionaryColors =
  object : StyleDictionaryColors {
${Object.keys(dictionary.properties.color).map(color => {
  return `    override val ${color}: Color = ${dictionary.properties.color[color].light.value}`
}).join(`\n`)}
  }

val darkStyleDictionaryColors =
  object : StyleDictionaryColors {
${Object.keys(dictionary.properties.color).map(color => {
  return `    override val ${color}: Color = ${dictionary.properties.color[color].dark.value}`
}).join(`\n`)}
  }
`
}

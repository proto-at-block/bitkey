/**
 * This custom format switches on enum cases based off of shared icon tokens.
*/
module.exports = function({ dictionary }) {
  return `// DO NOT EDIT.
// This file is generated from design tokens in the wallet/style folder.

package build.wallet.ui.tokens

import androidx.compose.runtime.Composable
import bitkey.shared.ui_core_public.generated.resources.*
import bitkey.shared.ui_core_public.generated.resources.Res
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import build.wallet.statemachine.core.Icon\n` +
dictionary.properties.icons.value.map(icon => {
  return `import build.wallet.statemachine.core.Icon.${icon.charAt(0).toUpperCase()}${icon.substring(1)}`
}).join(`\n`) + `

@Composable
fun Icon.painter() = painterResource(drawableRes)

private val Icon.drawableRes: DrawableResource
  get() =
    when (this) {\n` +
      dictionary.properties.icons.value.map(icon => {
        return `      ${icon.charAt(0).toUpperCase()}${icon.substring(1)} -> Res.drawable.${icon.replace(/([a-z0-9])([A-Z0-9])/g, '$1_$2').toLowerCase()}`
      }).join(`\n`) 
      + `\n    }\n`
}

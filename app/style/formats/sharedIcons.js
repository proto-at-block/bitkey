/**
 * This custom format creates enum cases based off of shared icon tokens.
*/
module.exports = function({ dictionary }) {
  return `// DO NOT EDIT.
// This file is generated from design tokens in the wallet/style folder.

package build.wallet.statemachine.core

/**
 * Model for describing UI icons.
 *
 * Each platform should map this model to UI specific implementation.
 *
 * Icons are defined in [this Figma](https://www.figma.com/file/aaOrQTgHXp2NpOYCBDoe5E/Wallet-System?node-id=1657%3A1943&t=AcSWo5OhyT308AVA-1).
 * This file is generated from design tokens in the wallet/style folder.
 */
enum class Icon {\n` +
  dictionary.properties.icons.value.map(icon => {
    return `  ${icon.charAt(0).toUpperCase()}${icon.substring(1)},`
  }).join(`\n`) + '\n}\n'
}

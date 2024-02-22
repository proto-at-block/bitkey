/**
 * This custom format creates an extension of the UIKit UIColor and SwiftUI Color
 * classes and adds all the color tokens as static variables so that
 * you can reference a color token like: `UIColor.primary` or `Color.primary`.
 */

module.exports = function({ dictionary }) {
  return `//
// DO NOT EDIT.
// This file is generated from design tokens in the wallet/style folder.
// Run \`npm run build\` to regenerate.
import SwiftUI\nimport UIKit

extension UIColor {\n` +
  Object.keys(dictionary.properties.color).map(color => {
    return `  // ${dictionary.properties.color[color].light.comment}
  public static let ${color} = UIColor(
    light: ${dictionary.properties.color[color].light.value},
    dark: ${dictionary.properties.color[color].dark.value}
  )`
  }).join(`\n`) +
  `\n}\n`
  + `
extension Color {\n` +
    Object.keys(dictionary.properties.color).map(color => {
      return `  public static let ${color} = Color(from: .${color})`
    }).join(`\n`) +
    `\n}\n`
}

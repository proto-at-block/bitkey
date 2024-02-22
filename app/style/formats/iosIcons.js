/**
 * This custom format creates an extension of UIKit UIImage and shared (KMP) Icon
 * classes and adds all the icon tokens as static variables so that you can
 * reference an image like: `UIImage.loading`
 */

module.exports = function({ dictionary }) {
  return `//
// DO NOT EDIT.
// This file is generated from design tokens in the wallet/style folder.
// Run \`npm run build\` to regenerate.
import Shared\nimport UIKit

public extension UIImage {\n` +
  dictionary.properties.icons.value.map(icon => {
    return `  static let ${icon} = UIImage(named: "${icon.charAt(0).toUpperCase()}${icon.substring(1)}", in: .main, compatibleWith: nil)!`
  }).join(`\n`) +
  `\n}\n`
  + `

// MARK: -

public extension Icon {\n` +
    `  var uiImage: UIImage {\n` +
    `        switch self {\n` +
    dictionary.properties.icons.value.map(icon => {
      return `        case .${icon.toLowerCase()}: return .${icon}`
    }).join(`\n`) + `\n` +
    `        default: fatalError("Unimplemented shared icon: \\(self)")\n` +
    `        }\n` +
    `  }\n}`
}

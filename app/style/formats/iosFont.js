/**
 * This custom format creates an extension of the UIKit UIFont and SwiftUI Font
 * classes and adds all the font tokens as static variables so that
 * you can reference a font token like: `UIFont.display` or `Font.display`.
 */

module.exports = function({ dictionary }) {
  return `//
// DO NOT EDIT.
// This file is generated from design tokens in the wallet/style folder.
// Run \`npm run build\` to regenerate.
import SwiftUI\nimport UIKit

public enum FontName: String, CaseIterable {\n` +
  Object.keys(dictionary.properties.font.names).map(fontName => {
    return `  case ${fontName} = ${dictionary.properties.font.names[fontName].value}`
  }).join(`\n`)
    +
    `\n}\n
extension UIFontTheme {\n` +
  Object.keys(dictionary.properties.font.styles).map(fontStyle => {
    return `  public static let ${fontStyle} = UIFontTheme(name: ${dictionary.properties.font.styles[fontStyle].name.value}, size: ${dictionary.properties.font.styles[fontStyle].size.value}, lineHeight: ${dictionary.properties.font.styles[fontStyle].lineHeight.value}, kerning: ${dictionary.properties.font.styles[fontStyle].kerning.value})`
  }).join(`\n`) +
  `\n}\n`
  + `
extension FontTheme {\n` +
  Object.keys(dictionary.properties.font.styles).map(fontStyle => {
    return `  public static let ${fontStyle} = FontTheme(name: ${dictionary.properties.font.styles[fontStyle].name.value}, size: ${dictionary.properties.font.styles[fontStyle].size.value}, lineHeight: ${dictionary.properties.font.styles[fontStyle].lineHeight.value}, kerning: ${dictionary.properties.font.styles[fontStyle].kerning.value})`
  }).join(`\n`) +
    `\n}\n`
}

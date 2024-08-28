//
// DO NOT EDIT.
// This file is generated from design tokens in the wallet/style folder.
// Run `npm run build` to regenerate.
import SwiftUI
import UIKit

public enum FontName: String, CaseIterable {
  case Inter400 = "Inter-Regular"
  case Inter500 = "Inter-Medium"
  case Inter600 = "Inter-SemiBold"
  case Inter700 = "Inter-Bold"
  case Mono = "RobotoMono-Regular"
}

extension UIFontTheme {
  public static let display1 = UIFontTheme(name: "Inter-Bold", size: "64", lineHeight: "76", kerning: "-1.43")
  public static let display2 = UIFontTheme(name: "Inter-SemiBold", size: "48", lineHeight: "60", kerning: "-1.07")
  public static let display3 = UIFontTheme(name: "Inter-Medium", size: "32", lineHeight: "46", kerning: "-1.07")
  public static let title1 = UIFontTheme(name: "Inter-SemiBold", size: "24", lineHeight: "32", kerning: "-0.47")
  public static let title2 = UIFontTheme(name: "Inter-SemiBold", size: "17", lineHeight: "24", kerning: "-0.22")
  public static let title3 = UIFontTheme(name: "Inter-Medium", size: "14", lineHeight: "18", kerning: "-0.09")
  public static let body1Regular = UIFontTheme(name: "Inter-Regular", size: "20", lineHeight: "30", kerning: "-0.33")
  public static let body1Medium = UIFontTheme(name: "Inter-Medium", size: "20", lineHeight: "30", kerning: "-0.33")
  public static let body1Bold = UIFontTheme(name: "Inter-SemiBold", size: "20", lineHeight: "30", kerning: "-0.33")
  public static let body2Regular = UIFontTheme(name: "Inter-Regular", size: "17", lineHeight: "24", kerning: "-0.22")
  public static let body2Medium = UIFontTheme(name: "Inter-Medium", size: "17", lineHeight: "24", kerning: "-0.22")
  public static let body2Bold = UIFontTheme(name: "Inter-SemiBold", size: "17", lineHeight: "24", kerning: "-0.22")
  public static let body2Mono = UIFontTheme(name: "RobotoMono-Regular", size: "17", lineHeight: "24", kerning: "0")
  public static let body2Link = UIFontTheme(name: "Inter-SemiBold", size: "17", lineHeight: "20", kerning: "-0.22")
  public static let body3Regular = UIFontTheme(name: "Inter-Regular", size: "15", lineHeight: "20", kerning: "-0.13")
  public static let body3Medium = UIFontTheme(name: "Inter-Medium", size: "15", lineHeight: "20", kerning: "-0.13")
  public static let body3Bold = UIFontTheme(name: "Inter-SemiBold", size: "15", lineHeight: "18", kerning: "-0.13")
  public static let body3Link = UIFontTheme(name: "Inter-SemiBold", size: "15", lineHeight: "20", kerning: "-0.13")
  public static let body3Mono = UIFontTheme(name: "RobotoMono-Regular", size: "15", lineHeight: "24", kerning: "0")
  public static let body4Regular = UIFontTheme(name: "Inter-Regular", size: "13", lineHeight: "18", kerning: "-0.13")
  public static let body4Medium = UIFontTheme(name: "Inter-Medium", size: "13", lineHeight: "18", kerning: "-0.13")
  public static let label1 = UIFontTheme(name: "Inter-SemiBold", size: "16", lineHeight: "24", kerning: "-0.18")
  public static let label2 = UIFontTheme(name: "Inter-SemiBold", size: "14", lineHeight: "24", kerning: "-0.09")
  public static let label3 = UIFontTheme(name: "Inter-SemiBold", size: "13", lineHeight: "14", kerning: "-0.04")
  public static let keypad = UIFontTheme(name: "Inter-Bold", size: "24", lineHeight: "48", kerning: "-0.47")
}

extension FontTheme {
  public static let display1 = FontTheme(name: "Inter-Bold", size: "64", lineHeight: "76", kerning: "-1.43")
  public static let display2 = FontTheme(name: "Inter-SemiBold", size: "48", lineHeight: "60", kerning: "-1.07")
  public static let display3 = FontTheme(name: "Inter-Medium", size: "32", lineHeight: "46", kerning: "-1.07")
  public static let title1 = FontTheme(name: "Inter-SemiBold", size: "24", lineHeight: "32", kerning: "-0.47")
  public static let title2 = FontTheme(name: "Inter-SemiBold", size: "17", lineHeight: "24", kerning: "-0.22")
  public static let title3 = FontTheme(name: "Inter-Medium", size: "14", lineHeight: "18", kerning: "-0.09")
  public static let body1Regular = FontTheme(name: "Inter-Regular", size: "20", lineHeight: "30", kerning: "-0.33")
  public static let body1Medium = FontTheme(name: "Inter-Medium", size: "20", lineHeight: "30", kerning: "-0.33")
  public static let body1Bold = FontTheme(name: "Inter-SemiBold", size: "20", lineHeight: "30", kerning: "-0.33")
  public static let body2Regular = FontTheme(name: "Inter-Regular", size: "17", lineHeight: "24", kerning: "-0.22")
  public static let body2Medium = FontTheme(name: "Inter-Medium", size: "17", lineHeight: "24", kerning: "-0.22")
  public static let body2Bold = FontTheme(name: "Inter-SemiBold", size: "17", lineHeight: "24", kerning: "-0.22")
  public static let body2Mono = FontTheme(name: "RobotoMono-Regular", size: "17", lineHeight: "24", kerning: "0")
  public static let body2Link = FontTheme(name: "Inter-SemiBold", size: "17", lineHeight: "20", kerning: "-0.22")
  public static let body3Regular = FontTheme(name: "Inter-Regular", size: "15", lineHeight: "20", kerning: "-0.13")
  public static let body3Medium = FontTheme(name: "Inter-Medium", size: "15", lineHeight: "20", kerning: "-0.13")
  public static let body3Bold = FontTheme(name: "Inter-SemiBold", size: "15", lineHeight: "18", kerning: "-0.13")
  public static let body3Link = FontTheme(name: "Inter-SemiBold", size: "15", lineHeight: "20", kerning: "-0.13")
  public static let body3Mono = FontTheme(name: "RobotoMono-Regular", size: "15", lineHeight: "24", kerning: "0")
  public static let body4Regular = FontTheme(name: "Inter-Regular", size: "13", lineHeight: "18", kerning: "-0.13")
  public static let body4Medium = FontTheme(name: "Inter-Medium", size: "13", lineHeight: "18", kerning: "-0.13")
  public static let label1 = FontTheme(name: "Inter-SemiBold", size: "16", lineHeight: "24", kerning: "-0.18")
  public static let label2 = FontTheme(name: "Inter-SemiBold", size: "14", lineHeight: "24", kerning: "-0.09")
  public static let label3 = FontTheme(name: "Inter-SemiBold", size: "13", lineHeight: "14", kerning: "-0.04")
  public static let keypad = FontTheme(name: "Inter-Bold", size: "24", lineHeight: "48", kerning: "-0.47")
}

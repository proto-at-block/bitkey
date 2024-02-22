// DO NOT EDIT.
// This file is generated from design tokens in the wallet/style folder.

package build.wallet.ui.tokens

import androidx.compose.ui.graphics.Color

interface StyleDictionaryColors {
  // The default background for the app
  val background: Color

  // The background when the view has containers
  val groupedBackground: Color

  // The default background for a container
  val containerBackground: Color

  // The background for a container that needs to draw attention
  val containerBackgroundHighlight: Color

  // foreground blue used on a highlighted container
  val containerHighlightForeground: Color

  // The main foreground color for things like text
  val foreground: Color

  // The secondary foreground color for things like secondary text
  val foreground60: Color

  // used for chevrons
  val foreground30: Color

  // used for borders and dividers
  val foreground10: Color

  // The primary color for buttons, switches, etc
  val primary: Color

  // The foreground color for things on top of the primary color like text and icons
  val primaryForeground: Color

  // The disabled foreground color for things on top of the primary color like text and icons
  val primaryForeground30: Color

  // The background color for secondary buttons
  val secondary: Color

  // Used as the foreground color for things on top of the secondary button like text and icons
  val secondaryForeground: Color

  // The disabled foreground color for things on top of the secondary button like text and icons
  val secondaryForeground30: Color

  // The primary color for icons
  val primaryIcon: Color

  // The primary foreground color for icons
  val primaryIconForeground: Color

  // The secondary color for icons
  val secondaryIcon: Color

  // The secondary foreground color for icons
  val secondaryIconForeground: Color

  // The background color for normal (20%) translucent buttons
  val translucentButton20: Color

  // The background color for lighter (10%) translucent buttons
  val translucentButton10: Color

  // The foreground color for translucent buttons
  val translucentForeground: Color

  // Mask color
  val mask: Color

  // Positive banner foreground color
  val positive: Color

  // Positive color
  val positiveForeground: Color

  // Destructive color
  val destructive: Color

  // Destructive foreground color
  val destructiveForeground: Color

  // Warning banner background color
  val warning: Color

  // Warning foreground color
  val warningForeground: Color

  // Device LED green color
  val deviceLEDGreen: Color

  // Device LED red color
  val deviceLEDRed: Color

  // Device LED blue color
  val deviceLEDBlue: Color

  // Device LED white color
  val deviceLEDWhite: Color

  // nfc blue
  val nfcBlue: Color

  // Background color for the initial landing screen before any setup
  val bitkeyGetStartedBackground: Color

  // Tint color for the logo and text on the initial landing screen before any setup
  val bitkeyGetStartedTint: Color

  // Color for the out of date icon in the app status screen
  val outOfDate: Color
}

val lightStyleDictionaryColors =
  object : StyleDictionaryColors {
    override val background: Color = Color(0xffffffff)
    override val groupedBackground: Color = Color(0xffffffff)
    override val containerBackground: Color = Color(0xffffffff)
    override val containerBackgroundHighlight: Color = Color(0xfff5f8fe)
    override val containerHighlightForeground: Color = Color(0xffa5c6ff)
    override val foreground: Color = Color(0xff111111)
    override val foreground60: Color = Color(0xff666666)
    override val foreground30: Color = Color(0xffc6c6c6)
    override val foreground10: Color = Color(0xfff6f6f6)
    override val primary: Color = Color(0xff008096)
    override val primaryForeground: Color = Color(0xffffffff)
    override val primaryForeground30: Color = Color(0xffc6c6c6)
    override val secondary: Color = Color(0x0a000000)
    override val secondaryForeground: Color = Color(0xff111111)
    override val secondaryForeground30: Color = Color(0xffc6c6c6)
    override val primaryIcon: Color = Color(0xff111111)
    override val primaryIconForeground: Color = Color(0xffffffff)
    override val secondaryIcon: Color = Color(0x0d000000)
    override val secondaryIconForeground: Color = Color(0xff111111)
    override val translucentButton20: Color = Color(0x33ffffff)
    override val translucentButton10: Color = Color(0x1affffff)
    override val translucentForeground: Color = Color(0xffffffff)
    override val mask: Color = Color(0x99111111)
    override val positive: Color = Color(0x0d009337)
    override val positiveForeground: Color = Color(0xff008000)
    override val destructive: Color = Color(0xffff0000)
    override val destructiveForeground: Color = Color(0xffca0000)
    override val warning: Color = Color(0xfffff5f0)
    override val warningForeground: Color = Color(0xfff46e38)
    override val deviceLEDGreen: Color = Color(0xff008000)
    override val deviceLEDRed: Color = Color(0xffff0000)
    override val deviceLEDBlue: Color = Color(0xff0059ff)
    override val deviceLEDWhite: Color = Color(0xffffffff)
    override val nfcBlue: Color = Color(0xff3d81ff)
    override val bitkeyGetStartedBackground: Color = Color(0xff201e22)
    override val bitkeyGetStartedTint: Color = Color(0xfff8f4e4)
    override val outOfDate: Color = Color(0xffeca900)
  }

val darkStyleDictionaryColors =
  object : StyleDictionaryColors {
    override val background: Color = Color(0xffffffff)
    override val groupedBackground: Color = Color(0xffffffff)
    override val containerBackground: Color = Color(0xffffffff)
    override val containerBackgroundHighlight: Color = Color(0xfff5f8fe)
    override val containerHighlightForeground: Color = Color(0xffa5c6ff)
    override val foreground: Color = Color(0xff111111)
    override val foreground60: Color = Color(0xff666666)
    override val foreground30: Color = Color(0xffc6c6c6)
    override val foreground10: Color = Color(0xfff6f6f6)
    override val primary: Color = Color(0xff008096)
    override val primaryForeground: Color = Color(0xffffffff)
    override val primaryForeground30: Color = Color(0xffc6c6c6)
    override val secondary: Color = Color(0x0a000000)
    override val secondaryForeground: Color = Color(0xff111111)
    override val secondaryForeground30: Color = Color(0xffc6c6c6)
    override val primaryIcon: Color = Color(0xff111111)
    override val primaryIconForeground: Color = Color(0xffffffff)
    override val secondaryIcon: Color = Color(0x0d000000)
    override val secondaryIconForeground: Color = Color(0xff111111)
    override val translucentButton20: Color = Color(0x33ffffff)
    override val translucentButton10: Color = Color(0x1affffff)
    override val translucentForeground: Color = Color(0xffffffff)
    override val mask: Color = Color(0x99111111)
    override val positive: Color = Color(0x0d009337)
    override val positiveForeground: Color = Color(0xff008000)
    override val destructive: Color = Color(0xffff0000)
    override val destructiveForeground: Color = Color(0xffca0000)
    override val warning: Color = Color(0xfffff5f0)
    override val warningForeground: Color = Color(0xfff46e38)
    override val deviceLEDGreen: Color = Color(0xff008000)
    override val deviceLEDRed: Color = Color(0xffff0000)
    override val deviceLEDBlue: Color = Color(0xff0059ff)
    override val deviceLEDWhite: Color = Color(0xffffffff)
    override val nfcBlue: Color = Color(0xff3d81ff)
    override val bitkeyGetStartedBackground: Color = Color(0xff201e22)
    override val bitkeyGetStartedTint: Color = Color(0xfff8f4e4)
    override val outOfDate: Color = Color(0xffeca900)
  }

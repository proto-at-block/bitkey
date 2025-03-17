package build.wallet.ui.tokens

import androidx.compose.ui.graphics.Color

interface StyleDictionaryColors {
  // The default background for the app
  val background: Color

  // Accent color for dark backgrounds
  val accentDarkBackground: Color

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
  val bitkeyPrimary: Color

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

  // Danger color
  val danger: Color

  // Danger background color
  val dangerBackground: Color

  // Danger button text color
  val dangerButtonText: Color

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

  // Default callout title color
  val calloutDefaultTitle: Color

  // Default callout subtitle color
  val calloutDefaultSubtitle: Color

  // Default callout background color
  val calloutDefaultBackground: Color

  // Default callout title color
  val calloutDefaultTrailingIcon: Color

  // Default callout title color
  val calloutDefaultTrailingIconBackground: Color

  // Information callout title color
  val calloutInformationTitle: Color

  // Information callout subtitle color
  val calloutInformationSubtitle: Color

  // Information callout background color
  val calloutInformationBackground: Color

  // Information callout icon color
  val calloutInformationLeadingIcon: Color

  // Information callout trailing icon color
  val calloutInformationTrailingIcon: Color

  // Information callout trailing icon background color
  val calloutInformationTrailingIconBackground: Color

  // Success callout title color
  val calloutSuccessTitle: Color

  // Success callout subtitle color
  val calloutSuccessSubtitle: Color

  // Success callout background color
  val calloutSuccessBackground: Color

  // Success callout trailing icon color
  val calloutSuccessTrailingIcon: Color

  // Success callout trailing icon background color
  val calloutSuccessTrailingIconBackground: Color

  // Warning callout title color
  val calloutWarningTitle: Color

  // Warning callout subtitle color
  val calloutWarningSubtitle: Color

  // Warning callout background color
  val calloutWarningBackground: Color

  // Warning callout trailing icon color
  val calloutWarningTrailingIcon: Color

  // Warning callout trailing icon background color
  val calloutWarningTrailingIconBackground: Color

  // Danger callout title color
  val calloutDangerTitle: Color

  // Danger callout subtitle color
  val calloutDangerSubtitle: Color

  // Danger callout trailing icon color
  val calloutDangerTrailingIcon: Color

  // Danger callout background color
  val calloutDangerBackground: Color

  // Coachmark background color
  val coachmarkBackground: Color

  // Surface color for inheritance graphics
  val inheritanceSurface: Color

  // Primary color used for Bitcoin elements
  val bitcoinPrimary: Color

  // Primary color used for 'Your balance' elements
  val yourBalancePrimary: Color

  // Background fill for elements while in loading state
  val loadingBackground: Color

  // Chart foreground element color (y-axis labels, dividers, etc.)
  val chartElement: Color

  // The color used for incomplete labels on the stepper indicator.
  val stepperIncompleteLabel: Color

  // The color used for sections of the stepper indicator that are incomplete/not in progress.
  val stepperIncomplete: Color

  // Loading indicator background matching the bitkey texture color.
  val bitkeyLoading: Color

  // The color used for sections of the stepper indicator that are incomplete/not in progress.
  val surfaceMarigold: Color

  // Color used for onboarding button background.
  val surfaceCorian: Color

  // Color used for onboarding button background.
  val grayscale20: Color

  // Color used for white in dark mode and black in light mode
  val inverseBackground: Color

  // Background color for cloud backup icons
  val primaryIconBackground: Color

  // Background color for new coachmark in Settings
  val newCoachmarkLightBackground: Color

  // Text color for new coachmark in Settings
  val newCoachmarkLightText: Color
}

val lightStyleDictionaryColors =
  object : StyleDictionaryColors {
    override val background: Color = Color(0xffffffff)
    override val accentDarkBackground: Color = Color(0xff333442)
    override val groupedBackground: Color = Color(0xffffffff)
    override val containerBackground: Color = Color(0xffffffff)
    override val containerBackgroundHighlight: Color = Color(0xfff5f8fe)
    override val containerHighlightForeground: Color = Color(0xffa5c6ff)
    override val foreground: Color = Color(0xff111111)
    override val foreground60: Color = Color(0xff666666)
    override val foreground30: Color = Color(0xffc6c6c6)
    override val foreground10: Color = Color(0xfff6f6f6)
    override val bitkeyPrimary: Color = Color(0xff008096)
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
    override val danger: Color = Color(0xffe14425)
    override val dangerBackground: Color = Color(0x1ae14425)
    override val dangerButtonText: Color = Color(0xffe14425)
    override val deviceLEDGreen: Color = Color(0xff008000)
    override val deviceLEDRed: Color = Color(0xffff0000)
    override val deviceLEDBlue: Color = Color(0xff0059ff)
    override val deviceLEDWhite: Color = Color(0xffffffff)
    override val nfcBlue: Color = Color(0xff3d81ff)
    override val bitkeyGetStartedBackground: Color = Color(0xff201e22)
    override val bitkeyGetStartedTint: Color = Color(0xfff8f4e4)
    override val outOfDate: Color = Color(0xffeca900)
    override val calloutDefaultTitle: Color = Color(0xe5000000)
    override val calloutDefaultSubtitle: Color = Color(0xb2000000)
    override val calloutDefaultBackground: Color = Color(0xfff2f2f2)
    override val calloutDefaultTrailingIcon: Color = Color(0xffffffff)
    override val calloutDefaultTrailingIconBackground: Color = Color(0xff000000)
    override val calloutInformationTitle: Color = Color(0xff004b73)
    override val calloutInformationSubtitle: Color = Color(0xff004b73)
    override val calloutInformationBackground: Color = Color(0x1a0c9ada)
    override val calloutInformationLeadingIcon: Color = Color(0xff2690c7)
    override val calloutInformationTrailingIcon: Color = Color(0xffffffff)
    override val calloutInformationTrailingIconBackground: Color = Color(0xff0c9ada)
    override val calloutSuccessTitle: Color = Color(0xff0b5c1f)
    override val calloutSuccessSubtitle: Color = Color(0xff0b5c1f)
    override val calloutSuccessBackground: Color = Color(0x1a90c300)
    override val calloutSuccessTrailingIcon: Color = Color(0xffffffff)
    override val calloutSuccessTrailingIconBackground: Color = Color(0xff90c300)
    override val calloutWarningTitle: Color = Color(0xff874900)
    override val calloutWarningSubtitle: Color = Color(0xff874900)
    override val calloutWarningBackground: Color = Color(0x1afd8a00)
    override val calloutWarningTrailingIcon: Color = Color(0xffffffff)
    override val calloutWarningTrailingIconBackground: Color = Color(0xfffd8a00)
    override val calloutDangerTitle: Color = Color(0xff74140d)
    override val calloutDangerSubtitle: Color = Color(0xff74140d)
    override val calloutDangerTrailingIcon: Color = Color(0xffffffff)
    override val calloutDangerBackground: Color = Color(0x1ae14425)
    override val coachmarkBackground: Color = Color(0xff333442)
    override val inheritanceSurface: Color = Color(0xffcfc4cd)
    override val bitcoinPrimary: Color = Color(0xfff7931a)
    override val yourBalancePrimary: Color = Color(0xff0c9ada)
    override val loadingBackground: Color = Color(0xfff4f4f4)
    override val chartElement: Color = Color(0xffe5e5e5)
    override val stepperIncompleteLabel: Color = Color(0xffb2b2b2)
    override val stepperIncomplete: Color = Color(0xfff2f2f2)
    override val bitkeyLoading: Color = Color(0xff575662)
    override val surfaceMarigold: Color = Color(0xfffdbb81)
    override val surfaceCorian: Color = Color(0xff333442)
    override val grayscale20: Color = Color(0x33333442)
    override val inverseBackground: Color = Color(0xff000000)
    override val primaryIconBackground: Color = Color(0x33008096)
    override val newCoachmarkLightBackground: Color = Color(0x1A008096)
    override val newCoachmarkLightText: Color = Color(0xFF008096)
  }

val darkStyleDictionaryColors =
  object : StyleDictionaryColors {
    override val background: Color = Color(0xFF000000)
    override val accentDarkBackground: Color = Color(0xFF333442)
    override val groupedBackground: Color = Color(0xFF000000)
    override val containerBackground: Color = Color(0xFF000000)
    override val containerBackgroundHighlight: Color = Color(0xFFF5F8FE)
    override val containerHighlightForeground: Color = Color(0xFFA5C6FF)
    override val foreground: Color = Color(0xFFE5E5E5)
    override val foreground60: Color = Color(0xFF999999)
    override val foreground30: Color = Color(0xFF999999)
    override val foreground10: Color = Color(0xFF232323)
    override val bitkeyPrimary: Color = Color(0xFF008096)
    override val primaryForeground: Color = Color(0xFFFFFFFF)
    override val primaryForeground30: Color = Color(0xFFFFFFFF)
    override val secondary: Color = Color(0xFF262626)
    override val secondaryForeground: Color = Color(0xFFE5E5E5)
    override val secondaryForeground30: Color = Color(0xFFE5E5E5)
    override val primaryIcon: Color = Color(0xFFE5E5E5)
    override val primaryIconForeground: Color = Color(0xFFFFFFFF)
    override val secondaryIcon: Color = Color(0xFFFFFFFF)
    override val secondaryIconForeground: Color = Color(0xFFE5E5E5)
    override val translucentButton20: Color = Color(0x33FFFFFF)
    override val translucentButton10: Color = Color(0x1AFFFFFF)
    override val translucentForeground: Color = Color(0xFFFFFFFF)
    override val mask: Color = Color(0x99111111)
    override val positive: Color = Color(0x0D009337)
    override val positiveForeground: Color = Color(0xFF008000)
    override val destructive: Color = Color(0xFFF84752)
    override val destructiveForeground: Color = Color(0xFFF84752)
    override val warning: Color = Color(0xFF1A1A1A)
    override val warningForeground: Color = Color(0xFFF46E38)
    override val danger: Color = Color(0xFFF84752)
    override val dangerBackground: Color = Color(0xFFF84752)
    override val dangerButtonText: Color = Color(0xFFFFFFFF)
    override val deviceLEDGreen: Color = Color(0xFF008000)
    override val deviceLEDRed: Color = Color(0xFFFF0000)
    override val deviceLEDBlue: Color = Color(0xFF0059FF)
    override val deviceLEDWhite: Color = Color(0xFFFFFFFF)
    override val nfcBlue: Color = Color(0xFF3D81FF)
    override val bitkeyGetStartedBackground: Color = Color(0xFF201E22)
    override val bitkeyGetStartedTint: Color = Color(0xFFF8F4E4)
    override val outOfDate: Color = Color(0xFFECA900)
    override val calloutDefaultTitle: Color = Color(0xFFE5E5E5)
    override val calloutDefaultSubtitle: Color = Color(0xFF999999)
    override val calloutDefaultBackground: Color = Color(0xFF1A1A1A)
    override val calloutDefaultTrailingIcon: Color = Color(0xFF595959)
    override val calloutDefaultTrailingIconBackground: Color = Color(0xFF1A1A1A)
    override val calloutInformationTitle: Color = Color(0xFFE5E5E5)
    override val calloutInformationSubtitle: Color = Color(0xFF999999)
    override val calloutInformationBackground: Color = Color(0xFF1A1A1A)
    override val calloutInformationLeadingIcon: Color = Color(0xFF0C9ADA)
    override val calloutInformationTrailingIcon: Color = Color(0xFF595959)
    override val calloutInformationTrailingIconBackground: Color = Color(0xFF1A1A1A)
    override val calloutSuccessTitle: Color = Color(0xFFE5E5E5)
    override val calloutSuccessSubtitle: Color = Color(0xFF999999)
    override val calloutSuccessBackground: Color = Color(0xFF1A1A1A)
    override val calloutSuccessTrailingIcon: Color = Color(0xFF595959)
    override val calloutSuccessTrailingIconBackground: Color = Color(0xFF1A1A1A)
    override val calloutWarningTitle: Color = Color(0xFFE5E5E5)
    override val calloutWarningSubtitle: Color = Color(0xFF999999)
    override val calloutWarningBackground: Color = Color(0xFF1A1A1A)
    override val calloutWarningTrailingIcon: Color = Color(0xFF595959)
    override val calloutWarningTrailingIconBackground: Color = Color(0xFF1A1A1A)
    override val calloutDangerTitle: Color = Color(0xFFF84752)
    override val calloutDangerSubtitle: Color = Color(0xFF808080)
    override val calloutDangerTrailingIcon: Color = Color(0xFF595959)
    override val calloutDangerBackground: Color = Color(0xFF1A1A1A)
    override val coachmarkBackground: Color = Color(0xFF262626)
    override val inheritanceSurface: Color = Color(0xFFCFC4CD)
    override val bitcoinPrimary: Color = Color(0xFFF7931A)
    override val yourBalancePrimary: Color = Color(0xFF0C9ADA)
    override val loadingBackground: Color = Color(0xFF232323)
    override val chartElement: Color = Color(0xFF232323)
    override val stepperIncompleteLabel: Color = Color(0xFF999999)
    override val stepperIncomplete: Color = Color(0xFF1A1A1A)
    override val bitkeyLoading: Color = Color(0xFF575662)
    override val surfaceMarigold: Color = Color(0xfffdbb81)
    override val surfaceCorian: Color = Color(0xff333442)
    override val grayscale20: Color = Color(0x33333442)
    override val inverseBackground: Color = Color(0xffFFFFFF)
    override val primaryIconBackground: Color = Color(0xFF1A1A1A)
    override val newCoachmarkLightBackground: Color = Color(0xFF008096)
    override val newCoachmarkLightText: Color = Color(0xFFE5E5E5)
  }

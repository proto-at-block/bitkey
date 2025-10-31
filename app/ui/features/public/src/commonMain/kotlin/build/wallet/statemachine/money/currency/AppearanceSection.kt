package build.wallet.statemachine.money.currency

import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.appearance_section_currency
import bitkey.ui.framework_public.generated.resources.appearance_section_display
import bitkey.ui.framework_public.generated.resources.appearance_section_privacy
import org.jetbrains.compose.resources.StringResource

enum class AppearanceSection(val label: StringResource) {
  DISPLAY(Res.string.appearance_section_display),
  CURRENCY(Res.string.appearance_section_currency),
  PRIVACY(Res.string.appearance_section_privacy),
}

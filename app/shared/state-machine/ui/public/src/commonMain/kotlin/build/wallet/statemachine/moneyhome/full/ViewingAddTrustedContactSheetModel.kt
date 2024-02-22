package build.wallet.statemachine.moneyhome.full

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel

fun ViewingAddTrustedContactSheetModel(
  onAddTrustedContact: () -> Unit,
  onClosed: () -> Unit,
) = SheetModel(
  body =
    FormBodyModel(
      id = SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_DETAIL_SHEET,
      onBack = onClosed,
      toolbar = null,
      header =
        FormHeaderModel(
          icon = Icon.LargeIconShieldPerson,
          headline = "Trusted Contacts",
          alignment = FormHeaderModel.Alignment.LEADING,
          subline =
            """
            Trusted Contacts can help you recover your wallet if you lose access. Instead of relying on a company, you depend on the people you know and trust.
            
            Trusted Contacts won’t have access to your wallet or funds. They’re only able to help you recover your wallet.
            """.trimIndent()
        ),
      primaryButton =
        ButtonModel(
          text = "Add Trusted Contact",
          size = ButtonModel.Size.Footer,
          onClick =
            Click.SheetClosingClick {
              onAddTrustedContact()
            }
        ),
      renderContext = RenderContext.Sheet
    ),
  dragIndicatorVisible = true,
  onClosed = onClosed
)

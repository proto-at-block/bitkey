package build.wallet.statemachine.recovery.socrec.view

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.socrec.RecoveryContact
import build.wallet.bitkey.socrec.UnendorsedTrustedContact
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel

/**
 * Builds a sheet model to show details about a Trusted Contact.
 *
 * @param contact The contact to show information and actions for.
 * @param onRemove Invoked when the user wants to remove the trusted contact.
 * @param onClosed Invoked when the user closes the sheet.
 */
fun ViewingTrustedContactSheetModel(
  contact: RecoveryContact,
  onRemove: () -> Unit,
  onClosed: () -> Unit,
) = ViewingRecoveryContactSheetModel(
  headline = contact.trustedContactAlias.alias,
  subline = null,
  removeButtonText = "Remove Trusted Contact",
  onRemove = onRemove,
  onClosed = onClosed
)

/**
 * Builds a sheet model to show details about a Recovery Contact that is in a tampered state.
 *
 * @param contact The contact to show information and actions for.
 * @param onRemove Invoked when the user wants to remove the trusted contact.
 * @param onClosed Invoked when the user closes the sheet.
 */
fun ViewingTamperedContactSheetModel(
  contact: UnendorsedTrustedContact,
  onRemove: () -> Unit,
  onClosed: () -> Unit,
) = ViewingRecoveryContactSheetModel(
  headline = "${contact.trustedContactAlias.alias} is no longer listed as a valid trusted contact",
  subline = "We are unable to validate your trusted contact. This often happens when a trusted contact has deleted the Bitkey app.",
  removeButtonText = "Remove Contact",
  onRemove = onRemove,
  onClosed = onClosed
)

/**
 * Builds a sheet model to show details about a Recovery Contact that failed endorsement.
 *
 * @param contact The contact to show information and actions for.
 * @param onRemove Invoked when the user wants to remove the contact.
 * @param onClosed Invoked when the user closes the sheet.
 */
fun ViewingFailedContactSheetModel(
  contact: UnendorsedTrustedContact,
  onRemove: () -> Unit,
  onClosed: () -> Unit,
) = ViewingRecoveryContactSheetModel(
  headline = "Couldn't complete trusted contact enrollment for ${contact.trustedContactAlias.alias}",
  subline = "There was a problem confirming the invite code sent to your trusted contact. To add this contact, you will need to reinvite them.",
  removeButtonText = "Remove Contact",
  onRemove = onRemove,
  onClosed = onClosed
)

/**
 * Generic sheet model for viewing a recovery contact.
 *
 * @param headline The headline to display in the sheet.
 * @param subline The subline to display, optional.
 * @param removeButtonText The text to display on the remove button.
 * @param onRemove Invoked when the user wants to remove the contact.
 * @param onClosed Invoked when the user closes the sheet.
 */
fun ViewingRecoveryContactSheetModel(
  headline: String,
  subline: String?,
  removeButtonText: String,
  onRemove: () -> Unit,
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
          headline = headline,
          subline = subline,
          alignment = FormHeaderModel.Alignment.CENTER
        ),
      primaryButton = null,
      secondaryButton =
        ButtonModel(
          text = removeButtonText,
          treatment = ButtonModel.Treatment.SecondaryDestructive,
          size = ButtonModel.Size.Footer,
          onClick =
            object : Click.SheetClosingClick {
              override fun invoke() {
                onRemove()
              }
            }
        ),
      renderContext = RenderContext.Sheet
    ),
  dragIndicatorVisible = true,
  onClosed = onClosed
)

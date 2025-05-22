package build.wallet.statemachine.trustedcontact.view

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.bitkey.relationships.UnendorsedTrustedContact
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel

/**
 * Builds a sheet model to show details about a Recovery Contact.
 *
 * @param contact The contact to show information and actions for.
 * @param onRemove Invoked when the user wants to remove the Recovery Contact.
 * @param onClosed Invoked when the user closes the sheet.
 */
fun ViewingTrustedContactSheetModel(
  contact: TrustedContact,
  onRemove: () -> Unit,
  onClosed: () -> Unit,
) = ViewingRecoveryContactSheetModel(
  headline = contact.trustedContactAlias.alias,
  subline = "If you ever get a new phone, ${contact.trustedContactAlias.alias} can help verify you so you can regain access to your wallet.",
  removeButtonText = "Remove Recovery Contact",
  onRemove = onRemove,
  onClosed = onClosed
)

/**
 * Builds a sheet model to show details about a Recovery Contact that is in a tampered state.
 *
 * @param contact The contact to show information and actions for.
 * @param onRemove Invoked when the user wants to remove the Recovery Contact.
 * @param onClosed Invoked when the user closes the sheet.
 */
fun ViewingTamperedContactSheetModel(
  contact: UnendorsedTrustedContact,
  onRemove: () -> Unit,
  onClosed: () -> Unit,
) = ViewingRecoveryContactSheetModel(
  headline = "${contact.trustedContactAlias.alias} is no longer listed as a valid ${contact.label}",
  subline = "We are unable to validate your ${contact.label}. This often happens when a ${contact.label} has deleted the Bitkey app.",
  removeButtonText = when {
    TrustedContactRole.Beneficiary == contact.roles.singleOrNull() -> "Remove beneficiary"
    else -> "Remove contact"
  },
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
  headline = "Couldn't complete ${contact.label} enrollment for ${contact.trustedContactAlias.alias}",
  subline = "There was a problem confirming the invite code sent to your ${contact.label}. To add this contact, you will need to reinvite them.",
  removeButtonText = when {
    TrustedContactRole.Beneficiary == contact.roles.singleOrNull() -> "Remove beneficiary"
    else -> "Remove contact"
  },
  onRemove = onRemove,
  onClosed = onClosed
)

/**
 * Inline label used in copy to refer to the contact.
 */
private val UnendorsedTrustedContact.label: String get() = when {
  TrustedContactRole.Beneficiary == roles.singleOrNull() -> "beneficiary"
  else -> "Recovery Contact"
}

/**
 * Generic sheet model for viewing a Recovery Contact.
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
  body = ViewingRecoveryContactSheetBodyModel(
    headline = headline,
    subline = subline,
    removeButtonText = removeButtonText,
    onRemove = onRemove,
    onClosed = onClosed
  ),
  onClosed = onClosed
)

private data class ViewingRecoveryContactSheetBodyModel(
  val headline: String,
  val subline: String?,
  val removeButtonText: String,
  val onRemove: () -> Unit,
  val onClosed: () -> Unit,
) : FormBodyModel(
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
        onClick = SheetClosingClick(onRemove)
      ),
    renderContext = RenderContext.Sheet
  )

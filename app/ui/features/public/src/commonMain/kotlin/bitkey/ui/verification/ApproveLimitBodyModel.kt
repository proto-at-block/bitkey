package bitkey.ui.verification

import build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

/**
 * Body model for the "Approve your new limit" loading screen.
 * This screen is shown when the user needs to approve a transaction verification limit change via email.
 * It displays a loading spinner with instructions and action buttons.
 */
fun ApproveLimitBodyModel(
  onResendEmail: () -> Unit,
  onCancel: () -> Unit,
) = LoadingBodyModel(
  id = TxVerificationEventTrackerScreenId.APPROVE_LIMIT,
  onBack = onCancel,
  title = "Approve your new limit",
  description = "We emailed you a one-time link to approve your new limit.\n\nIf you don't see it, check your spam folder or resend the email.",
  primaryButton = ButtonModel(
    text = "Resend email",
    size = ButtonModel.Size.Footer,
    leadingIcon = Icon.SmallIconBitkey,
    treatment = ButtonModel.Treatment.BitkeyInteraction,
    onClick = StandardClick(onResendEmail)
  ),
  secondaryButton = ButtonModel(
    text = "Cancel",
    size = ButtonModel.Size.Footer,
    treatment = ButtonModel.Treatment.SecondaryDestructive,
    onClick = StandardClick(onCancel)
  )
)

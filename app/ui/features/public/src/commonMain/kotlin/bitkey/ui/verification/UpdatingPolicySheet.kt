package bitkey.ui.verification

import build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext

/**
 * Loading spinner shown in the bottom sheet while changes are being applied to
 * the user's Transaction Verification Policy.
 */
fun UpdatingPolicySheet(onBack: () -> Unit) =
  SheetModel(
    size = SheetSize.MIN40,
    onClosed = onBack,
    body = UpdatingPolicySheetBody(
      onBack = onBack
    )
  )

private data class UpdatingPolicySheetBody(
  override val onBack: () -> Unit,
) : FormBodyModel(
    id = TxVerificationEventTrackerScreenId.UPDATING_POLICY,
    onBack = {},
    toolbar = null,
    header = null,
    mainContentList = immutableListOf(FormMainContentModel.Loader),
    primaryButton = null,
    renderContext = RenderContext.Sheet
  )

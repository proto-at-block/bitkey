package bitkey.ui.verification

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import bitkey.verification.VerificationThreshold
import bitkey.verification.VerificationThreshold.Companion.Always
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.form.FormMainContentVerticalAlignment
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.statemachine.core.form.FormScreenTitleModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.switch.SwitchCard
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.switch.SwitchCardModel
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Body model used for managing the user's Transaction Verification Policy.
 */
internal data class TxVerificationPolicyStateModel(
  val formatter: MoneyDisplayFormatter,
  val checked: Boolean,
  val threshold: VerificationThreshold? = null,
  val enabled: Boolean = true,
  val updatePolicy: (Boolean) -> Unit,
  override val onBack: () -> Unit,
) : BodyModel() {
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = EventTrackerScreenInfo(
    eventTrackerScreenId = TxVerificationEventTrackerScreenId.MANAGE_POLICY
  )
  val switchCardModel = SwitchCardModel(
    title = "Transaction verification",
    subline = "Add extra protection by confirming transaction details before you send.",
    switchModel = SwitchModel(
      checked = checked,
      enabled = enabled,
      onCheckedChange = updatePolicy,
      testTag = "tx-verification-policy-toggle"
    ),
    actionRows = when (threshold) {
      is VerificationThreshold.Disabled, null -> emptyImmutableList()
      Always -> immutableListOf(
        SwitchCardModel.ActionRow(
          title = "Verify",
          sideText = "Always",
          onClick = { updatePolicy(true) }
        )
      )
      is VerificationThreshold.Enabled -> immutableListOf(
        SwitchCardModel.ActionRow(
          title = "Verify above",
          sideText = threshold.amount.let {
            when (it) {
              is BitcoinMoney -> formatter.format(it)
              is FiatMoney -> formatter.formatCompact(it)
            }
          },
          onClick = { updatePolicy(true) }
        )
      )
    }
  )

  @Composable
  override fun render(modifier: Modifier) {
    FormScreen(
      modifier = modifier,
      onBack = onBack,
      toolbarModel = ToolbarModel(
        leadingAccessory = ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory(onClick = onBack)
      ),
      toolbarContent = {
        Toolbar(
          model = ToolbarModel(
            leadingAccessory = ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory(onClick = onBack),
            middleAccessory = ToolbarMiddleAccessoryModel(
              title = "Transaction verification"
            )
          )
        )
      },
      screenTitle = FormScreenTitleModel(title = "Transaction verification"),
      layout = FormScreenLayoutModel.LargeTitle(
        scrollable = false,
        mainContentVerticalAlignment = FormMainContentVerticalAlignment.BOTTOM
      ),
      mainContent = {
        SwitchCard(model = switchCardModel)
      }
    )
  }
}

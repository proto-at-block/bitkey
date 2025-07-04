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
  private val formatter: MoneyDisplayFormatter,
  private val checked: Boolean,
  private val threshold: VerificationThreshold? = null,
  private val enabled: Boolean = true,
  private val updatePolicy: (Boolean) -> Unit,
  override val onBack: () -> Unit,
) : BodyModel() {
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = EventTrackerScreenInfo(
    eventTrackerScreenId = TxVerificationEventTrackerScreenId.MANAGE_POLICY
  )
  val switchCardModel = SwitchCardModel(
    title = "Address verification",
    subline = "When on, we'll ask you to verify the address of the recipient using a secure Bitkey server.",
    switchModel = SwitchModel(
      checked = checked,
      enabled = enabled,
      onCheckedChange = updatePolicy
    ),
    actionRows = when (threshold) {
      null, is VerificationThreshold.Disabled -> emptyImmutableList()
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
      modifier = Modifier.Companion,
      onBack = onBack,
      toolbarContent = {
        Toolbar(
          model = ToolbarModel(
            leadingAccessory = ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory(onClick = onBack),
            middleAccessory = ToolbarMiddleAccessoryModel(
              title = "Address verification"
            )
          )
        )
      },
      mainContent = {
        SwitchCard(model = switchCardModel)
      }
    )
  }
}

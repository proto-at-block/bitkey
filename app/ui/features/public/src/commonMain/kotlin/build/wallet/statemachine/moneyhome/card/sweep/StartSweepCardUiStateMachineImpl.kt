package build.wallet.statemachine.moneyhome.card.sweep

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.sweep.SweepService
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.callout.CalloutModel.Treatment

@BitkeyInject(ActivityScope::class)
class StartSweepCardUiStateMachineImpl(
  private val sweepService: SweepService,
) : StartSweepCardUiStateMachine {
  @Composable
  override fun model(props: StartSweepCardUiProps): CardModel? {
    val sweepRequiredState by sweepService.sweepRequired.collectAsState()

    return when (sweepRequiredState) {
      false -> null
      true -> CardModel(
        title = null,
        content = null,
        style = CardModel.CardStyle.Callout(
          CalloutModel(
            title = "Funds in inactive wallet",
            subtitle = LabelModel.StringModel("Transfer funds now"),
            treatment = Treatment.Warning,
            leadingIcon = Icon.SmallIconInformationFilled,
            trailingIcon = Icon.SmallIconArrowRight,
            onClick = StandardClick(props.onStartSweepClicked)
          )
        )
      )
    }
  }
}

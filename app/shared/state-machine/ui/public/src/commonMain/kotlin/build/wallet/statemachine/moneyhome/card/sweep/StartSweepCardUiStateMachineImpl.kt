package build.wallet.statemachine.moneyhome.card.sweep

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import build.wallet.recovery.sweep.SweepPromptRequirementCheck
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Treatment.Warning
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.minutes

class StartSweepCardUiStateMachineImpl(
  private val sweepPromptRequirementCheck: SweepPromptRequirementCheck,
) : StartSweepCardUiStateMachine {
  @Composable
  override fun model(props: StartSweepCardUiProps): CardModel? {
    val sweepRequiredState = sweepPromptRequirementCheck.sweepRequired.collectAsState()

    LaunchedEffect("update-sweep-status") {
      while (currentCoroutineContext().isActive) {
        sweepPromptRequirementCheck.checkForSweeps(props.keybox)
        delay(5.minutes)
      }
    }

    return when (sweepRequiredState.value) {
      false -> null
      true -> CardModel(
        title = LabelModel.StringWithStyledSubstringModel.from("Funds in inactive wallet", emptyMap()),
        subtitle = "Transfer funds now",
        leadingImage = CardModel.CardImage.StaticImage(
          icon = Icon.SmallIconInformationFilled,
          iconTint = CardModel.CardImage.StaticImage.IconTint.Warning
        ),
        content = null,
        style = CardModel.CardStyle.Gradient(backgroundColor = CardModel.CardStyle.Gradient.BackgroundColor.Warning),
        onClick = props.onStartSweepClicked,
        trailingButton = ButtonModel(
          text = "->",
          size = ButtonModel.Size.Compact,
          treatment = Warning,
          onClick = StandardClick(props.onStartSweepClicked)
        )
      )
    }
  }
}

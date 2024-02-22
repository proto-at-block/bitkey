package build.wallet.statemachine.education

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.statemachine.core.ScreenModel

class EducationUiStateMachineImpl : EducationUiStateMachine {
  @Composable
  override fun model(props: EducationUiProps): ScreenModel {
    var currentIndex by remember { mutableStateOf(0) }
    val currentEducationItem by remember {
      derivedStateOf { props.items[currentIndex] }
    }

    return EducationBodyModel(
      progressPercentage = (currentIndex + 1) / props.items.size.toFloat(),
      onDismiss = props.onExit,
      title = currentEducationItem.title,
      subtitle = currentEducationItem.subtitle,
      primaryButton = currentEducationItem.primaryButton,
      secondaryButton = currentEducationItem.secondaryButton,
      onClick = {
        if (currentIndex == props.items.lastIndex) {
          props.onContinue()
        } else {
          currentIndex++
        }
      },
      onBack = {
        if (currentIndex > 0) {
          currentIndex--
        } else {
          props.onExit()
        }
      }
    ).asModalScreen()
  }
}

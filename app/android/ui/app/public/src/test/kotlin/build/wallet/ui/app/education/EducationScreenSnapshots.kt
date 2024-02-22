package build.wallet.ui.app.education

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.compose.collections.immutableListOf
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.education.EducationBodyModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.delay

class EducationScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  // TODO(W-5468) Enable this test once paparazzi supports gif verification
  xtest("Explainer Screen Interation") {
    paparazzi.gif(length = 6000) { // 6 seconds
      var currentIndex by remember { mutableStateOf(0) }
      LaunchedEffect("Advance Explainer Screen") {
        delay(2000)
        currentIndex++
        delay(2000)
        currentIndex++
      }

      val items =
        immutableListOf(
          EducationItem(
            title = "If you can’t access wallet, your Trusted Contacts er.",
            subtitle = "Tap anywhere to continue",
            primaryButton =
              ButtonModel(
                text = "Primary",
                onClick = Click.StandardClick { },
                size = ButtonModel.Size.Footer
              ),
            secondaryButton =
              ButtonModel(
                text = "Secondary",
                onClick = Click.StandardClick { },
                treatment = ButtonModel.Treatment.Secondary,
                size = ButtonModel.Size.Footer
              ),
            onClick = null
          ),
          EducationItem(
            title = "If you can access wallet, your Trusted Contacts can help you recover.",
            subtitle = "Tap anywhere to continue",
            primaryButton =
              ButtonModel(
                text = "Primary",
                onClick = Click.StandardClick { },
                size = ButtonModel.Size.Footer
              ),
            secondaryButton =
              ButtonModel(
                text = "Secondary",
                onClick = Click.StandardClick { },
                treatment = ButtonModel.Treatment.Secondary,
                size = ButtonModel.Size.Footer
              ),
            onClick = null
          ),
          EducationItem(
            title = "If you can’t access wallet, your Trusted Contacts can help you recover.",
            subtitle = "Don't Tap anywhere to continue",
            primaryButton =
              ButtonModel(
                text = "Primary",
                onClick = Click.StandardClick { },
                size = ButtonModel.Size.Footer
              ),
            onClick = null
          )
        )

      EducationScreen(
        model =
          EducationBodyModel(
            progressPercentage = (currentIndex + 1f) / 3f,
            title = items[currentIndex].title,
            subtitle = items[currentIndex].subtitle,
            primaryButton = items[currentIndex].primaryButton,
            secondaryButton = items[currentIndex].secondaryButton,
            onClick = {},
            onDismiss = {},
            onBack = {}
          )
      )
    }
  }
})

private data class EducationItem(
  val title: String,
  val subtitle: String? = null,
  val primaryButton: ButtonModel? = null,
  val secondaryButton: ButtonModel? = null,
  val onClick: (() -> Unit)? = null,
)

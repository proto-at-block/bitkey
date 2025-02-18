package build.wallet.statemachine.education

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.Progress
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.app.education.EducationScreen
import build.wallet.ui.model.button.ButtonModel

/**
 * A [BodyModel] that represents the Education screen. The screen operates like a carousel which
 * should show items in succession
 *
 * @property progressPercentage - the percentage of the carousel that has been completed.
 * @property title - the title of the active education content
 * @property subtitle - the subtitle of the active education content
 * @property primaryButton - optional primary button to display, described via a [ButtonModel]
 * @property secondaryButton - optional secondary button to display, described via a [ButtonModel]
 * @property onClick - click handler for the active education content, should typically advance the
 * content to the next item in the carousel
 * @property onDismiss - the callback to invoke when the user dismisses the explainer
 * @property onBack - the callback to invoke when the user clicks the back button
 */
data class EducationBodyModel(
  val progressPercentage: Progress,
  val title: String,
  val subtitle: String? = null,
  val primaryButton: ButtonModel? = null,
  val secondaryButton: ButtonModel? = null,
  val onClick: () -> Unit,
  val onDismiss: () -> Unit,
  override val onBack: () -> Unit,
) : BodyModel() {
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null

  @Composable
  override fun render(modifier: Modifier) {
    EducationScreen(modifier, model = this)
  }
}

data class EducationItem(
  val title: String,
  val subtitle: String? = null,
  val primaryButton: ButtonModel? = null,
  val secondaryButton: ButtonModel? = null,
)

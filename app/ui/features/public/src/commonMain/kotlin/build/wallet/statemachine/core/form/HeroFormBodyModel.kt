package build.wallet.statemachine.core.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.automations.AutomaticUiTests
import build.wallet.statemachine.automations.AutomationUnavailable
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.app.core.form.HeroFormScreen
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel

/**
 * A body model for a screen with a large hero image at the top, a headline + subline
 * header, optional callout, and primary/secondary footer buttons.
 *
 * This is intentionally a slimmer alternative to [FormBodyModel] — it exposes only the
 * parameters required to render the hero-style screens (e.g. inheritance setup,
 * inheritance explainer, promo code upsell).
 *
 * @property heroContent Which hero image to display behind the toolbar.
 * @property leadingAccessory Leading toolbar accessory (e.g. close/back).
 * @property headline Headline text shown below the hero.
 * @property subline Supporting copy shown beneath the headline.
 * @property callout Optional callout rendered between the header and the footer buttons.
 * @property primaryButton Primary footer button.
 * @property secondaryButton Optional secondary footer button shown above the primary.
 * @property scrollContent When true, the footer scrolls together with the page content
 *  rather than being pinned to the bottom.
 */
open class HeroFormBodyModel(
  open val id: EventTrackerScreenId,
  override val onBack: () -> Unit,
  open val heroContent: HeroContent,
  open val leadingAccessory: ToolbarAccessoryModel,
  open val headline: String,
  open val subline: String,
  open val callout: CalloutModel? = null,
  open val primaryButton: ButtonModel,
  open val secondaryButton: ButtonModel? = null,
  open val scrollContent: Boolean = false,
  open val eventTrackerContext: EventTrackerContext? = null,
  open val eventTrackerShouldTrack: Boolean = true,
) : BodyModel(), AutomaticUiTests {
  enum class HeroContent {
    InheritanceSetup,
    InheritanceExplainer,
    PromoCodeHeader,
  }

  override val eventTrackerScreenInfo: EventTrackerScreenInfo =
    EventTrackerScreenInfo(
      eventTrackerScreenId = id,
      eventTrackerContext = eventTrackerContext,
      eventTrackerShouldTrack = eventTrackerShouldTrack
    )

  override val key: String get() = "${this::class.qualifiedName}-${id.name}."

  @Composable
  override fun render(modifier: Modifier) {
    HeroFormScreen(model = this, modifier = modifier)
  }

  override fun automateNextPrimaryScreen() {
    if (primaryButton.isEnabled) {
      primaryButton.onClick.invoke()
    } else {
      throw AutomationUnavailable(
        reason = "Primary button is disabled in hero layout: [${this::class.simpleName}]"
      )
    }
  }
}

package build.wallet.statemachine.core.form

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.EventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.platform.random.uuid
import build.wallet.platform.web.BrowserNavigator
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.form.RenderContext.Screen
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.label.CallToActionModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList

/**
 * A generic model capable of showing many different "form" like screens
 *
 * @property id: A unique identifier for this screen that will also be used to track screen
 * analytic events.
 * @property onSwipeToDismiss: Handler for iOS swipe to dismiss gesture. If nonnull, screen will
 * be swipe-to-dimiss-able. Otherwise, swipe to dismiss will be prevented.
 * @property toolbar: Toolbar component to show at the top of the screen. If `null`, empty toolbar
 * will be used that will act as a spacer.
 * @property header: Content shown at the top of the screen, under the toolbar.
 * @property mainContentList: A list of customizable main content for the screen that will be
 * arranged in a vertical column.
 * @property primaryButton: The primary button in the footer area of the screen.
 * @property secondaryButton: Optional secondary button shown below the primary button.
 * @property tertiaryButton: Optional tertiary button shown below the secondary button.
 * @property ctaWarning If specified, show a warning text above button stack.
 * @property keepScreenOn: Prevent screen dimming from inactivity.
 * @property renderContext [RenderContext]: how the model will be displayed to the user, defaults to
 * [Screen]
 * @property eventTrackerShouldTrack: whether the screen event should be tracked for analytics
 * @property errorData If the screen is an error screen, this will contain appropriate error data
 * to be logged.
 */
data class FormBodyModel(
  val id: EventTrackerScreenId?,
  override val onBack: (() -> Unit)?,
  val onSwipeToDismiss: (() -> Unit)? = null,
  val toolbar: ToolbarModel?,
  val header: FormHeaderModel?,
  val mainContentList: ImmutableList<FormMainContentModel> = emptyImmutableList(),
  val primaryButton: ButtonModel?,
  val secondaryButton: ButtonModel? = null,
  val tertiaryButton: ButtonModel? = null,
  val ctaWarning: CallToActionModel? = null,
  val keepScreenOn: Boolean = false,
  val renderContext: RenderContext = Screen,
  val onLoaded: ((BrowserNavigator) -> Unit) = {},
  val eventTrackerScreenIdContext: EventTrackerScreenIdContext? = null,
  val eventTrackerShouldTrack: Boolean = true,
  val errorData: ErrorData? = null,
) : BodyModel() {
  override val eventTrackerScreenInfo: EventTrackerScreenInfo?
    get() =
      id?.let {
        EventTrackerScreenInfo(
          eventTrackerScreenId = it,
          eventTrackerScreenIdContext = eventTrackerScreenIdContext,
          eventTrackerShouldTrack = eventTrackerShouldTrack
        )
      }

  private val unique = id?.name ?: uuid().random()
  override val key: String = "${this::class.qualifiedName}-$unique."
}

/**
 * Serves as a signal to the render layer of how the model is intended to be presented to the user
 *
 * Example: Error models render differently in Screens and bottom sheets
 *
 * @constructor Create empty Render context
 */
enum class RenderContext {
  /** Render the formscreen model as screen on the viewport, also the default */
  Screen,

  /** Render the formscreen model as appropriate for a bottom sheet */
  Sheet,
}

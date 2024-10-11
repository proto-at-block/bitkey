package build.wallet.statemachine.core.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.platform.random.uuid
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ComposableRenderedModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.form.RenderContext.Screen
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.label.CallToActionModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList
import kotlin.native.HiddenFromObjC

/**
 * A generic model capable of showing many different "form" like screens.
 *
 * When creating a new [FormBodyModel], you should implement it as a new type extending
 * this class:
 *
 * ```kotlin
 * data class EmergencyAccessKitImportWalletBodyModel(
 *   override val onBack: () -> Unit,
 *   val onScanQRCode: () -> Unit,
 *   val onEnterManually: () -> Unit,
 * ) : FormBodyModel(
 *      id = SELECT_IMPORT_METHOD,
 *      onBack = onBack,
 *      // ...
 * )
 * ```
 *
 * [formBodyModel] builder function is deprecated and should not be used for new
 * implementations, it exists purely for migration purposes.
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
 * @property enableComposeRendering When enabled, render this screen with Compose UI on iOS.
 */
abstract class FormBodyModel(
  open val id: EventTrackerScreenId?,
  override val onBack: (() -> Unit)?,
  open val onSwipeToDismiss: (() -> Unit)? = null,
  open val toolbar: ToolbarModel?,
  open val header: FormHeaderModel?,
  open val mainContentList: ImmutableList<FormMainContentModel> = emptyImmutableList(),
  open val primaryButton: ButtonModel?,
  open val secondaryButton: ButtonModel? = null,
  open val tertiaryButton: ButtonModel? = null,
  open val ctaWarning: CallToActionModel? = null,
  open val keepScreenOn: Boolean = false,
  open val renderContext: RenderContext = Screen,
  open val onLoaded: (() -> Unit) = {},
  open val eventTrackerContext: EventTrackerContext? = null,
  open val eventTrackerShouldTrack: Boolean = true,
  open val errorData: ErrorData? = null,
  open val enableComposeRendering: Boolean = false,
) : BodyModel(), ComposableRenderedModel {
  override val eventTrackerScreenInfo: EventTrackerScreenInfo?
    get() =
      id?.let {
        EventTrackerScreenInfo(
          eventTrackerScreenId = it,
          eventTrackerContext = eventTrackerContext,
          eventTrackerShouldTrack = eventTrackerShouldTrack
        )
      }

  private val unique: String
    get() = id?.name ?: uuid().substringBefore('-')
  override val key: String
    get() = "${this::class.qualifiedName}-$unique."

  @HiddenFromObjC
  @Composable
  override fun render(modifier: Modifier) {
    FormScreen(model = this)
  }
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

/**
 * Builder function for creating a new [FormBodyModel] instance.
 *
 * This function is deprecated and should not be used for new implementations. It exists
 * purely for migration purposes.
 * Instead, create a new type extending [FormBodyModel].
 *
 * TODO(W-9780):  migrate all usages of this function to new types extending [FormBodyModel], and
 *                remove this function.
 */
@Deprecated(message = "Implement a new type extending FormBodyModel instead")
fun formBodyModel(
  id: EventTrackerScreenId?,
  onBack: (() -> Unit)?,
  onSwipeToDismiss: (() -> Unit)? = null,
  toolbar: ToolbarModel?,
  header: FormHeaderModel?,
  mainContentList: ImmutableList<FormMainContentModel> = emptyImmutableList(),
  primaryButton: ButtonModel?,
  secondaryButton: ButtonModel? = null,
  tertiaryButton: ButtonModel? = null,
  ctaWarning: CallToActionModel? = null,
  keepScreenOn: Boolean = false,
  renderContext: RenderContext = Screen,
  onLoaded: (() -> Unit) = {},
  eventTrackerContext: EventTrackerContext? = null,
  eventTrackerShouldTrack: Boolean = true,
  errorData: ErrorData? = null,
): FormBodyModel =
  FormBodyModelImpl(
    id = id,
    onBack = onBack,
    onSwipeToDismiss = onSwipeToDismiss,
    toolbar = toolbar,
    header = header,
    mainContentList = mainContentList,
    primaryButton = primaryButton,
    secondaryButton = secondaryButton,
    tertiaryButton = tertiaryButton,
    ctaWarning = ctaWarning,
    keepScreenOn = keepScreenOn,
    renderContext = renderContext,
    onLoaded = onLoaded,
    eventTrackerContext = eventTrackerContext,
    eventTrackerShouldTrack = eventTrackerShouldTrack,
    errorData = errorData
  )

/**
 * A concrete internal implementation of [FormBodyModel] that is used by [formBodyModel] builder
 * function.
 *
 * Implemented as a private class instead of anonymous class so that we are able to read
 * class's qualifier name for the key by tests at runtime.
 */
private data class FormBodyModelImpl(
  override val id: EventTrackerScreenId?,
  override val onBack: (() -> Unit)?,
  override val onSwipeToDismiss: (() -> Unit)? = null,
  override val toolbar: ToolbarModel?,
  override val header: FormHeaderModel?,
  override val mainContentList: ImmutableList<FormMainContentModel> = emptyImmutableList(),
  override val primaryButton: ButtonModel?,
  override val secondaryButton: ButtonModel? = null,
  override val tertiaryButton: ButtonModel? = null,
  override val ctaWarning: CallToActionModel? = null,
  override val keepScreenOn: Boolean = false,
  override val renderContext: RenderContext = Screen,
  override val onLoaded: (() -> Unit) = {},
  override val eventTrackerContext: EventTrackerContext? = null,
  override val eventTrackerShouldTrack: Boolean = true,
  override val errorData: ErrorData? = null,
) : FormBodyModel(
    id = id,
    onBack = onBack,
    onSwipeToDismiss = onSwipeToDismiss,
    toolbar = toolbar,
    header = header,
    mainContentList = mainContentList,
    primaryButton = primaryButton,
    secondaryButton = secondaryButton,
    tertiaryButton = tertiaryButton,
    ctaWarning = ctaWarning,
    keepScreenOn = keepScreenOn,
    renderContext = renderContext,
    onLoaded = onLoaded,
    eventTrackerContext = eventTrackerContext,
    eventTrackerShouldTrack = eventTrackerShouldTrack,
    errorData = errorData
  )

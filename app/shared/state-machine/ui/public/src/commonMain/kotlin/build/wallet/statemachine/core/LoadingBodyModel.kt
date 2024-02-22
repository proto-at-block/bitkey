package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.EventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.platform.random.UuidImpl
import build.wallet.statemachine.core.LoadingBodyModel.Style.Explicit

/**
 * @property id: A unique identifier for this screen that will also be used to track screen
 * analytic events.
 * @property message to display while showing loading screen.
 * @property onBack callback for back navigation. If `null`, effectively disables navigation back.
 */
data class LoadingBodyModel(
  val message: String? = null,
  override val onBack: (() -> Unit)? = null,
  val style: Style = Explicit,
  val id: EventTrackerScreenId?,
  val eventTrackerScreenIdContext: EventTrackerScreenIdContext? = null,
  val eventTrackerShouldTrack: Boolean = true,
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

  sealed interface Style {
    /**
     * Horizontally left-aligned, vertically top-aligned
     * Paired with [SuccessBodyModel.Explicit]
     */
    data object Explicit : Style

    /**
     * Horizontally center-aligned, vertically center-aligned
     * Paired with [SuccessBodyModel.Implicit]
     */
    data object Implicit : Style
  }

  private val unique = id?.name ?: UuidImpl().random()
  override val key: String = "${this::class.qualifiedName}-$unique."
}

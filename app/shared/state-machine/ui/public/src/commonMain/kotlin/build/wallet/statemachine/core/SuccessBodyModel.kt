package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.platform.random.UuidImpl

/**
 * @property id: A unique identifier for this screen that will also be used to track screen
 * analytic events.
 */
data class SuccessBodyModel(
  val title: String,
  val message: String? = null,
  val style: Style,
  val id: EventTrackerScreenId?,
) : BodyModel() {
  override val eventTrackerScreenInfo: EventTrackerScreenInfo?
    get() = id?.let { EventTrackerScreenInfo(it) }

  sealed interface Style {
    /**
     * Horizontally left-aligned, vertically top-aligned
     * Needs explicit customer action to continue
     */
    data class Explicit(
      val primaryButton: ButtonDataModel,
    ) : Style {
      constructor(
        onPrimaryButtonClick: () -> Unit,
      ) : this(
        primaryButton =
          ButtonDataModel(
            text = "Done",
            onClick = onPrimaryButtonClick
          )
      )
    }

    /**
     * Horizontally center-aligned, vertically center-aligned
     * No customer action required, automatically continues
     */
    data object Implicit : Style
  }

  private val unique = id?.name ?: UuidImpl().random()
  override val key: String = "${this::class.qualifiedName}-$unique."
}

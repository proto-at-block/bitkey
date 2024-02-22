package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.ScreenPresentationStyle.ModalFullScreen
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.ui.model.Model
import build.wallet.ui.model.alert.AlertModel

abstract class BodyModel : Model() {
  /**
   * A unique ID and optional context used to track screens across the app.
   *
   * Set on [BodyModel] instead of [ScreenModel] because state machines may only emit a
   * [BodyModel] that their parent state machine will wrap into a singular [ScreenModel]
   * for consistent presentation style.
   *
   * Set to null to avoid the screen from being tracked.
   *
   * Once set, do not edit without informing data analysts.
   */
  abstract val eventTrackerScreenInfo: EventTrackerScreenInfo?

  /**
   * Back handler for Android, if any.
   */
  open val onBack: (() -> Unit)? = null

  /**
   * Convenience method to wrap this body model into a Root screen model.
   */
  fun asRootScreen(alertModel: AlertModel? = null) =
    ScreenModel(
      body = this,
      presentationStyle = Root,
      alertModel = alertModel
    )

  /**
   * Convenience method to wrap this body model into a Modal screen model.
   */
  fun asModalScreen(alertModel: AlertModel? = null) =
    ScreenModel(
      body = this,
      presentationStyle = Modal,
      alertModel = alertModel
    )

  /**
   * Convenience method to wrap this body model into a Modal full screen model.
   */
  fun asModalFullScreen() =
    ScreenModel(
      body = this,
      presentationStyle = ModalFullScreen
    )

  fun asScreen(
    presentationStyle: ScreenPresentationStyle,
    alertModel: AlertModel? = null,
  ) = ScreenModel(
    body = this,
    presentationStyle = presentationStyle,
    alertModel = alertModel
  )
}

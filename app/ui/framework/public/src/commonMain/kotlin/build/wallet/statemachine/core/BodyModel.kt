package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.ScreenPresentationStyle.*
import build.wallet.ui.model.ComposeModel
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.toast.ToastModel
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference

abstract class BodyModel : ComposeModel {
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
  fun asRootScreen(
    alertModel: ButtonAlertModel? = null,
    bottomSheetModel: SheetModel? = null,
    toastModel: ToastModel? = null,
  ) = ScreenModel(
    body = this,
    presentationStyle = Root,
    alertModel = alertModel,
    bottomSheetModel = bottomSheetModel,
    toastModel = toastModel
  )

  /**
   * Convenience method to wrap this body model into a Sheet Modal screen model.
   */
  fun asSheetModalScreen(onClosed: () -> Unit) =
    SheetModel(
      body = this,
      onClosed = onClosed
    )

  /**
   * Convenience method to wrap this body model into a Modal screen model.
   */
  fun asModalScreen(
    alertModel: AlertModel? = null,
    bottomSheetModel: SheetModel? = null,
    toastModel: ToastModel? = null,
  ) = ScreenModel(
    body = this,
    presentationStyle = Modal,
    alertModel = alertModel,
    bottomSheetModel = bottomSheetModel,
    toastModel = toastModel
  )

  /**
   * Convenience method to wrap this body model into a Modal full screen model.
   */
  fun asModalFullScreen(bottomSheetModel: SheetModel? = null) =
    ScreenModel(
      body = this,
      presentationStyle = ModalFullScreen,
      bottomSheetModel = bottomSheetModel
    )

  fun asScreen(
    presentationStyle: ScreenPresentationStyle,
    alertModel: ButtonAlertModel? = null,
  ) = ScreenModel(
    body = this,
    presentationStyle = presentationStyle,
    alertModel = alertModel
  )

  fun asRootFullScreen(
    theme: Theme? = null,
    alertModel: ButtonAlertModel? = null,
  ) = ScreenModel(
    body = this,
    presentationStyle = RootFullScreen,
    alertModel = alertModel,
    themePreference = theme?.let { ThemePreference.Manual(it) }
  )
}

package build.wallet.statemachine.core

import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.ui.model.Model
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.model.status.StatusBannerModel
import build.wallet.ui.model.toast.ToastModel
import build.wallet.ui.theme.ThemePreference

/**
 * Model for rendering full screen UI.
 * Top-level screen model that renders some content.
 *
 * @property body main content of the screen.
 * @property colorMode determines preferred color mode by the screen. For example on NFC screen we
 * want to always use Dark theme.
 * @property presentationStyle defines how the screen should be presented.
 * @property themePreference defines the theme preference for the screen.
 * @property alertModel an alert to show over the screen, if any.
 * @property statusBannerModel a status banner to show attached to the top edge and extending into
 * the status bar, if any. Currently only expected in Root presentation style, errors otherwise.
 * @property toastModel a temporary toast shown at the bottom of the screen, if any.
 * @property bottomSheetModel a half sheet to show over the screen, if any.
 * @property platformNfcScreen enable for NFC screens that can be handled by platform UI.
 */
data class ScreenModel(
  val body: BodyModel,
  val onTwoFingerDoubleTap: (() -> Unit)? = null,
  val presentationStyle: ScreenPresentationStyle = Root,
  val themePreference: ThemePreference? = null,
  val alertModel: AlertModel? = null,
  val statusBannerModel: StatusBannerModel? = null,
  val toastModel: ToastModel? = null,
  val bottomSheetModel: SheetModel? = null,
  val systemUIModel: SystemUIModel? = null,
  val platformNfcScreen: Boolean = false,
) : Model {
  init {
    if (statusBannerModel != null) {
      // We only expect use the status banner in Root style screens currently.
      // Update this if that changes.
      require(presentationStyle == Root || presentationStyle == ScreenPresentationStyle.RootFullScreen) {
        "Status banner is only expected in Root presentation styles, got $presentationStyle"
      }
    }
  }

  override val key: String
    get() = body.eventTrackerScreenInfo?.screenId ?: body.key
}

/**
 * Determines how the body should be presented on the screen.
 */
enum class ScreenPresentationStyle {
  /**
   * Body is presented as a root screen.
   */
  Root,

  /**
   * Body is presented as a screen over the root body, using slide up and down animation.
   */
  Modal,

  /**
   * Body is presented as a full screen screen over the root body, using slide up and down animation.
   */
  ModalFullScreen,

  /**
   * Body is presented as a root screen that extends beyond the top system bar.
   */
  RootFullScreen,

  /**
   * Body is presented as a full screen screen, over the root body and other modals. Uses
   * Z-axis animation and extends beyond the top system bar.
   */
  FullScreen,
}

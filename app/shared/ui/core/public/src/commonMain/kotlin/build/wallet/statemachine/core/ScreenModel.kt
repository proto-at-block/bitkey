package build.wallet.statemachine.core

import build.wallet.statemachine.core.ScreenColorMode.SystemPreference
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.ui.model.Model
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.model.status.StatusBannerModel

/**
 * Model for rendering full screen UI.
 * Top-level screen model that renders some content.
 *
 * @property body main content of the screen.
 * @property tabBar bottom navigation tab bar.
 * @property colorMode determines preferred color mode by the screen. For example on NFC screen we
 * want to always use Dark theme.
 * @property presentationStyle defines how the screen should be presented.
 * @property alertModel an alert to show over the screen, if any.
 * @property statusBannerModel a status banner to show attached to the top edge and extending into
 * the status bar, if any. Currently only expected in Root presentation style, errors otherwise.
 * @property bottomSheetModel a half sheet to show over the screen, if any.
 * @property onBack back handler for Android, if any.
 */
data class ScreenModel(
  val body: BodyModel,
  val tabBar: TabBarModel? = null,
  val onTwoFingerDoubleTap: (() -> Unit)? = null,
  val presentationStyle: ScreenPresentationStyle = Root,
  val colorMode: ScreenColorMode = SystemPreference,
  val alertModel: AlertModel? = null,
  val statusBannerModel: StatusBannerModel? = null,
  val bottomSheetModel: SheetModel? = null,
  val systemUIModel: SystemUIModel? = null,
) : Model() {
  init {
    if (statusBannerModel != null) {
      // We only expect use the status banner in Root style screens currently.
      // Update this if that changes.
      require(presentationStyle == Root)
    }
  }

  override val key: String
    get() = body.eventTrackerScreenInfo?.screenId ?: body.key
}

/**
 * Describes the background color style of the body. Does not define the exact color, but rather
 * to define the visual style in which the body should be rendered. For example, NFC screen
 * is always dark, regardless of the theme.
 */
enum class ScreenColorMode {
  /**
   * Use the color mode based on phone's theme preference.
   */
  SystemPreference,

  /**
   * Overrides the color mode to dark.
   */
  Dark,
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

package build.wallet.statemachine.account.create.full.hardware

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.platform.random.UuidImpl
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.model.video.VideoStartingPosition

/**
 * A special model for pairing new hardware which shows animated videos of the Bitkey device
 * behind content. There should be no transitions between screens with this body model (i.e.
 * so 'push' animation) that the videos seamlessly transition from one to the other.
 */
data class PairNewHardwareBodyModel(
  override val onBack: () -> Unit,
  val header: FormHeaderModel,
  val primaryButton: ButtonModel,
  val backgroundVideo: BackgroundVideo,
  val isNavigatingBack: Boolean,
  /** Prevent screen dimming from inactivity. */
  val keepScreenOn: Boolean = false,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo?,
) : BodyModel() {
  data class BackgroundVideo(
    val content: VideoContent,
    val startingPosition: VideoStartingPosition,
  ) {
    enum class VideoContent {
      /** Shows a bitkey device with a white activation light */
      BitkeyActivate,

      /** Shows a bitkey device against the top of a phone  */
      BitkeyPair,

      /** Shows a bitkey device with the fingerprint sensor highlighted  */
      BitkeyFingerprint,
    }
  }

  private val unique = eventTrackerScreenInfo?.eventTrackerScreenId?.name ?: UuidImpl().random()
  override val key: String = "${this::class.qualifiedName}-$unique."

  fun toolbarModel(onRefreshClick: () -> Unit) =
    ToolbarModel(
      leadingAccessory =
        toolbarAccessory(icon = Icon.SmallIconArrowLeft, onClick = onBack),
      trailingAccessory =
        toolbarAccessory(icon = Icon.SmallIconRefresh, onClick = onRefreshClick)
    )

  private fun toolbarAccessory(
    icon: Icon,
    onClick: () -> Unit,
  ) = ToolbarAccessoryModel.IconAccessory(
    model =
      IconButtonModel(
        iconModel =
          IconModel(
            icon = icon,
            iconSize = IconSize.Accessory,
            iconBackgroundType =
              IconBackgroundType.Circle(
                circleSize = IconSize.Regular,
                color = IconBackgroundType.Circle.CircleColor.TranslucentWhite
              ),
            iconTint = IconTint.OnTranslucent
          ),
        onClick = StandardClick(onClick)
      )
  )
}

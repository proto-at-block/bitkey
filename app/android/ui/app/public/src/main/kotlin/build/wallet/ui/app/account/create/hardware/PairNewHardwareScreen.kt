package build.wallet.ui.app.account.create.hardware

import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.android.ui.core.R
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo.VideoContent.BitkeyActivate
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo.VideoContent.BitkeyFingerprint
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo.VideoContent.BitkeyPair
import build.wallet.statemachine.core.ScreenColorMode
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.components.video.Video
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.system.KeepScreenOn

@Composable
fun PairNewHardwareScreen(model: PairNewHardwareBodyModel) {
  if (model.keepScreenOn) {
    KeepScreenOn()
  }

  var videoView: VideoView? by remember { mutableStateOf(null) }

  PairNewHardwareScreen(
    onBack = model.onBack,
    toolbarModel = model.toolbarModel(
      onRefreshClick = {
        // Replay the video
        videoView?.seekTo(0)
        videoView?.start()
      }
    ),
    headerModel = model.header,
    buttonModel = model.primaryButton,
    backgroundContent = {
      BoxWithConstraints {
        Video(
          modifier =
            Modifier
              .wrapContentSize(Alignment.TopCenter, unbounded = true)
              .size(maxWidth + 200.dp),
          resource =
            when (model.backgroundVideo.content) {
              BitkeyActivate -> R.raw.activate
              BitkeyFingerprint -> R.raw.fingerprint
              BitkeyPair -> R.raw.pair
            },
          isLooping = false,
          startingPosition = model.backgroundVideo.startingPosition,
          videoViewCallback = { view ->
            videoView = view
          }
        )
      }
    }
  )
}

@Composable
fun PairNewHardwareScreen(
  onBack: (() -> Unit)?,
  toolbarModel: ToolbarModel?,
  headerModel: FormHeaderModel,
  buttonModel: ButtonModel,
  backgroundContent: @Composable () -> Unit,
) {
  onBack?.let {
    BackHandler(onBack = onBack)
  }
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(Color.Black)
  ) {
    Box {
      // Background
      backgroundContent()

      // Content
      Column(
        modifier =
          Modifier
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Toolbar
        toolbarModel?.let {
          Toolbar(model = it)
        }

        Spacer(Modifier.weight(1F))

        // Header and button
        Column(
          modifier =
            Modifier
              .height(IntrinsicSize.Min)
        ) {
          Spacer(Modifier.height(56.dp))
          Header(model = headerModel, colorMode = ScreenColorMode.Dark)
          Spacer(Modifier.height(56.dp))
          Button(buttonModel)
          Spacer(Modifier.height(28.dp))
        }
      }
    }
  }
}

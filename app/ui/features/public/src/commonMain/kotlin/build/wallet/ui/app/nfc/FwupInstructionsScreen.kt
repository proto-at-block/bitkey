package build.wallet.ui.app.nfc

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.TopCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bitkey.account.HardwareType
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.bitkey_update_dark
import bitkey.ui.framework_public.generated.resources.bitkey_update_dark_snapshot
import bitkey.ui.framework_public.generated.resources.bitkey_update_light
import bitkey.ui.framework_public.generated.resources.bitkey_update_light_snapshot
import bitkey.ui.framework_public.generated.resources.pair_snapshot
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.nfc.FwupInstructionsBodyModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.LabelTreatment.Unspecified
import build.wallet.ui.components.label.buildAnnotatedString
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.components.video.VideoPlayer
import build.wallet.ui.components.video.VideoScalingMode
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.LocalIsPreviewTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val UpdateFirmwareButtonBottomSpacing = 24.dp
private val UpdateFirmwareCompactHeightThreshold = 680.dp
private val UpdateFirmwareCompactHeaderSpacing = 24.dp
private val UpdateFirmwareCompactButtonTopSpacing = 16.dp
private val UpdateFirmwareCompactHeroCornerRadius = 24.dp
private val UpdateFirmwareCompactHeroMinHeight = 260.dp
private val UpdateFirmwareCompactHeroMaxHeight = 380.dp
private val UpdateFirmwareLegacyPairHeroOverscan = 200.dp
private const val UPDATE_FIRMWARE_COMPACT_HERO_IMAGE_SCALE = 1.04f
private val UpdateFirmwareCompactHeroImageOffset = 8.dp
private const val UPDATE_FIRMWARE_COMPACT_HERO_FADE_START = 0.82f
private const val FWUP_HERO_PLACEHOLDER_REVEAL_DELAY_MILLIS = 350
private const val FWUP_HERO_PLACEHOLDER_FADE_DURATION_MILLIS = 150

@Composable
fun FwupInstructionsScreen(
  modifier: Modifier = Modifier,
  model: FwupInstructionsBodyModel,
) {
  val isW3 = model.hardwareType == HardwareType.W3
  BackHandler(onBack = model.onBack)
  FwupSystemThemedContent(followIosSystemTheme = isW3) {
    val theme = LocalTheme.current
    // The legacy W1 hero is composed for the bottom-sheet copy layout, while W3
    // uses the full-screen presentation and media.
    val useLegacyScreen = !isW3
    Box(
      modifier = modifier
        .fillMaxSize()
        .background(WalletTheme.colors.background)
    ) {
      if (useLegacyScreen) {
        LegacyFwupInstructionsScreen(
          modifier = Modifier.fillMaxSize(),
          model = model,
          theme = theme
        )
      } else {
        FwupInstructionsScreen(
          modifier = Modifier.fillMaxSize(),
          model = model,
          theme = theme
        )
      }
    }
  }
}

@Composable
private fun LegacyFwupInstructionsScreen(
  modifier: Modifier = Modifier,
  model: FwupInstructionsBodyModel,
  theme: Theme,
) {
  Box(modifier = modifier) {
    FwupUpdateBackgroundMedia(
      modifier = Modifier.matchParentSize(),
      theme = theme,
      hardwareType = model.hardwareType,
      showVideoPlaceholder = false
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding(),
      horizontalAlignment = CenterHorizontally
    ) {
      Toolbar(
        modifier = Modifier.padding(horizontal = 20.dp),
        model = model.toolbarModel,
        showDesignSystemChrome = false
      )
      Spacer(Modifier.weight(1F))
      Column(
        modifier =
          Modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp))
            .background(WalletTheme.colors.background)
            .padding(horizontal = 20.dp)
      ) {
        Spacer(Modifier.height(16.dp))
        Header(
          model = model.headerModel,
          sublineLabelTreatment = Primary
        )
        Spacer(Modifier.height(24.dp))
        Button(
          model = model.buttonModel
        )
        Spacer(Modifier.height(28.dp))
      }
    }
  }
}

@Composable
private fun FwupInstructionsScreen(
  modifier: Modifier = Modifier,
  model: FwupInstructionsBodyModel,
  theme: Theme,
) {
  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(WalletTheme.colors.background)
  ) {
    if (maxHeight < UpdateFirmwareCompactHeightThreshold) {
      FwupInstructionsCompactScreen(
        modifier = Modifier.fillMaxSize(),
        model = model,
        theme = theme,
        maxHeight = maxHeight
      )
    } else {
      Box(modifier = Modifier.fillMaxSize()) {
        FwupUpdateBackgroundMedia(
          modifier = Modifier.matchParentSize(),
          theme = theme,
          hardwareType = model.hardwareType,
          showVideoPlaceholder = fwupHeroVideoPlaceholderEnabled
        )

        Column(
          modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
          horizontalAlignment = CenterHorizontally
        ) {
          Toolbar(
            model = updateFirmwareToolbarModel(
              onClose = model.onBack,
              onHelpClick = model.onHelpClick
            ),
            showDesignSystemChrome = false
          )

          Spacer(Modifier.height(24.dp))

          FwupInstructionsHeader(model = model)

          Spacer(Modifier.weight(1f))

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = UpdateFirmwareButtonBottomSpacing),
            verticalArrangement = Arrangement.Bottom
          ) {
            Button(model.buttonModel)
          }
        }
      }
    }
  }
}

@Composable
private fun FwupInstructionsCompactScreen(
  modifier: Modifier = Modifier,
  model: FwupInstructionsBodyModel,
  theme: Theme,
  maxHeight: Dp,
) {
  val scrollState = rememberScrollState()
  val heroHeight = (maxHeight * 0.5f).coerceIn(
    UpdateFirmwareCompactHeroMinHeight,
    UpdateFirmwareCompactHeroMaxHeight
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(WalletTheme.colors.background)
      .systemBarsPadding()
      .padding(horizontal = 20.dp),
    horizontalAlignment = CenterHorizontally
  ) {
    Toolbar(
      model = updateFirmwareToolbarModel(
        onClose = model.onBack,
        onHelpClick = model.onHelpClick
      ),
      showDesignSystemChrome = false
    )

    Spacer(Modifier.height(UpdateFirmwareCompactHeaderSpacing))

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .verticalScroll(scrollState),
      horizontalAlignment = CenterHorizontally
    ) {
      FwupInstructionsHeader(model = model)

      Spacer(Modifier.height(UpdateFirmwareCompactHeaderSpacing))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(heroHeight)
          .clip(RoundedCornerShape(UpdateFirmwareCompactHeroCornerRadius))
      ) {
        FwupCompactHeroBackground(
          modifier = Modifier.matchParentSize(),
          theme = theme,
          hardwareType = model.hardwareType
        )
      }
    }

    Spacer(Modifier.height(UpdateFirmwareCompactButtonTopSpacing))

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = UpdateFirmwareButtonBottomSpacing),
      verticalArrangement = Arrangement.Bottom
    ) {
      Button(model.buttonModel)
    }
  }
}

@Composable
private fun FwupCompactHeroBackground(
  modifier: Modifier = Modifier,
  theme: Theme,
  hardwareType: HardwareType,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(WalletTheme.colors.background)
  ) {
    FwupUpdateHeroPlatformImage(
      modifier = Modifier
        .fillMaxSize()
        .offset(y = UpdateFirmwareCompactHeroImageOffset)
        .graphicsLayer(
          scaleX = UPDATE_FIRMWARE_COMPACT_HERO_IMAGE_SCALE,
          scaleY = UPDATE_FIRMWARE_COMPACT_HERO_IMAGE_SCALE
        ),
      theme = theme,
      hardwareType = hardwareType,
      alpha = 1f,
      contentScale = ContentScale.Crop
    )

    Box(
      modifier = Modifier
        .matchParentSize()
        .background(
          Brush.verticalGradient(
            colorStops = arrayOf(
              0f to Color.Transparent,
              UPDATE_FIRMWARE_COMPACT_HERO_FADE_START to Color.Transparent,
              1f to WalletTheme.colors.background
            )
          )
        )
    )
  }
}

@Composable
private fun FwupInstructionsHeader(model: FwupInstructionsBodyModel) {
  val sublineModel = model.headerModel.sublineModel

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = CenterHorizontally
  ) {
    model.headerModel.headline?.let { headline ->
      Label(
        text = headline,
        type = LabelType.Body2MonoCaps,
        treatment = Unspecified,
        alignment = TextAlign.Center,
        color = WalletTheme.colors.foreground
      )
    }

    sublineModel?.buildAnnotatedString()?.let { subline ->
      Spacer(Modifier.height(8.dp))
      Label(
        text = subline,
        type = LabelType.Body3Regular,
        treatment = Unspecified,
        alignment = TextAlign.Center,
        color = WalletTheme.colors.foreground60
      )
    }
  }
}

@Composable
private fun FwupUpdateBackgroundMedia(
  modifier: Modifier = Modifier,
  theme: Theme,
  hardwareType: HardwareType,
  showVideoPlaceholder: Boolean,
) {
  val videoResourcePath = fwupUpdateHeroVideoResource(hardwareType, theme)
  var showPlaceholder by remember(theme, videoResourcePath, showVideoPlaceholder) {
    mutableStateOf(showVideoPlaceholder && videoResourcePath != null)
  }

  LaunchedEffect(theme, videoResourcePath, showVideoPlaceholder) {
    showPlaceholder = showVideoPlaceholder && videoResourcePath != null
    if (showVideoPlaceholder && videoResourcePath != null) {
      delay(FWUP_HERO_PLACEHOLDER_REVEAL_DELAY_MILLIS.toLong())
      showPlaceholder = false
    }
  }

  val placeholderAlpha by animateFloatAsState(
    targetValue = if (showPlaceholder) 1f else 0f,
    animationSpec = tween(durationMillis = FWUP_HERO_PLACEHOLDER_FADE_DURATION_MILLIS),
    label = "fwupHeroPlaceholderAlpha"
  )

  Box(modifier = modifier) {
    if (hardwareType == HardwareType.W1) {
      FwupLegacyPairMedia(videoResourcePath = videoResourcePath)
    } else if (videoResourcePath == null || !showVideoPlaceholder) {
      FwupUpdateBackgroundMediaWithoutPlaceholder(
        theme = theme,
        videoResourcePath = videoResourcePath
      )
    } else {
      FwupUpdateBackgroundMediaWithPlaceholder(
        theme = theme,
        videoResourcePath = videoResourcePath,
        placeholderAlpha = placeholderAlpha
      )
    }
  }
}

@Composable
private fun FwupLegacyPairMedia(videoResourcePath: String?) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val mediaSize = minOf(maxWidth + UpdateFirmwareLegacyPairHeroOverscan, maxHeight + UpdateFirmwareLegacyPairHeroOverscan)
    val mediaModifier = Modifier
      .wrapContentSize(TopCenter, unbounded = true)
      .size(mediaSize)

    if (videoResourcePath != null) {
      VideoPlayer(
        modifier = mediaModifier,
        resourcePath = videoResourcePath,
        isLooping = false,
        backgroundColor = Color.Black
      )
    } else {
      Image(
        painter = painterResource(Res.drawable.pair_snapshot),
        contentDescription = null,
        modifier = mediaModifier,
        contentScale = ContentScale.FillBounds
      )
    }
  }
}

@Composable
private fun FwupUpdateBackgroundMediaWithoutPlaceholder(
  theme: Theme,
  videoResourcePath: String?,
) {
  if (videoResourcePath != null) {
    VideoPlayer(
      modifier = Modifier.fillMaxSize(),
      resourcePath = videoResourcePath,
      isLooping = false,
      backgroundColor = WalletTheme.colors.background,
      scalingMode = VideoScalingMode.CROP
    )
  } else {
    FwupUpdateHeroPlatformImage(
      modifier = Modifier.fillMaxSize(),
      theme = theme,
      hardwareType = HardwareType.W3,
      alpha = 1f,
      contentScale = ContentScale.Crop
    )
  }
}

@Composable
private fun FwupUpdateBackgroundMediaWithPlaceholder(
  theme: Theme,
  videoResourcePath: String?,
  placeholderAlpha: Float,
) {
  if (videoResourcePath != null) {
    VideoPlayer(
      modifier = Modifier.fillMaxSize(),
      resourcePath = videoResourcePath,
      isLooping = false,
      backgroundColor = WalletTheme.colors.background,
      scalingMode = VideoScalingMode.CROP,
      allowSurfaceOnTopWorkaround = false
    )
  }

  FwupUpdateHeroPlatformImage(
    modifier = Modifier.fillMaxSize(),
    theme = theme,
    hardwareType = HardwareType.W3,
    alpha = placeholderAlpha,
    contentScale = ContentScale.Crop
  )
}

@Composable
internal fun updateFirmwareHeroImageResource(
  theme: Theme,
  hardwareType: HardwareType,
): DrawableResource =
  when (hardwareType) {
    HardwareType.W1 -> Res.drawable.pair_snapshot
    HardwareType.W3 ->
      if (LocalIsPreviewTheme.current) {
        if (theme == Theme.DARK) {
          Res.drawable.bitkey_update_dark_snapshot
        } else {
          Res.drawable.bitkey_update_light_snapshot
        }
      } else if (theme == Theme.DARK) {
        Res.drawable.bitkey_update_dark
      } else {
        Res.drawable.bitkey_update_light
      }
  }

private fun updateFirmwareToolbarModel(
  onClose: () -> Unit,
  onHelpClick: (() -> Unit)?,
): ToolbarModel {
  return ToolbarModel(
    leadingAccessory = ToolbarAccessoryModel.IconAccessory(
      model = IconButtonModel(
        iconModel = IconModel(
          icon = Icon.X,
          iconSize = IconSize.Accessory,
          iconBackgroundType = IconBackgroundType.Circle(
            circleSize = IconSize.Regular,
            color = IconBackgroundType.Circle.CircleColor.Secondary
          ),
          iconTint = IconTint.Foreground
        ),
        testTag = "fwup-instructions-close",
        onClick = StandardClick(onClose)
      )
    ),
    trailingAccessory = onHelpClick?.let {
      ToolbarAccessoryModel.IconAccessory(
        model = IconButtonModel(
          iconModel = IconModel(
            icon = Icon.Question,
            iconSize = IconSize.Accessory,
            iconBackgroundType = IconBackgroundType.Circle(
              circleSize = IconSize.Regular,
              color = IconBackgroundType.Circle.CircleColor.Secondary
            ),
            iconTint = IconTint.Foreground
          ),
          testTag = "fwup-instructions-help",
          onClick = StandardClick(it)
        )
      )
    }
  )
}

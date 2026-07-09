package build.wallet.ui.app.core.form

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitkey.ui.features_public.generated.resources.upgradew3updown
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.bitkey_tilt_dark
import bitkey.ui.framework_public.generated.resources.bitkey_tilt_light
import bitkey.ui.framework_public.generated.resources.upgrade_w3
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.LabelModel.StringWithStyledSubstringModel
import build.wallet.statemachine.core.form.FORM_WAITING_REVEAL_DURATION_MILLIS
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.*
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer.Statement
import build.wallet.statemachine.core.form.FormWaitingRevealEasing
import build.wallet.statemachine.core.form.RenderContext.Screen
import build.wallet.ui.app.moneyhome.card.MoneyHomeCard
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.button.OrderedButtonPair
import build.wallet.ui.components.callout.Callout
import build.wallet.ui.components.card.BitkeyDevice
import build.wallet.ui.components.explainer.Explainer
import build.wallet.ui.components.explainer.Statement
import build.wallet.ui.components.fee.FeeOption
import build.wallet.ui.components.forms.DatePickerField
import build.wallet.ui.components.forms.ItemPickerField
import build.wallet.ui.components.forms.TextField
import build.wallet.ui.components.forms.TextFieldOverflowCharacteristic.Multiline
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.buildAnnotatedString
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListGroup
import build.wallet.ui.components.list.SettingsListComponent
import build.wallet.ui.components.loading.FormLoader
import build.wallet.ui.components.loading.FormLoaderStyle
import build.wallet.ui.components.progress.StepperIndicator
import build.wallet.ui.components.tab.CircularTabRow
import build.wallet.ui.components.timer.Timer
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.components.video.VideoPlayer
import build.wallet.ui.components.video.VideoPlayerHandler
import build.wallet.ui.compose.getVideoResource
import build.wallet.ui.compose.thenIf
import build.wallet.ui.data.DataGroup
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.video.VideoStartingPosition
import build.wallet.ui.system.KeepScreenOn
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.statemachine.core.Icon
import build.wallet.ui.tokens.painter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds
import bitkey.ui.features_public.generated.resources.Res as FeaturesRes

@Composable
fun FormScreen(
  model: FormBodyModel,
  modifier: Modifier = Modifier,
) {
  val footerRevealDelayMillis = model.footerRevealDelayMillis
  val footerVisible = rememberFooterVisible(model.key, footerRevealDelayMillis)
  val headerModel = model.header

  if (model.keepScreenOn) {
    KeepScreenOn()
  }

  FormScreen(
    modifier = modifier.thenIf(model.renderContext == Screen) {
      Modifier.fillMaxSize()
    },
    onBack = model.onBack,
    renderContext = model.renderContext,
    background = WalletTheme.colors.background,
    toolbarModel = model.toolbar,
    screenTitle = model.formScreenTitle,
    layout = model.formScreenLayout,
    toolbarContent = {
      model.toolbar?.let {
        Toolbar(model = it, designSystemChromeBackgroundColor = WalletTheme.colors.background)
      }
    },
    headerToMainContentSpacing = resolveHeaderToMainContentSpacing(model, headerModel),
    headerContent = headerModel?.let { header ->
      {
        Header(
          model = header,
          headlineLabelType = header.headlineLabelType
        )
      }
    },
    mainContent = {
      FormBodyMainContent(model = model)
    },
    footerContent = when {
      model.primaryButton != null || model.secondaryButton != null ||
        model.preFooterContentList.isNotEmpty() -> {
        {
          PreFooterContent(
            preFooterMainContentList = model.preFooterContentList
          )
          AnimatedFooterContent(
            visible = footerVisible,
            animateVisibility = model.footerRevealDelayMillis > 0,
            reserveSpace = model.preFooterContentList.isEmpty()
          ) {
            FooterContent(
              primaryButton = model.primaryButton,
              secondaryButton = model.secondaryButton,
              tertiaryButton = model.tertiaryButton
            )
          }
        }
      }
      else -> null
    }
  )
}

internal fun resolveHeaderToMainContentSpacing(
  model: FormBodyModel,
  headerModel: FormHeaderModel?,
): Int =
  model.headerToMainContentSpacing ?: when {
    headerModel == null -> 16
    headerModel.sublineModel == null -> 24
    else -> 16
  }

@Composable
private fun rememberFooterVisible(
  modelKey: String,
  footerRevealDelayMillis: Int,
): Boolean {
  var footerVisible by remember(modelKey, footerRevealDelayMillis) {
    mutableStateOf(footerRevealDelayMillis == 0)
  }

  LaunchedEffect(modelKey, footerRevealDelayMillis) {
    if (footerRevealDelayMillis == 0) {
      footerVisible = true
    } else {
      footerVisible = false
      delay(footerRevealDelayMillis.toLong())
      footerVisible = true
    }
  }

  return footerVisible
}

@Composable
internal fun ColumnScope.FormBodyMainContent(model: FormBodyModel) {
  model.mainContentList.forEachIndexed { index, mainContent ->
    when (mainContent) {
      is Spacer ->
        Spacer(
          modifier = mainContent.height?.let { Modifier.height(it.dp) }
            ?: Modifier.weight(1F)
        )
      is Divider -> Divider()
      is Explainer -> Explainer(statements = mainContent.items)
      is DataList -> DataGroup(rows = mainContent)
      is FeeOptionList -> FeeOptionList(mainContent)
      is VerificationCodeInput -> VerificationCodeInput(mainContent)
      is TextInput -> TextInput(mainContent)
      is TextArea -> TextArea(mainContent)
      is AddressInput -> AddressTextField(mainContent)
      is DatePicker -> DatePicker(mainContent)
      is Timer -> Timer(model = mainContent)
      is AnnotatedText -> AnnotatedText(mainContent)
      is ListGroup -> ListGroup(model = mainContent.listGroupModel)
      is Loader -> FormLoader()
      is DotLoader ->
        FormLoader(
          style = FormLoaderStyle.DotLoading
        )
      is Picker -> Picker(model = mainContent)
      is StepperIndicator -> StepperIndicator(model = mainContent)
      is Callout -> Callout(model = mainContent.item)
      is CalloutCard -> MoneyHomeCard(model = mainContent.item)
      is HeaderBlock ->
        Header(
          model = mainContent.header,
          headlineLabelType = mainContent.header.headlineLabelType
        )
      is Showcase -> Showcase(
        model = mainContent
      )
      is CustomContent -> mainContent.item.render(modifier = Modifier.fillMaxWidth())
      is CircularTabRow -> CircularTabRow(model = mainContent.item)
      is Upsell -> mainContent.render(modifier = Modifier)
      is DeviceStatusCard -> BitkeyDevice(model = mainContent)
      is SettingsList -> SettingsListComponent(model = mainContent)
      is CollapsibleAddress -> CollapsibleAddressSection(model = mainContent)
    }
    if (index < model.mainContentList.lastIndex) {
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
internal fun FooterContent(
  primaryButton: ButtonModel?,
  secondaryButton: ButtonModel?,
  tertiaryButton: ButtonModel?,
) {
  OrderedButtonPair(
    primary = primaryButton,
    secondary = secondaryButton,
    spacing = 16.dp,
    renderButton = { it.toFooterButton() }
  )
  tertiaryButton?.let {
    if (primaryButton != null || secondaryButton != null) {
      Spacer(Modifier.height(16.dp))
    }
    it.toFooterButton()
  }
}

@Composable
private fun PreFooterContent(preFooterMainContentList: ImmutableList<FormMainContentModel>) {
  preFooterMainContentList.forEach { mainContent ->
    when (mainContent) {
      is FormMainContentModel.CollapsibleAddress -> CollapsibleAddressSection(
        model = mainContent
      )
      is FormMainContentModel.HeaderBlock -> {
        Header(
          model = mainContent.header,
          headlineLabelType = mainContent.header.headlineLabelType
        )
        Spacer(Modifier.height(24.dp))
      }
      else -> error(
        "Unsupported pre-footer content type: ${mainContent::class.simpleName}. " +
          "PreFooterContent only supports CollapsibleAddress and HeaderBlock."
      )
    }
  }
}

@Composable
private fun CollapsibleAddressSection(model: FormMainContentModel.CollapsibleAddress) {
  var expanded by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxWidth()) {
    Divider()

    // Header row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .clickable(
          indication = null,
          interactionSource = remember { MutableInteractionSource() },
          onClick = { expanded = !expanded }
        )
        .padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "chevron-rotation"
      )
      IconImage(
        model = IconModel(
          icon = Icon.CaretRight,
          iconSize = IconSize.Custom(14)
        ).copy(text = if (expanded) "Collapse address" else "Expand address"),
        modifier = Modifier.rotate(chevronRotation),
        color = WalletTheme.colors.foreground60
      )
      Spacer(modifier = Modifier.width(8.dp))
      Label(
        text = model.label,
        type = LabelType.Body4Mono,
        treatment = LabelTreatment.Secondary
      )
    }

    // Expandable address content
    AnimatedVisibility(
      visible = expanded,
      enter = expandVertically(),
      exit = shrinkVertically()
    ) {
      Label(
        modifier = Modifier.padding(bottom = 16.dp),
        model = LabelModel.chunkedAddress(model.address),
        type = LabelType.Body2Mono,
        alignment = TextAlign.Start,
        treatment = LabelTreatment.Primary
      )
    }
  }
}

@Composable
private fun AnimatedFooterContent(
  visible: Boolean,
  animateVisibility: Boolean,
  reserveSpace: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (!animateVisibility) {
    if (visible) {
      Column(content = content)
    }
    return
  }

  val animationFraction by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(
      durationMillis = FORM_WAITING_REVEAL_DURATION_MILLIS,
      easing = FormWaitingRevealEasing
    ),
    label = "footer-reveal-animation"
  )

  SubcomposeLayout { constraints ->
    val placeables = subcompose("footer-content") {
      Column(content = content)
    }.map { measurable ->
      measurable.measure(constraints)
    }

    val width = (placeables.maxOfOrNull { it.width } ?: 0)
      .coerceIn(constraints.minWidth, constraints.maxWidth)
    val measuredHeight = (placeables.maxOfOrNull { it.height } ?: 0)
      .coerceIn(constraints.minHeight, constraints.maxHeight)

    // When reserveSpace is true, reserve the footer's final measured size throughout the
    // reveal so centered content above it doesn't reflow when the buttons fade and slide in.
    // When reserveSpace is false, the height grows with the animation so content below
    // (e.g. a destination address) gets pushed down as buttons appear.
    val height = if (reserveSpace) {
      measuredHeight
    } else {
      (measuredHeight * animationFraction).toInt()
    }

    layout(width, height) {
      if (animationFraction > 0f) {
        placeables.forEach { placeable ->
          placeable.placeRelativeWithLayer(0, 0) {
            alpha = animationFraction
            translationY = if (reserveSpace) {
              (measuredHeight / 3f) * (1f - animationFraction)
            } else {
              0f
            }
          }
        }
      }
    }
  }
}

@Composable
fun Showcase(model: Showcase) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .thenIf(model.fillAvailableSpace) {
        Modifier.fillMaxSize()
      },
    horizontalAlignment = CenterHorizontally,
    verticalArrangement = Arrangement.Top
  ) {
    when (val content = model.content) {
      is Showcase.Content.IconContent -> {
        ShowcaseIconContent(content)
      }
      is Showcase.Content.ImageContent -> {
        ShowcaseImageContent(content)
      }
      is Showcase.Content.VideoContent -> {
        ShowcaseVideoContent(
          content = content
        )
      }
    }

    ShowcaseLabels(model)
  }
}

@Composable
private fun ShowcaseImageContent(content: Showcase.Content.ImageContent) {
  val painter = painterResource(
    when (content.image) {
      Showcase.Content.ImageContent.Image.BITKEY_TILT ->
        when (LocalTheme.current) {
          Theme.DARK -> Res.drawable.bitkey_tilt_dark
          Theme.LIGHT -> Res.drawable.bitkey_tilt_light
        }
      Showcase.Content.ImageContent.Image.UPGRADE_W3 ->
        Res.drawable.upgrade_w3
      Showcase.Content.ImageContent.Image.UPGRADE_W3_UP_DOWN ->
        FeaturesRes.drawable.upgradew3updown
    }
  )

  val aspectRatio = with(painter.intrinsicSize) {
    val hasFiniteDimensions = width.isFinite() && height.isFinite()
    val hasPositiveDimensions = width > 0f && height > 0f

    if (hasFiniteDimensions && hasPositiveDimensions) {
      width / height
    } else {
      1f
    }
  }

  BoxWithConstraints(
    modifier = Modifier.fillMaxWidth()
  ) {
    Image(
      modifier = Modifier
        .requiredWidth(maxWidth * content.scale)
        .aspectRatio(aspectRatio)
        .align(Alignment.Center),
      painter = painter,
      contentDescription = null,
      contentScale = ContentScale.Fit
    )
  }
}

@Composable
private fun ShowcaseIconContent(content: Showcase.Content.IconContent) {
  Image(
    modifier =
      if (content.widthDp != null && content.heightDp != null) {
        Modifier.size(width = content.widthDp.dp, height = content.heightDp.dp)
      } else {
        Modifier
          .aspectRatio(1f)
          .padding(horizontal = 24.dp)
      },
    painter = content.icon.painter(),
    contentDescription = null
  )
}

@Composable
private fun ShowcaseVideoContent(content: Showcase.Content.VideoContent) {
  var videoHandler: VideoPlayerHandler? by remember(content.video) { mutableStateOf(null) }
  val videoVisible = rememberShowcaseVideoVisibility(
    video = content.video,
    videoHandler = videoHandler
  )

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(1f)
      .clipToBounds()
  ) {
    VideoPlayer(
      modifier =
        Modifier
          .matchParentSize()
          .graphicsLayer {
            alpha = if (videoVisible) 1f else 0f
          },
      resourcePath = showcaseVideoResourcePath(content.video),
      backgroundColor = WalletTheme.colors.background,
      autoStart = false,
      isLooping = content.video.looping,
      startingPosition = VideoStartingPosition.START,
      scalingMode = content.video.scalingMode,
      allowSurfaceOnTopWorkaround = true,
      videoPlayerCallback = { handler -> videoHandler = handler }
    )
  }
}

@Composable
private fun rememberShowcaseVideoVisibility(
  video: Showcase.Content.VideoContent.Video,
  videoHandler: VideoPlayerHandler?,
): Boolean {
  var videoVisible by remember(video) { mutableStateOf(false) }
  var hasStartedPlayback by remember(video) { mutableStateOf(false) }

  LaunchedEffect(video, videoHandler) {
    val handler = videoHandler ?: return@LaunchedEffect
    if (hasStartedPlayback) {
      videoVisible = true
      handler.play()
      return@LaunchedEffect
    }
    videoVisible = false
    // Hold the first frame back slightly so playback starts after the screen transition settles.
    delay(200.milliseconds)
    videoVisible = true
    hasStartedPlayback = true
    handler.play()
  }

  return videoVisible
}

@Composable
private fun showcaseVideoResourcePath(video: Showcase.Content.VideoContent.Video): String =
  when (video) {
    Showcase.Content.VideoContent.Video.BITKEY_ROTATE -> {
      when (LocalTheme.current) {
        Theme.LIGHT -> Res.getVideoResource("bitkey_rotate")
        Theme.DARK -> Res.getVideoResource("bitkey_rotate_dark")
      }
    }
  }

@Composable
private fun ShowcaseLabels(model: Showcase) {
  if (model.title == null && model.body == null) return

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp),
    verticalArrangement = Arrangement.Top,
    horizontalAlignment = CenterHorizontally
  ) {
    model.title?.let {
      Label(
        model = StringModel(it),
        treatment = LabelTreatment.Primary,
        type = LabelType.Body1Medium,
        alignment = TextAlign.Center
      )
    }

    if (model.title != null && model.body != null) {
      Spacer(modifier = Modifier.height(6.dp))
    }

    model.body?.let {
      Label(
        model = it,
        treatment = LabelTreatment.Secondary,
        type = LabelType.Body2Regular,
        alignment = TextAlign.Center
      )
    }
  }
}

@Composable
private fun Explainer(statements: ImmutableList<Statement>) {
  Explainer(modifier = Modifier.padding(end = 12.dp)) {
    statements.forEach { item ->
      Statement(
        icon = item.leadingIcon,
        leadingIconSize = item.leadingIconSize,
        leadingContentTopPadding = item.leadingContentTopPaddingDp.dp,
        leadingContentSpacing = item.leadingContentSpacingDp.dp,
        leadingText = item.leadingText,
        leadingTextType = item.leadingTextType,
        leadingTextTreatment = item.leadingTextLabelTreatment,
        title = item.title,
        body =
          when (val body = item.body) {
            is StringModel -> AnnotatedString(body.string)
            is LabelModel.CalloutModel -> buildAnnotatedString {
              pushStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.W600))
              pushStyle(ParagraphStyle(lineHeight = 32.sp))
              append(body.string)
            }
            is StringWithStyledSubstringModel ->
              body.buildAnnotatedString()
            is LabelModel.ChunkedAddressModel ->
              body.buildAnnotatedString()
            is LabelModel.LinkSubstringModel -> body.buildAnnotatedString()
          },
        tint =
          when (item.treatment) {
            Statement.Treatment.PRIMARY -> WalletTheme.colors.foreground
            Statement.Treatment.WARNING -> WalletTheme.colors.warningForeground
          },
        titleType = item.titleLabelType,
        titleTreatment = item.titleLabelTreatment,
        bodyType = item.bodyType,
        bodyTreatment = item.bodyLabelTreatment
      )
    }
  }
}

@Composable
private fun FeeOptionList(model: FeeOptionList) {
  Column(
    modifier = Modifier.selectableGroup(),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    model.options.forEach { option ->
      FeeOption(
        leadingText = option.optionName,
        trailingPrimaryText = option.transactionTime,
        trailingSecondaryText = option.transactionFee,
        selected = option.selected,
        enabled = option.enabled,
        infoText = option.infoText,
        onClick = option.onClick
      )
    }
  }
}

@Composable
private fun TextInput(model: TextInput) {
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    model.title?.let {
      Label(
        text = it,
        type = LabelType.Title3,
        treatment = LabelTreatment.Primary
      )
    }

    TextField(
      modifier = Modifier.fillMaxWidth(),
      model = model.fieldModel
    )
  }
}

@Composable
private fun AnnotatedText(model: AnnotatedText) {
  Label(
    modifier = Modifier.fillMaxWidth(),
    text = model.text,
    type = model.type,
    alignment = model.alignment,
    treatment = model.treatment,
    onClick = model.onClick
  )
}

@Composable
private fun TextArea(model: TextArea) {
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    model.title?.let {
      Label(
        text = it,
        type = LabelType.Title3,
        treatment = LabelTreatment.Primary
      )
    }

    TextField(
      modifier = Modifier.fillMaxWidth(),
      model = model.fieldModel,
      textFieldOverflowCharacteristic = Multiline
    )
  }
}

@Composable
private fun AddressTextField(model: AddressInput) {
  TextField(
    modifier = Modifier.fillMaxWidth(),
    model = model.fieldModel,
    labelType = LabelType.Body2Mono,
    textFieldOverflowCharacteristic = Multiline,
    trailingButtonModel = model.trailingButtonModel
  )
}

@Composable
private fun DatePicker(model: DatePicker) {
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    model.title?.let {
      Label(
        text = it,
        type = LabelType.Title3,
        treatment = LabelTreatment.Primary
      )
    }

    DatePickerField(
      modifier = Modifier.fillMaxWidth(),
      model = model.fieldModel
    )
  }
}

@Composable
private fun Picker(model: Picker) {
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    model.title?.let {
      Label(
        text = it,
        type = LabelType.Title3,
        treatment = LabelTreatment.Primary
      )
    }

    ItemPickerField(
      modifier = Modifier.fillMaxWidth(),
      model = model.fieldModel
    )
  }
}

@Suppress("ComposableNaming")
@Composable
fun ButtonModel.toFooterButton() =
  Button(
    text = text,
    enabled = isEnabled,
    isLoading = isLoading,
    treatment = treatment,
    leadingIcon = leadingIcon,
    size = Footer,
    onClick = onClick
  )

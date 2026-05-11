package build.wallet.ui.app.core.form

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.bitkey_tilt_dark
import bitkey.ui.framework_public.generated.resources.bitkey_tilt_light
import bitkey.ui.framework_public.generated.resources.upgrade_w3
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.LabelModel.StringWithStyledSubstringModel
import build.wallet.statemachine.core.form.FORM_DS_V2_WAITING_REVEAL_DURATION_MILLIS
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormDsV2WaitingRevealEasing
import build.wallet.statemachine.core.form.FormDesignSystemV2Model

import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.*
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer.Statement
import build.wallet.statemachine.core.form.RenderContext.Screen
import build.wallet.statemachine.money.currency.AppearanceSection
import build.wallet.ui.app.moneyhome.card.MoneyHomeCard
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconImage
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
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.buildAnnotatedString
import build.wallet.ui.components.label.toWalletTheme
import build.wallet.ui.components.layout.CollapsedMoneyView
import build.wallet.ui.components.layout.CollapsibleLabelContainer
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
import build.wallet.ui.components.video.VideoScalingMode
import build.wallet.ui.components.webview.WebView
import build.wallet.ui.compose.getVideoResource
import build.wallet.ui.compose.thenIf
import build.wallet.ui.data.DataGroup
import build.wallet.ui.data.DataGroupDevice
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.label.CallToActionModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.model.video.VideoStartingPosition
import build.wallet.ui.system.KeepScreenOn
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.tokens.market.MarketIcons
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.painter
import build.wallet.compose.collections.emptyImmutableList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds


internal data class ResolvedFormScreenModel(
  val designSystemV2Eyebrow: String?,
  val designSystemV2Title: String?,
  val designSystemV2UseLayout: Boolean,
  val designSystemV2HeaderToMainContentSpacing: Int,
  val designSystemV2ContentSpacing: Int,
  val designSystemV2Scrollable: Boolean,
  val designSystemV2MainContentAlignment: FormScreenContentVerticalAlignment,
  val toolbarModel: ToolbarModel?,
  val headerModel: FormHeaderModel?,
  val mainContentList: ImmutableList<FormMainContentModel>,
  val primaryButton: ButtonModel?,
  val secondaryButton: ButtonModel?,
  val footerRevealDelayMillis: Int,
  val preFooterMainContentList: ImmutableList<FormMainContentModel>,
)

@Composable
fun FormScreen(
  model: FormBodyModel,
  modifier: Modifier = Modifier,
) {
  val resolvedModel = resolveFormScreenModel(model, LocalDesignSystemUpdatesEnabled.current)
  val footerVisible = rememberFooterVisible(model.key, resolvedModel.footerRevealDelayMillis)

  if (model.keepScreenOn) {
    KeepScreenOn()
  }

  LaunchedEffect("form-screen-loaded") {
    model.onLoaded?.invoke()
  }

  FormScreenContent(model = model, resolvedModel = resolvedModel, footerVisible = footerVisible, modifier = modifier)
}

@Composable
private fun FormScreenContent(
  model: FormBodyModel,
  resolvedModel: ResolvedFormScreenModel,
  footerVisible: Boolean,
  modifier: Modifier = Modifier,
) {
  FormScreen(
    modifier = modifier.thenIf(model.renderContext == Screen) {
      Modifier.fillMaxSize()
    },
    onBack = model.onBack,
    renderContext = model.renderContext,
    background = WalletTheme.colors.background,
    toolbarModel = resolvedModel.toolbarModel,
    designSystemV2Eyebrow = resolvedModel.designSystemV2Eyebrow,
    designSystemV2Title = resolvedModel.designSystemV2Title,
    designSystemV2UseLayout = resolvedModel.designSystemV2UseLayout,
    designSystemV2ContentSpacing = resolvedModel.designSystemV2ContentSpacing,
    designSystemV2Scrollable = resolvedModel.designSystemV2Scrollable,
    designSystemV2MainContentAlignment = resolvedModel.designSystemV2MainContentAlignment,
    toolbarContent = {
      resolvedModel.toolbarModel?.let {
        Toolbar(model = it, designSystemChromeBackgroundColor = WalletTheme.colors.background)
      }
    },
    headerToMainContentSpacing = resolvedModel.designSystemV2HeaderToMainContentSpacing,
    headerContent = resolvedModel.headerModel?.let { header ->
      {
        Header(
          model = header,
          headlineLabelType = header.headlineLabelType
        )
      }
    },
    mainContent = {
      FormBodyMainContent(
        model = model,
        resolvedModel = resolvedModel,
        footerVisible = footerVisible
      )
    },
    footerContent = when {
      model.disableFixedFooter -> null
      resolvedModel.primaryButton != null || resolvedModel.secondaryButton != null ||
        resolvedModel.preFooterMainContentList.isNotEmpty() -> {
        {
          PreFooterContent(
            preFooterMainContentList = resolvedModel.preFooterMainContentList
          )
          AnimatedFooterContent(
            visible = footerVisible,
            animateVisibility = resolvedModel.footerRevealDelayMillis > 0,
            reserveSpace = resolvedModel.preFooterMainContentList.isEmpty()
          ) {
            FooterContent(
              model = model,
              primaryButton = resolvedModel.primaryButton,
              secondaryButton = resolvedModel.secondaryButton
            )
          }
        }
      }
      else -> null
    }
  )
}

internal fun resolveFormScreenModel(
  model: FormBodyModel,
  designSystemUpdatesEnabled: Boolean,
): ResolvedFormScreenModel {
  val designSystemV2Model =
    if (designSystemUpdatesEnabled) {
      model.designSystemV2Model
    } else {
      null
    }
  val headerModel = resolveHeaderModel(model, designSystemV2Model)

  return ResolvedFormScreenModel(
    designSystemV2Eyebrow = designSystemV2Model?.eyebrow,
    designSystemV2Title = designSystemV2Model?.title,
    designSystemV2UseLayout = designSystemV2Model?.useDesignSystemV2ScreenLayout ?: false,
    designSystemV2HeaderToMainContentSpacing =
      resolveHeaderToMainContentSpacing(headerModel, designSystemV2Model),
    designSystemV2ContentSpacing = designSystemV2Model?.contentSpacing ?: 24,
    designSystemV2Scrollable = designSystemV2Model?.scrollable ?: true,
    designSystemV2MainContentAlignment =
      designSystemV2Model?.mainContentVerticalAlignment?.toFormScreenContentVerticalAlignment()
        ?: FormScreenContentVerticalAlignment.Top,
    toolbarModel = resolveToolbarModel(model, designSystemV2Model),
    headerModel = headerModel,
    mainContentList = designSystemV2Model?.mainContentList ?: model.mainContentList,
    primaryButton = resolvePrimaryButton(model, designSystemV2Model),
    secondaryButton = resolveSecondaryButton(model, designSystemV2Model),
    footerRevealDelayMillis = designSystemV2Model?.footerRevealDelayMillis ?: 0,
    preFooterMainContentList = designSystemV2Model?.preFooterMainContentList ?: emptyImmutableList()
  )
}

private fun resolveHeaderModel(
  model: FormBodyModel,
  designSystemV2Model: FormDesignSystemV2Model?,
): FormHeaderModel? =
  when {
    designSystemV2Model == null -> model.header
    designSystemV2Model.useLegacyHeaderFallback -> designSystemV2Model.header ?: model.header
    else -> designSystemV2Model.header
  }

private fun resolveToolbarModel(
  model: FormBodyModel,
  designSystemV2Model: FormDesignSystemV2Model?,
): ToolbarModel? =
  when {
    designSystemV2Model == null -> model.toolbar
    designSystemV2Model.useLegacyToolbarFallback -> designSystemV2Model.toolbar ?: model.toolbar
    else -> designSystemV2Model.toolbar
  }

private fun resolvePrimaryButton(
  model: FormBodyModel,
  designSystemV2Model: FormDesignSystemV2Model?,
): ButtonModel? =
  when {
    designSystemV2Model == null -> model.primaryButton
    designSystemV2Model.useLegacyPrimaryButtonFallback ->
      designSystemV2Model.primaryButton ?: model.primaryButton
    else -> designSystemV2Model.primaryButton
  }

private fun resolveSecondaryButton(
  model: FormBodyModel,
  designSystemV2Model: FormDesignSystemV2Model?,
): ButtonModel? =
  when {
    designSystemV2Model == null -> model.secondaryButton
    designSystemV2Model.useLegacySecondaryButtonFallback ->
      designSystemV2Model.secondaryButton ?: model.secondaryButton
    else -> designSystemV2Model.secondaryButton
  }

private fun resolveHeaderToMainContentSpacing(
  headerModel: FormHeaderModel?,
  designSystemV2Model: FormDesignSystemV2Model?,
): Int =
  designSystemV2Model?.headerToMainContentSpacing ?: when {
    headerModel == null -> 16
    headerModel.sublineModel == null -> 24
    else -> 16
  }
private fun FormDesignSystemV2Model.MainContentVerticalAlignment.toFormScreenContentVerticalAlignment():
  FormScreenContentVerticalAlignment =
  when (this) {
    FormDesignSystemV2Model.MainContentVerticalAlignment.TOP -> FormScreenContentVerticalAlignment.Top
    FormDesignSystemV2Model.MainContentVerticalAlignment.CENTER -> FormScreenContentVerticalAlignment.Center
    FormDesignSystemV2Model.MainContentVerticalAlignment.BOTTOM -> FormScreenContentVerticalAlignment.Bottom
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
  val resolvedModel = resolveFormScreenModel(model, LocalDesignSystemUpdatesEnabled.current)
  val footerVisible = rememberFooterVisible(model.key, resolvedModel.footerRevealDelayMillis)

  FormBodyMainContent(
    model = model,
    resolvedModel = resolvedModel,
    footerVisible = footerVisible
  )
}

@Composable
private fun ColumnScope.FormBodyMainContent(
  model: FormBodyModel,
  resolvedModel: ResolvedFormScreenModel,
  footerVisible: Boolean,
) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  resolvedModel.mainContentList.forEachIndexed { index, mainContent ->
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
      is WebView -> WebView(mainContent.url)
      is Button -> Button(model = mainContent.item)
      is AnnotatedText -> AnnotatedText(mainContent)
      is ListGroup -> ListGroup(model = mainContent.listGroupModel)
      is Loader -> FormLoader()
      is DotLoader ->
        FormLoader(
          style =
            if (isDesignSystemV2Enabled) {
              FormLoaderStyle.DotLoading
            } else {
              FormLoaderStyle.Legacy
            }
        )
      is MoneyHomeHero -> MoneyHomeHero(model = mainContent)
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
      is CircularTabRow -> CircularTabRow(model = mainContent.item)
      is Upsell -> mainContent.render(modifier = Modifier)
      is DeviceDataList -> DataGroupDevice(rows = mainContent.rows)
      is DeviceStatusCard -> BitkeyDevice(model = mainContent)
      is SettingsList -> SettingsListComponent(model = mainContent)
      is CollapsibleAddress -> CollapsibleAddressSection(model = mainContent)
    }
    if (index < resolvedModel.mainContentList.lastIndex) {
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
  if (
    model.disableFixedFooter &&
    (resolvedModel.primaryButton != null || resolvedModel.secondaryButton != null)
  ) {
    AnimatedFooterContent(
      visible = footerVisible,
      animateVisibility = resolvedModel.footerRevealDelayMillis > 0
    ) {
      FooterContent(
        model = model,
        primaryButton = resolvedModel.primaryButton,
        secondaryButton = resolvedModel.secondaryButton
      )
      // Adjust bottom padding to account for the lack of a footer container in the parent.
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
internal fun FooterContent(model: FormBodyModel) {
  val resolvedModel = resolveFormScreenModel(model, LocalDesignSystemUpdatesEnabled.current)

  FooterContent(
    model = model,
    primaryButton = resolvedModel.primaryButton,
    secondaryButton = resolvedModel.secondaryButton
  )
}

@Composable
private fun FooterContent(
  model: FormBodyModel,
  primaryButton: ButtonModel?,
  secondaryButton: ButtonModel?,
) {
  model.ctaWarning?.let {
    CallToActionLabel(model = it)
    Spacer(Modifier.height(12.dp))
  }
  OrderedButtonPair(
    primary = primaryButton,
    secondary = secondaryButton,
    spacing = 16.dp,
    renderButton = { it.toFooterButton() }
  )
  model.tertiaryButton?.let { tertiaryButton ->
    if (primaryButton != null || secondaryButton != null) {
      Spacer(Modifier.height(16.dp))
    }
    tertiaryButton.toFooterButton()
  }
}

@Composable
private fun PreFooterContent(
  preFooterMainContentList: ImmutableList<FormMainContentModel>,
) {
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
private fun CollapsibleAddressSection(
  model: FormMainContentModel.CollapsibleAddress,
) {
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
          icon = MarketIcons.ChevronRight,
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
      durationMillis = FORM_DS_V2_WAITING_REVEAL_DURATION_MILLIS,
      easing = FormDsV2WaitingRevealEasing
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
fun Showcase(
  model: Showcase,
) {
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
    }
  )

  Image(
    modifier = Modifier.fillMaxWidth(),
    painter = painter,
    contentDescription = null,
    contentScale = ContentScale.FillWidth
  )
}

@Composable
private fun ShowcaseIconContent(
  content: Showcase.Content.IconContent,
) {
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
private fun ShowcaseVideoContent(
  content: Showcase.Content.VideoContent,
) {
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
    Showcase.Content.VideoContent.Video.BITKEY_WIPE -> {
      when (LocalTheme.current) {
        Theme.LIGHT -> Res.getVideoResource("bitkey_wipe")
        Theme.DARK -> Res.getVideoResource("bitkey_wipe_dark")
      }
    }
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
    statements.map { item ->
      Statement(
        icon = item.leadingIcon,
        leadingIconSize = item.leadingIconSize,
        leadingContentTopPadding = item.leadingContentTopPaddingDp.dp,
        leadingContentSpacing = item.leadingContentSpacingDp.dp,
        leadingText = item.leadingText,
        leadingTextType = item.leadingTextType,
        leadingTextTreatment = item.leadingTextLabelTreatment,
        title = item.title,
        onClick = (item.body as? LabelModel.LinkSubstringModel)?.let { linkedLabelModel ->
          { clickPosition ->
            linkedLabelModel.linkedSubstrings.find { ls ->
              ls.range.contains(clickPosition)
            }?.onClick?.invoke()
          }
        },
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
internal fun MoneyHomeHero(
  model: MoneyHomeHero,
  selectedSection: AppearanceSection? = null,
  isDarkMode: Boolean = LocalTheme.current == Theme.DARK,
  isPriceGraphEnabled: Boolean = false,
) {
  val easeOutCubic = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1f)

  val image = when {
    isDarkMode && isPriceGraphEnabled -> Icon.MoneyHomeHeroDarkWithGraph.painter()
    isDarkMode && !isPriceGraphEnabled -> Icon.MoneyHomeHeroDarkNoGraph.painter()
    !isDarkMode && isPriceGraphEnabled -> Icon.MoneyHomeHeroLightWithGraph.painter()
    !isDarkMode && !isPriceGraphEnabled -> Icon.MoneyHomeHeroLightNoGraph.painter()
    else -> Icon.MoneyHomeHero.painter()
  }
  val scale by animateFloatAsState(
    targetValue = when (selectedSection) {
      AppearanceSection.DISPLAY -> .9f
      AppearanceSection.CURRENCY -> 1.2f
      AppearanceSection.PRIVACY -> 2.0f
      null -> 1.0f
    },
    animationSpec = tween(durationMillis = 300, easing = easeOutCubic),
    label = "scale"
  )

  val scaleBalance by animateFloatAsState(
    targetValue = when (selectedSection) {
      AppearanceSection.DISPLAY -> .4f
      AppearanceSection.CURRENCY -> .6f
      AppearanceSection.PRIVACY -> 1.1f
      null -> 1.0f
    },
    animationSpec = tween(durationMillis = 300, easing = easeOutCubic),
    label = "scaleBalance"
  )

  val balanceOffsetY by animateDpAsState(
    targetValue = when (selectedSection) {
      AppearanceSection.DISPLAY -> (-38).dp
      AppearanceSection.CURRENCY -> 4.dp
      AppearanceSection.PRIVACY -> 35.dp
      null -> 0.dp
    },
    animationSpec = tween(durationMillis = 300, easing = easeOutCubic),
    label = "balanceOffsetY"
  )

  val offsetY by animateDpAsState(
    targetValue = when (selectedSection) {
      AppearanceSection.DISPLAY -> 0.dp
      AppearanceSection.CURRENCY -> 60.dp
      AppearanceSection.PRIVACY -> 140.dp
      null -> 0.dp
    },
    animationSpec = tween(durationMillis = 300, easing = easeOutCubic),
    label = "offsetY"
  )

  Box {
    Image(
      painter = image,
      contentDescription = "money home hero",
      alignment = Alignment.TopCenter,
      modifier = Modifier
        .align(Alignment.Center)
        .clipToBounds()
        .background(
          color = WalletTheme.colors.subtleBackground,
          shape = RoundedCornerShape(12.dp)
        )
        .offset(y = offsetY)
        .fillMaxWidth()
        .height(200.dp)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
    )

    CollapsibleLabelContainer(
      modifier = Modifier
        .padding(vertical = 64.dp)
        .align(Alignment.TopCenter)
        .offset(y = balanceOffsetY)
        .graphicsLayer {
          scaleX = scaleBalance
          scaleY = scaleBalance
        },
      collapsed = model.isHidden,
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
      topContent = { Label(model.primaryAmount, type = LabelType.Body2Bold) },
      bottomContent = {
        Label(
          model.secondaryAmount,
          type = LabelType.Body4Medium,
          treatment = LabelTreatment.Secondary
        )
      },
      collapsedContent = { placeholder ->
        CollapsedMoneyView(
          height = 16.dp,
          shimmer = !placeholder
        )
      }
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

@Composable
private fun CallToActionLabel(model: CallToActionModel) {
  Label(
    modifier = Modifier.fillMaxWidth(),
    text = model.text,
    type = LabelType.Body4Regular,
    treatment = when (model.treatment) {
      CallToActionModel.Treatment.SECONDARY -> LabelTreatment.Secondary
      CallToActionModel.Treatment.WARNING -> LabelTreatment.Warning
    },
    alignment = TextAlign.Center
  )
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

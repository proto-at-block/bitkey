package build.wallet.ui.app.core.form

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.LabelModel.StringWithStyledSubstringModel
import build.wallet.statemachine.core.LabelModel.StringWithStyledSubstringModel.SubstringStyle.*
import build.wallet.statemachine.core.form.BackgroundTreatment
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.*
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer.Statement
import build.wallet.statemachine.core.form.RenderContext.Screen
import build.wallet.statemachine.money.currency.AppearanceSection
import build.wallet.ui.app.moneyhome.card.MoneyHomeCard
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.callout.Callout
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
import build.wallet.ui.components.loading.FormLoader
import build.wallet.ui.components.progress.StepperIndicator
import build.wallet.ui.components.tab.CircularTabRow
import build.wallet.ui.components.timer.Timer
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.components.video.VideoPlayer
import build.wallet.ui.components.webview.WebView
import build.wallet.ui.compose.getVideoResource
import build.wallet.ui.compose.thenIf
import build.wallet.ui.data.DataGroup
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.label.CallToActionModel
import build.wallet.ui.model.video.VideoStartingPosition
import build.wallet.ui.system.KeepScreenOn
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.painter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun FormScreen(
  model: FormBodyModel,
  modifier: Modifier = Modifier,
) {
  if (model.keepScreenOn) {
    KeepScreenOn()
  }

  LaunchedEffect("form-screen-loaded") {
    model.onLoaded?.invoke()
  }

  FormScreen(
    modifier = modifier.thenIf(model.renderContext == Screen) {
      Modifier.fillMaxSize()
    },
    onBack = model.onBack,
    renderContext = model.renderContext,
    background = if (model.backgroundTreatment == BackgroundTreatment.Inheritance) {
      WalletTheme.colors.inheritanceSurface
    } else {
      WalletTheme.colors.background
    },
    headerToMainContentSpacing = when (val header = model.header) {
      null -> 16
      else ->
        when (header.sublineModel) {
          null -> 24
          else -> 16
        }
    },
    toolbarContent = {
      model.toolbar?.let {
        Toolbar(it)
      }
    },
    headerContent = model.header?.let { header ->
      {
        Header(
          model = header,
          headlineLabelType = LabelType.Title1
        )
      }
    },
    mainContent = {
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
          is WebView -> WebView(mainContent.url)
          is Button -> Button(model = mainContent.item)
          is ListGroup -> ListGroup(model = mainContent.listGroupModel)
          is Loader -> FormLoader()
          is MoneyHomeHero -> MoneyHomeHero(model = mainContent)
          is Picker -> Picker(model = mainContent)
          is StepperIndicator -> StepperIndicator(model = mainContent)
          is Callout -> Callout(model = mainContent.item)
          is CalloutCard -> MoneyHomeCard(model = mainContent.item)
          is Showcase -> Showcase(model = mainContent)
          is CircularTabRow -> CircularTabRow(model = mainContent.item)
          is Upsell -> mainContent.render(modifier = Modifier)
        }
        if (index < model.mainContentList.lastIndex) {
          Spacer(modifier = Modifier.height(16.dp))
        }
      }
      if (
        model.disableFixedFooter &&
        (model.primaryButton != null || model.secondaryButton != null)
      ) {
        FooterContent(model)
        // Adjust bottom padding to account for the lack of a footer container in the parent.
        Spacer(modifier = Modifier.height(16.dp))
      }
    },
    footerContent = when {
      model.disableFixedFooter -> null
      model.primaryButton != null || model.secondaryButton != null -> {
        { FooterContent(model) }
      }
      else -> null
    }
  )
}

@Composable
private fun FooterContent(model: FormBodyModel) {
  model.ctaWarning?.let {
    CallToActionLabel(model = it)
    Spacer(Modifier.height(12.dp))
  }
  model.primaryButton?.toFooterButton()
  model.secondaryButton?.let { secondaryButton ->
    model.primaryButton?.let {
      Spacer(Modifier.height(16.dp))
    }
    secondaryButton.toFooterButton()
  }
  model.tertiaryButton?.let { tertiaryButton ->
    if (model.primaryButton != null || model.secondaryButton != null) {
      Spacer(Modifier.height(16.dp))
    }
    tertiaryButton.toFooterButton()
  }
}

@Composable
fun Showcase(model: Showcase) {
  val backgroundColor = when (model.treatment) {
    Showcase.Treatment.DEFAULT -> Color.Transparent
    Showcase.Treatment.INHERITANCE -> WalletTheme.colors.inheritanceSurface
  }

  // used to adjust spacing and padding to avoid scrolling on smaller devices
  val smallDeviceWidth = 374.dp

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(backgroundColor),
    horizontalAlignment = CenterHorizontally,
    verticalArrangement = Arrangement.Top
  ) {
    when (val content = model.content) {
      is Showcase.Content.IconContent -> {
        ShowcaseIconContent(content, model.treatment, smallDeviceWidth)
      }
      is Showcase.Content.VideoContent -> {
        ShowcaseVideoContent(content)
      }
    }

    if (model.treatment == Showcase.Treatment.INHERITANCE) {
      BoxWithConstraints {
        Spacer(
          modifier = Modifier.thenIf(maxWidth > smallDeviceWidth) {
            Modifier.height(26.dp)
          }
        )
      }
    }

    ShowcaseLabels(model)
  }
}

@Composable
private fun ShowcaseIconContent(
  content: Showcase.Content.IconContent,
  treatment: Showcase.Treatment,
  smallDeviceWidth: Dp,
) {
  BoxWithConstraints {
    Image(
      modifier = when (treatment) {
        Showcase.Treatment.DEFAULT ->
          Modifier
            .aspectRatio(1f)
            .padding(horizontal = 24.dp)
        Showcase.Treatment.INHERITANCE ->
          Modifier
            .thenIf(maxWidth <= smallDeviceWidth) {
              Modifier.padding(horizontal = 60.dp)
            }
      },
      painter = content.icon.painter(),
      contentDescription = null
    )
  }
}

@Composable
private fun ShowcaseVideoContent(content: Showcase.Content.VideoContent) {
  val scope = rememberStableCoroutineScope()
  VideoPlayer(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .aspectRatio(1f),
    resourcePath = when (content.video) {
      Showcase.Content.VideoContent.Video.BITKEY_WIPE -> {
        when (LocalTheme.current) {
          Theme.LIGHT -> Res.getVideoResource("bitkey_wipe")
          Theme.DARK -> Res.getVideoResource("bitkey_wipe_dark")
        }
      }
    },
    backgroundColor = WalletTheme.colors.background,
    autoStart = false,
    isLooping = content.video.looping,
    startingPosition = VideoStartingPosition.START,
    videoPlayerCallback = { handler ->
      scope.launch {
        // Short delay to avoid playing over screen transitions
        delay(200.milliseconds)
        handler.play()
      }
    }
  )
}

@Composable
private fun ShowcaseLabels(model: Showcase) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp),
    verticalArrangement = Arrangement.Top,
    horizontalAlignment = when (model.treatment) {
      Showcase.Treatment.DEFAULT -> CenterHorizontally
      Showcase.Treatment.INHERITANCE -> Alignment.Start
    }
  ) {
    Label(
      model = StringModel(model.title),
      treatment = when (model.treatment) {
        Showcase.Treatment.DEFAULT -> LabelTreatment.Primary
        Showcase.Treatment.INHERITANCE -> LabelTreatment.PrimaryDark
      },
      type = when (model.treatment) {
        Showcase.Treatment.DEFAULT -> LabelType.Body1Medium
        Showcase.Treatment.INHERITANCE -> LabelType.Header1
      },
      alignment = when (model.treatment) {
        Showcase.Treatment.DEFAULT -> TextAlign.Center
        Showcase.Treatment.INHERITANCE -> TextAlign.Start
      }
    )

    if (model.treatment == Showcase.Treatment.DEFAULT) {
      Spacer(modifier = Modifier.height(6.dp))
    }

    Label(
      model = model.body,
      treatment = LabelTreatment.Secondary,
      type = when (model.treatment) {
        Showcase.Treatment.DEFAULT -> LabelType.Body2Regular
        Showcase.Treatment.INHERITANCE -> LabelType.Body1Regular
      },
      alignment = when (model.treatment) {
        Showcase.Treatment.DEFAULT -> TextAlign.Center
        Showcase.Treatment.INHERITANCE -> TextAlign.Start
      }
    )
  }
}

@Composable
private fun Explainer(statements: ImmutableList<Statement>) {
  Explainer(modifier = Modifier.padding(end = 12.dp)) {
    statements.map { item ->
      Statement(
        icon = item.leadingIcon,
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
              buildAnnotatedString {
                append(body.string)
                body.styledSubstrings.forEach { styledSubstring ->
                  addStyle(
                    style =
                      when (val substringStyle = styledSubstring.style) {
                        is ColorStyle -> SpanStyle(color = substringStyle.color.toWalletTheme())
                        is BoldStyle -> SpanStyle(fontWeight = FontWeight.W600)
                        is FontFeatureStyle -> SpanStyle(fontFeatureSettings = substringStyle.fontFeatureSettings)
                      },
                    start = styledSubstring.range.first,
                    end = styledSubstring.range.last + 1
                  )
                }
              }
            is LabelModel.LinkSubstringModel -> body.buildAnnotatedString()
          },
        tint =
          when (item.treatment) {
            Statement.Treatment.PRIMARY -> WalletTheme.colors.foreground
            Statement.Treatment.WARNING -> WalletTheme.colors.warningForeground
          }
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

package build.wallet.ui.app.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.BackgroundTreatment
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.MONO
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.REGULAR
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.SMALL
import build.wallet.statemachine.core.form.RenderContext.Screen
import build.wallet.statemachine.transactions.TransactionDetailModel
import build.wallet.ui.app.core.form.FooterContent
import build.wallet.ui.app.core.form.FormBodyMainContent
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.header.CustomHeaderContent
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.components.label.LabelTreatment.Unspecified
import build.wallet.ui.components.label.buildAnnotatedString
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.system.BackHandler
import build.wallet.ui.system.KeepScreenOn
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
fun TransactionDetailScreen(
  modifier: Modifier = Modifier,
  model: TransactionDetailModel,
) {
  val title = model.formHeaderModel.headline
  val content: @Composable (Modifier) -> Unit =
    if (!LocalDesignSystemUpdatesEnabled.current || title == null) {
      { screenModifier ->
        FormScreen(model = model, modifier = screenModifier)
      }
    } else {
      { screenModifier ->
        TransactionDetailScreenV2(
          modifier = screenModifier,
          model = model,
          title = title
        )
      }
    }

  content(modifier)
}

@Composable
private fun TransactionDetailScreenV2(
  modifier: Modifier = Modifier,
  model: TransactionDetailModel,
  title: String,
) {
  if (model.keepScreenOn) {
    KeepScreenOn()
  }

  LaunchedEffect("transaction-detail-screen-loaded") {
    model.onLoaded?.invoke()
  }

  model.onBack?.let {
    BackHandler(onBack = it)
  }

  val isFullScreen = model.renderContext == Screen
  val background = when (model.backgroundTreatment) {
    BackgroundTreatment.Inheritance -> WalletTheme.colors.inheritanceSurface
    BackgroundTreatment.Default -> WalletTheme.colors.background
  }
  val headerToMainContentSpacing = when (model.formHeaderModel.sublineModel) {
    null -> 24.dp
    else -> 16.dp
  }

  val scrollState = rememberScrollState()
  val collapseRangePx = with(LocalDensity.current) { TransactionDetailTitleCollapseRange.toPx() }
  val collapseProgress by remember(scrollState, collapseRangePx) {
    derivedStateOf {
      if (collapseRangePx <= 0f) {
        0f
      } else {
        (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
      }
    }
  }

  Column(
    modifier = modifier
      .background(background)
      .imePadding()
      .thenIf(isFullScreen) {
        Modifier.fillMaxSize()
      }
  ) {
    Box(
      modifier = Modifier.thenIf(isFullScreen) {
        Modifier.weight(1f)
      }
    ) {
      val contentShadowHeight = 12.dp
      Column(
        modifier = Modifier
          .thenIf(isFullScreen) { Modifier.matchParentSize() }
          .background(background)
          .verticalScroll(scrollState)
          .padding(bottom = contentShadowHeight)
          .padding(horizontal = TransactionDetailHorizontalPadding)
      ) {
        Spacer(modifier = Modifier.height(TransactionDetailToolbarReservedHeight))
        TransactionDetailHeader(
          headerModel = model.formHeaderModel,
          collapseProgress = collapseProgress
        )
        Column(
          modifier = Modifier.padding(
            top = headerToMainContentSpacing,
            bottom = TransactionDetailBottomContentPadding
          )
        ) {
          FormBodyMainContent(model)
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(contentShadowHeight)
          .align(Alignment.BottomCenter)
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(Color.Transparent, background)
            )
          )
      )

      TransactionDetailCollapsibleToolbar(
        title = title,
        toolbarModel = model.toolbar,
        collapseProgress = collapseProgress
      )
    }

    when {
      model.disableFixedFooter -> Unit
      model.primaryButton != null || model.secondaryButton != null -> {
        Column(
          modifier = Modifier
            .background(background)
            .padding(top = 12.dp, bottom = 28.dp)
            .padding(horizontal = TransactionDetailHorizontalPadding)
        ) {
          FooterContent(model)
        }
      }
    }
  }
}

@Composable
private fun TransactionDetailHeader(
  headerModel: FormHeaderModel,
  collapseProgress: Float,
) {
  val theme = LocalTheme.current
  val horizontalAlignment = when (headerModel.alignment) {
    LEADING -> Alignment.Start
    CENTER -> Alignment.CenterHorizontally
  }
  val textAlignment = when (headerModel.alignment) {
    LEADING -> TextAlign.Start
    CENTER -> TextAlign.Center
  }

  Header(
    horizontalAlignment = horizontalAlignment,
    iconContent = {
      headerModel.iconModel?.let { iconModel ->
        Spacer(modifier = Modifier.height(iconModel.iconTopSpacing?.dp ?: 24.dp))
        IconImage(model = iconModel)
      }
    },
    customContent = {
      headerModel.customContent?.let { customContent ->
        CustomHeaderContent(model = customContent)
      }
    },
    headlineContent = {
      headerModel.headline?.let { headline ->
        Label(
          modifier = Modifier
            .padding(top = 16.dp)
            .alpha(collapseProgress.fadeOut(start = 0.7f, end = 0.86f)),
          text = headline,
          type = headerModel.headlineLabelType,
          treatment = when (theme) {
            Theme.DARK -> Unspecified
            Theme.LIGHT -> Primary
          },
          alignment = textAlignment,
          color = when (theme) {
            Theme.DARK -> Color.White
            Theme.LIGHT -> Color.Unspecified
          }
        )
      }
    },
    sublineContent = {
      headerModel.sublineModel?.buildAnnotatedString()?.let { subline ->
        Label(
          modifier = Modifier.padding(top = 8.dp),
          text = subline,
          type = when (headerModel.sublineTreatment) {
            REGULAR -> LabelType.Body2Regular
            SMALL -> LabelType.Body3Regular
            MONO -> LabelType.Body2Mono
          },
          treatment = when (theme) {
            Theme.DARK -> Unspecified
            Theme.LIGHT -> Secondary
          },
          alignment = textAlignment,
          color = when (theme) {
            Theme.DARK -> Color.White
            Theme.LIGHT -> Color.Unspecified
          },
          onClick = { index ->
            when (val sublineModel = headerModel.sublineModel) {
              is LabelModel.LinkSubstringModel ->
                sublineModel.linkedSubstrings.forEach { link ->
                  if (link.range.contains(index)) {
                    link.onClick()
                  }
                }
              else -> Unit
            }
          }
        )
      }
    }
  )
}

@Composable
private fun BoxScope.TransactionDetailCollapsibleToolbar(
  title: String,
  toolbarModel: ToolbarModel?,
  collapseProgress: Float,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(
        TransactionDetailToolbarTopPadding +
          TransactionDetailToolbarHeight +
          TransactionDetailToolbarBottomPadding +
          TransactionDetailToolbarBottomGradientHeight
      )
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(
          TransactionDetailToolbarTopPadding +
            TransactionDetailToolbarHeight +
            TransactionDetailToolbarBottomPadding
        )
        .background(WalletTheme.colors.background)
    ) {
      Box(
        modifier = Modifier
          .padding(
            top = TransactionDetailToolbarTopPadding,
            start = TransactionDetailHorizontalPadding,
            end = TransactionDetailHorizontalPadding
          )
          .fillMaxWidth()
          .height(TransactionDetailToolbarHeight)
      ) {
        Toolbar(
          model = ToolbarModel(
            leadingAccessory = toolbarModel?.leadingAccessory,
            middleAccessory = null,
            trailingAccessory = toolbarModel?.trailingAccessory
          ),
          showDesignSystemChrome = false
        )

        Label(
          modifier = Modifier
            .fillMaxWidth()
            .padding(
              start = if (toolbarModel?.leadingAccessory != null) TransactionDetailInlineTitleStartPadding else 0.dp,
              end = if (toolbarModel?.trailingAccessory != null) TransactionDetailInlineTitleEndPadding else 0.dp
            )
            .align(Alignment.CenterStart)
            .alpha(collapseProgress.fadeIn(start = 0.62f, end = 0.8f)),
          text = AnnotatedString(title),
          style = WalletTheme.labelStyle(type = LabelType.Title2),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(TransactionDetailToolbarBottomGradientHeight)
        .align(Alignment.BottomCenter)
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(
              WalletTheme.colors.background,
              WalletTheme.colors.background.copy(alpha = 0.65f),
              Color.Transparent
            )
          )
        )
    )
  }
}

private val TransactionDetailHorizontalPadding = 20.dp
private val TransactionDetailToolbarTopPadding = 8.dp
private val TransactionDetailToolbarHeight = 48.dp
private val TransactionDetailToolbarBottomPadding = 8.dp
private val TransactionDetailToolbarBottomGradientHeight = 20.dp
private val TransactionDetailToolbarReservedHeight =
  TransactionDetailToolbarTopPadding +
    TransactionDetailToolbarHeight +
    TransactionDetailToolbarBottomPadding +
    TransactionDetailToolbarBottomGradientHeight
private val TransactionDetailInlineTitleStartPadding = 56.dp
private val TransactionDetailInlineTitleEndPadding = 56.dp
private val TransactionDetailTitleCollapseRange = 120.dp
private val TransactionDetailBottomContentPadding = 24.dp

private fun Float.fadeOut(
  start: Float,
  end: Float,
): Float =
  when {
    this <= start -> 1f
    this >= end -> 0f
    else -> 1f - ((this - start) / (end - start))
  }

private fun Float.fadeIn(
  start: Float,
  end: Float,
): Float =
  when {
    this <= start -> 0f
    this >= end -> 1f
    else -> (this - start) / (end - start)
  }

package build.wallet.ui.app.core.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.form.FormMainContentVerticalAlignment
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.statemachine.core.form.FormScreenTitleModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.core.form.RenderContext.Screen
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.toolbar.EmptyToolbar
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

/**
 * A slot-based screen for rendering form views.
 *
 * https://www.figma.com/file/aaOrQTgHXp2NpOYCBDoe5E/Wallet-System?node-id=3477%3A4033&t=JWWhnI4XJy2RNvVd-1.
 */
@Composable
fun FormScreen(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)?,
  renderContext: RenderContext = Screen,
  horizontalPadding: Int = 20,
  headerToMainContentSpacing: Int = 16,
  background: Color = WalletTheme.colors.background,
  toolbarModel: ToolbarModel? = null,
  screenTitle: FormScreenTitleModel? = null,
  layout: FormScreenLayoutModel = FormScreenLayoutModel.Legacy,
  toolbarContent: @Composable (() -> Unit)? = null,
  headerContent: @Composable (() -> Unit)? = null,
  mainContent: @Composable (ColumnScope.() -> Unit)? = null,
  footerContent: @Composable (ColumnScope.() -> Unit)? = null,
) {
  val isFullScreen = renderContext == Screen
  onBack?.let {
    BackHandler(onBack = it)
  }

  val resolvedLayout = when {
    layout != FormScreenLayoutModel.Legacy -> layout
    screenTitle != null -> FormScreenLayoutModel.LargeTitle()
    else -> FormScreenLayoutModel.Legacy
  }
  val largeTitleLayout = resolvedLayout as? FormScreenLayoutModel.LargeTitle

  if (largeTitleLayout != null) {
    require(screenTitle?.title == null || toolbarModel?.middleAccessory == null) {
      "FormScreen with a large title manages its own centered toolbar title."
    }

    FormScreenLargeTitle(
      modifier = modifier,
      isFullScreen = isFullScreen,
      background = background,
      horizontalPadding = horizontalPadding.dp,
      headerToMainContentSpacing = headerToMainContentSpacing.dp,
      toolbarModel = toolbarModel,
      eyebrow = screenTitle?.eyebrow,
      title = screenTitle?.title,
      contentSpacing = largeTitleLayout.contentSpacing.dp,
      isScrollable = largeTitleLayout.scrollable ||
        largeTitleLayout.mainContentVerticalAlignment == FormMainContentVerticalAlignment.TOP,
      mainContentAlignment = largeTitleLayout.mainContentVerticalAlignment,
      headerContent = headerContent,
      mainContent = mainContent,
      footerContent = footerContent
    )
  } else {
    FormScreenLegacy(
      modifier = modifier,
      isFullScreen = isFullScreen,
      background = background,
      horizontalPadding = horizontalPadding,
      headerToMainContentSpacing = headerToMainContentSpacing,
      toolbarContent = toolbarContent,
      headerContent = headerContent,
      mainContent = mainContent,
      footerContent = footerContent
    )
  }
}

@Composable
private fun FormScreenLegacy(
  modifier: Modifier = Modifier,
  isFullScreen: Boolean,
  background: Color,
  horizontalPadding: Int,
  headerToMainContentSpacing: Int,
  toolbarContent: @Composable (() -> Unit)?,
  headerContent: @Composable (() -> Unit)?,
  mainContent: @Composable (ColumnScope.() -> Unit)?,
  footerContent: @Composable (ColumnScope.() -> Unit)?,
) {
  Column(
    modifier =
      modifier
        .background(background)
        .imePadding()
        .thenIf(isFullScreen) {
          Modifier.fillMaxSize()
        }
  ) {
    val contentShadowHeight = 12.dp
    Box(
      modifier = Modifier
        .thenIf(isFullScreen) { Modifier.weight(1F) }
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .thenIf(isFullScreen) { Modifier.matchParentSize() }
          .background(background)
          .verticalScroll(rememberScrollState())
          .padding(bottom = contentShadowHeight)
          .padding(horizontal = horizontalPadding.dp)
      ) {
        if (toolbarContent != null) {
          toolbarContent()
        } else {
          EmptyToolbar()
        }
        headerContent?.invoke()
        Spacer(Modifier.height(headerToMainContentSpacing.dp))
        mainContent?.invoke(this)
      }
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(contentShadowHeight)
            .align(Alignment.BottomCenter)
            .background(
              brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, background)
              )
            )
      ) {}
    }
    footerContent?.let {
      Column(
        modifier =
          Modifier
            .background(background)
            .padding(top = 12.dp, bottom = 28.dp)
            .padding(horizontal = horizontalPadding.dp)
      ) {
        footerContent()
      }
    }
  }
}

@Composable
private fun FormScreenLargeTitle(
  modifier: Modifier = Modifier,
  isFullScreen: Boolean,
  background: Color,
  horizontalPadding: Dp,
  headerToMainContentSpacing: Dp,
  toolbarModel: ToolbarModel?,
  eyebrow: String?,
  title: String?,
  contentSpacing: Dp,
  isScrollable: Boolean,
  mainContentAlignment: FormMainContentVerticalAlignment,
  headerContent: @Composable (() -> Unit)?,
  mainContent: @Composable (ColumnScope.() -> Unit)?,
  footerContent: @Composable (ColumnScope.() -> Unit)?,
) {
  Column(
    modifier =
      modifier
        .background(background)
        .imePadding()
        .thenIf(isFullScreen) {
          Modifier.fillMaxSize()
        }
  ) {
    Box(
      modifier = Modifier
        .thenIf(isFullScreen) { Modifier.weight(1F) }
    ) {
      if (isScrollable) {
        FormScreenLargeTitleScrollable(
          isFullScreen = isFullScreen,
          background = background,
          horizontalPadding = horizontalPadding,
          headerToMainContentSpacing = headerToMainContentSpacing,
          toolbarModel = toolbarModel,
          eyebrow = eyebrow,
          title = title,
          contentSpacing = contentSpacing,
          headerContent = headerContent,
          mainContent = mainContent
        )
      } else {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding)
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
          ) {
            Spacer(modifier = Modifier.height(FormScreenToolbarReservedHeight))
            FormScreenLargeTitleBlock(
              eyebrow = eyebrow,
              title = title
            )
            headerContent?.let {
              if (eyebrow != null || title != null) {
                Spacer(modifier = Modifier.height(headerToMainContentSpacing))
              }
              it()
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(
                  top = if (headerContent != null && mainContent != null) headerToMainContentSpacing else 0.dp,
                  bottom = when (mainContentAlignment) {
                    FormMainContentVerticalAlignment.TOP,
                    FormMainContentVerticalAlignment.BOTTOM,
                    -> FormScreenBottomContentPadding
                    FormMainContentVerticalAlignment.CENTER -> 0.dp
                  }
                ),
              contentAlignment = when (mainContentAlignment) {
                FormMainContentVerticalAlignment.TOP -> Alignment.TopCenter
                FormMainContentVerticalAlignment.CENTER -> Alignment.Center
                FormMainContentVerticalAlignment.BOTTOM -> Alignment.BottomCenter
              }
            ) {
              mainContent?.let { content ->
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(contentSpacing),
                  content = content
                )
              }
            }
          }
        }

        FormScreenDesignSystemToolbar(
          title = title,
          toolbarModel = toolbarModel,
          collapseProgress = 0f,
          horizontalPadding = horizontalPadding,
          background = background
        )
      }
    }

    footerContent?.let {
      Column(
        modifier =
          Modifier
            .background(background)
            .padding(top = 12.dp, bottom = 28.dp)
            .padding(horizontal = horizontalPadding)
      ) {
        it()
      }
    }
  }
}

@Composable
private fun BoxScope.FormScreenLargeTitleScrollable(
  isFullScreen: Boolean,
  background: Color,
  horizontalPadding: Dp,
  headerToMainContentSpacing: Dp,
  toolbarModel: ToolbarModel?,
  eyebrow: String?,
  title: String?,
  contentSpacing: Dp,
  headerContent: @Composable (() -> Unit)?,
  mainContent: @Composable (ColumnScope.() -> Unit)?,
) {
  val scrollState = rememberScrollState()
  val collapseRangePx = with(LocalDensity.current) { FormScreenTitleCollapseRange.toPx() }
  val collapseProgress by remember(scrollState, collapseRangePx) {
    derivedStateOf {
      if (collapseRangePx <= 0f) {
        0f
      } else {
        (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
      }
    }
  }

  val contentShadowHeight = 12.dp
  Column(
    modifier = Modifier
      .thenIf(isFullScreen) { Modifier.matchParentSize() }
      .background(background)
      .verticalScroll(scrollState)
      .padding(bottom = contentShadowHeight)
      .padding(horizontal = horizontalPadding)
  ) {
    Spacer(modifier = Modifier.height(FormScreenToolbarReservedHeight))
    Column {
      FormScreenLargeTitleBlock(
        eyebrow = eyebrow,
        title = title,
        collapseProgress = collapseProgress
      )
      Column(
        modifier = Modifier.padding(
          top = if (eyebrow != null || title != null) headerToMainContentSpacing else 0.dp,
          bottom = FormScreenBottomContentPadding
        )
      ) {
        headerContent?.invoke()
        if (headerContent != null && mainContent != null) {
          Spacer(modifier = Modifier.height(headerToMainContentSpacing))
        }
        mainContent?.let { content ->
          Column(
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = content
          )
        }
      }
    }
  }

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .height(contentShadowHeight)
        .align(Alignment.BottomCenter)
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, background)
          )
        )
  ) {}

  FormScreenDesignSystemToolbar(
    title = title,
    toolbarModel = toolbarModel,
    collapseProgress = collapseProgress,
    horizontalPadding = horizontalPadding,
    background = background
  )
}

@Composable
private fun FormScreenLargeTitleBlock(
  eyebrow: String?,
  title: String?,
  collapseProgress: Float = 0f,
) {
  eyebrow?.let {
    Label(
      modifier = Modifier
        .padding(top = FormScreenLargeTitleTopSpacing)
        .alpha(formScreenExpandedTitleAlpha(collapseProgress)),
      text = it,
      type = LabelType.Body2Mono
    )
  }
  title?.let {
    Label(
      modifier = Modifier
        .padding(top = if (eyebrow != null) FormScreenEyebrowToTitleSpacing else FormScreenLargeTitleTopSpacing)
        .alpha(formScreenExpandedTitleAlpha(collapseProgress)),
      text = it,
      type = LabelType.Display3
    )
  }
}

@Composable
private fun BoxScope.FormScreenDesignSystemToolbar(
  title: String?,
  toolbarModel: ToolbarModel?,
  collapseProgress: Float,
  horizontalPadding: Dp,
  background: Color = WalletTheme.colors.background,
  backgroundAlpha: Float = 1f,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(
        FormScreenToolbarTopPadding +
          FormScreenToolbarHeight +
          FormScreenToolbarBottomPadding +
          FormScreenToolbarBottomGradientHeight
      )
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(FormScreenToolbarTopPadding + FormScreenToolbarHeight + FormScreenToolbarBottomPadding)
        .thenIf(backgroundAlpha > 0f) {
          Modifier.background(background.copy(alpha = background.alpha * backgroundAlpha))
        }
    ) {
      Box(
        modifier = Modifier
          .padding(
            top = FormScreenToolbarTopPadding,
            start = horizontalPadding,
            end = horizontalPadding
          )
          .fillMaxWidth()
          .height(FormScreenToolbarHeight)
      ) {
        Toolbar(
          model = ToolbarModel(
            leadingAccessory = toolbarModel?.leadingAccessory,
            middleAccessory = null,
            trailingAccessory = toolbarModel?.trailingAccessory
          ),
          showDesignSystemChrome = false
        )

        title?.let {
          Label(
            modifier = Modifier
              .fillMaxWidth()
              .padding(
                start = if (toolbarModel?.leadingAccessory != null) FormScreenInlineTitleStartPadding else 0.dp,
                end = FormScreenInlineTitleEndPadding
              )
              .align(Alignment.CenterStart)
              .alpha(formScreenInlineTitleAlpha(collapseProgress)),
            text = it,
            type = LabelType.Title2
          )
        }
      }
    }

    if (backgroundAlpha > 0f) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(FormScreenToolbarBottomGradientHeight)
          .align(Alignment.BottomCenter)
          .background(
            brush =
              Brush.verticalGradient(
                colors =
                  listOf(
                    background.copy(alpha = background.alpha * backgroundAlpha),
                    background.copy(alpha = background.alpha * backgroundAlpha * 0.65f),
                    Color.Transparent
                  )
              )
          )
      )
    }
  }
}

private fun formScreenExpandedTitleAlpha(collapseProgress: Float): Float =
  (1f - collapseProgress).coerceIn(0f, 1f)

private fun formScreenInlineTitleAlpha(collapseProgress: Float): Float =
  (
    (collapseProgress - FORM_SCREEN_INLINE_TITLE_FADE_START_PROGRESS) /
      (1f - FORM_SCREEN_INLINE_TITLE_FADE_START_PROGRESS)
  ).coerceIn(0f, 1f)

private val FormScreenToolbarTopPadding = 8.dp
private val FormScreenToolbarHeight = 48.dp
private val FormScreenToolbarBottomPadding = 8.dp
private val FormScreenToolbarBottomGradientHeight = 20.dp
private val FormScreenToolbarReservedHeight =
  FormScreenToolbarTopPadding +
    FormScreenToolbarHeight +
    FormScreenToolbarBottomPadding +
    FormScreenToolbarBottomGradientHeight
private val FormScreenLargeTitleTopSpacing = 24.dp
private val FormScreenEyebrowToTitleSpacing = 8.dp
private val FormScreenInlineTitleStartPadding = 56.dp
private val FormScreenInlineTitleEndPadding = 56.dp
private const val FORM_SCREEN_INLINE_TITLE_FADE_START_PROGRESS = 0.95f
private val FormScreenTitleCollapseRange = 120.dp
private val FormScreenBottomContentPadding = 24.dp
